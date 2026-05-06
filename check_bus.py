"""
Bus 712 arrival notifier for Fordyce Avenue.

Required environment variables:
  AT_API_KEY   - Your Auckland Transport API subscription key
  NTFY_TOPIC   - Your private Ntfy topic name
"""

import os
import sys
import requests
from datetime import datetime, timezone, timedelta

# ── Configuration ────────────────────────────────────────────────────────────

AT_API_KEY = os.environ["AT_API_KEY"]
NTFY_TOPIC = os.environ.get("NTFY_TOPIC", "")

ROUTE_SHORT_NAME        = "712"
STOP_CODE               = "6087"
ALERT_THRESHOLD_MINUTES = 6

AT_BASE             = "https://api.at.govt.nz"
AT_TRIP_UPDATES_URL = f"{AT_BASE}/realtime/legacy/tripupdates"
AT_STOP_TIMES_URL   = f"{AT_BASE}/gtfs/v3/stop_times"
AT_STOPS_URL        = f"{AT_BASE}/gtfs/v3/stops"
NTFY_URL            = f"https://ntfy.sh/{NTFY_TOPIC}"

# ── Helpers ──────────────────────────────────────────────────────────────────

def at_headers():
    return {"Ocp-Apim-Subscription-Key": AT_API_KEY}


def resolve_stop_id(stop_code: str) -> str | None:
    """Look up the internal stop_id for a given public stop_code."""
    resp = requests.get(
        AT_STOPS_URL,
        params={"filter[stop_code]": stop_code},
        headers=at_headers(),
        timeout=10,
    )
    if resp.status_code != 200:
        return None
    data = resp.json().get("data", [])
    if not data:
        return None
    return data[0].get("id")


def get_active_712_trips(feed: dict) -> list[dict]:
    """Return list of {trip_id, delay_seconds} for all active route 712 trips."""
    trips = []
    for entity in feed.get("response", {}).get("entity", []):
        tu = entity.get("trip_update", {})
        trip = tu.get("trip", {})
        route_id = trip.get("route_id", "")
        if ROUTE_SHORT_NAME not in route_id:
            continue

        trip_id = trip.get("trip_id", "")
        if not trip_id:
            continue

        delay = 0
        for stu in tu.get("stop_time_update", []):
            if not isinstance(stu, dict):
                continue
            arr = stu.get("arrival") or stu.get("departure")
            if isinstance(arr, dict) and arr.get("delay") is not None:
                delay = arr["delay"]
                break

        trips.append({"trip_id": trip_id, "delay_seconds": delay})

    return trips


def get_trip_stop_times(trip_id: str) -> list[dict]:
    """Get all stop_times for a trip (returns list of attributes dicts)."""
    resp = requests.get(
        AT_STOP_TIMES_URL,
        params={"filter[trip_id]": trip_id},
        headers=at_headers(),
        timeout=10,
    )
    if resp.status_code != 200:
        print(f"DEBUG: stop_times fetch failed: {resp.status_code} {resp.text[:300]}")
        return []
    data = resp.json().get("data", [])
    return data


def find_arrival_at_stop(stop_times: list[dict], stop_id: str, stop_code: str) -> str | None:
    """
    Search through a trip's stop_times to find the one matching our stop.
    Tries matching by stop_id first, then stop_code as fallback.
    """
    for st in stop_times:
        attrs = st.get("attributes", {})
        st_stop_id = attrs.get("stop_id", "")
        # Match either full stop_id or by prefix (just the number part)
        if st_stop_id == stop_id or st_stop_id.startswith(stop_code + "-") or st_stop_id == stop_code:
            return attrs.get("arrival_time") or attrs.get("departure_time")
    return None


def parse_gtfs_time(time_str: str, base_date: datetime) -> datetime:
    """Parse a GTFS time string into a datetime."""
    parts = time_str.split(":")
    hours, minutes, seconds = int(parts[0]), int(parts[1]), int(parts[2])
    return base_date + timedelta(hours=hours, minutes=minutes, seconds=seconds)


def get_minutes_away() -> int | None:
    stop_id = resolve_stop_id(STOP_CODE)
    if not stop_id:
        print(f"ERROR: Could not resolve stop_code {STOP_CODE}")
        return None

    print(f"DEBUG: Using stop_id={stop_id}")

    resp = requests.get(AT_TRIP_UPDATES_URL, headers=at_headers(), timeout=10)
    resp.raise_for_status()
    feed = resp.json()

    now = datetime.now(timezone.utc)
    nzt_offset = timezone(timedelta(hours=12))
    today_nzt = datetime.now(nzt_offset).replace(hour=0, minute=0, second=0, microsecond=0)

    active_trips = get_active_712_trips(feed)
    print(f"DEBUG: Found {len(active_trips)} active 712 trips")

    if not active_trips:
        return None

    soonest = None

    for trip in active_trips:
        trip_id = trip["trip_id"]
        delay   = trip["delay_seconds"]

        stop_times = get_trip_stop_times(trip_id)
        if not stop_times:
            print(f"DEBUG: trip {trip_id} returned no stop_times")
            continue

        # Print first stop_id of the trip so we can see the format
        first = stop_times[0].get("attributes", {})
        print(f"DEBUG: trip {trip_id} has {len(stop_times)} stops, "
              f"e.g. stop_id={first.get('stop_id')}")

        scheduled = find_arrival_at_stop(stop_times, stop_id, STOP_CODE)
        if not scheduled:
            print(f"DEBUG: Stop {STOP_CODE} not on trip {trip_id}")
            continue

        try:
            arr_time = parse_gtfs_time(scheduled, today_nzt)
            arr_time_utc = arr_time.astimezone(timezone.utc)
            predicted = arr_time_utc + timedelta(seconds=delay)
            minutes = (predicted - now).total_seconds() / 60
            print(f"DEBUG: trip={trip_id}, scheduled={scheduled}, "
                  f"delay={delay}s, in {minutes:.1f}min")
            if minutes > 0 and (soonest is None or minutes < soonest):
                soonest = minutes
        except Exception as e:
            print(f"DEBUG: Error parsing time '{scheduled}': {e}")
            continue

    return round(soonest) if soonest is not None else None


def send_notification(minutes: int):
    message = f"Bus 712 is {minutes} minute{'s' if minutes != 1 else ''} away!"
    requests.post(
        NTFY_URL,
        data=message.encode("utf-8"),
        headers={
            "Title": "🚌 Bus Alert – Fordyce Ave",
            "Priority": "high",
            "Tags": "bus,alarm",
        },
        timeout=10,
    ).raise_for_status()
    print(f"Notification sent: {message}")


# ── Main ─────────────────────────────────────────────────────────────────────

def main():
    print(f"Checking bus {ROUTE_SHORT_NAME} at stop code {STOP_CODE}...")
    minutes = get_minutes_away()

    if minutes is None:
        print("No upcoming arrival found.")
        return

    print(f"Bus {ROUTE_SHORT_NAME} is ~{minutes} minute(s) away.")

    if minutes < ALERT_THRESHOLD_MINUTES:
        send_notification(minutes)
    else:
        print("More than 5 minutes away — no notification sent.")


if __name__ == "__main__":
    main()

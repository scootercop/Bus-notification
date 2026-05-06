"""
Bus 712 arrival notifier for Fordyce Avenue.

This run will print all stops on the active 712 trips so we can find
the correct stop_id format for Fordyce Avenue.

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
STOP_CODE               = "6087"   # the public-facing stop number on the sign
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
    """
    Look up the internal stop_id for a given public stop_code.
    e.g. stop_code "6087" might map to stop_id "6087-abc123"
    """
    resp = requests.get(
        AT_STOPS_URL,
        params={"filter[stop_code]": stop_code},
        headers=at_headers(),
        timeout=10,
    )
    if resp.status_code != 200:
        print(f"DEBUG: Stop lookup failed: {resp.status_code} {resp.text[:200]}")
        return None
    data = resp.json().get("data", [])
    if not data:
        print(f"DEBUG: No stop found with stop_code={stop_code}")
        return None
    for s in data:
        attrs = s.get("attributes", {})
        print(f"DEBUG: Found stop_id={s.get('id')} name={attrs.get('stop_name')} code={attrs.get('stop_code')}")
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

    print(f"DEBUG: Found {len(trips)} active 712 trips")
    return trips


def get_scheduled_arrival(trip_id: str, stop_id: str) -> str | None:
    """Look up the scheduled arrival time for a specific trip+stop."""
    resp = requests.get(
        AT_STOP_TIMES_URL,
        params={
            "filter[trip_id]": trip_id,
            "filter[stop_id]": stop_id,
        },
        headers=at_headers(),
        timeout=10,
    )
    if resp.status_code != 200:
        return None
    data = resp.json().get("data", [])
    if not data:
        return None
    attrs = data[0].get("attributes", {})
    return attrs.get("arrival_time") or attrs.get("departure_time")


def parse_gtfs_time(time_str: str, base_date: datetime) -> datetime:
    """Parse a GTFS time string into a datetime."""
    parts = time_str.split(":")
    hours, minutes, seconds = int(parts[0]), int(parts[1]), int(parts[2])
    return base_date + timedelta(hours=hours, minutes=minutes, seconds=seconds)


def get_minutes_away() -> int | None:
    # Step 1: Resolve stop_code to internal stop_id
    stop_id = resolve_stop_id(STOP_CODE)
    if not stop_id:
        print(f"ERROR: Could not resolve stop_code {STOP_CODE} to a stop_id")
        return None

    print(f"DEBUG: Using stop_id={stop_id}")

    # Step 2: Get active 712 trips from realtime feed
    resp = requests.get(AT_TRIP_UPDATES_URL, headers=at_headers(), timeout=10)
    resp.raise_for_status()
    feed = resp.json()

    now = datetime.now(timezone.utc)
    nzt_offset = timezone(timedelta(hours=12))
    today_nzt = datetime.now(nzt_offset).replace(hour=0, minute=0, second=0, microsecond=0)

    active_trips = get_active_712_trips(feed)
    if not active_trips:
        return None

    soonest = None

    for trip in active_trips:
        trip_id = trip["trip_id"]
        delay   = trip["delay_seconds"]

        scheduled = get_scheduled_arrival(trip_id, stop_id)
        if not scheduled:
            print(f"DEBUG: No scheduled time for trip {trip_id} at stop {stop_id}")
            continue

        try:
            arr_time = parse_gtfs_time(scheduled, today_nzt)
            arr_time_utc = arr_time.astimezone(timezone.utc)
            predicted = arr_time_utc + timedelta(seconds=delay)
            minutes = (predicted - now).total_seconds() / 60
            print(f"DEBUG: trip={trip_id}, scheduled={scheduled}, delay={delay}s, in {minutes:.1f}min")
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

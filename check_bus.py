"""
Bus 712 arrival notifier for Fordyce Avenue (stop 6087).

Strategy:
  1. Get all active 712 trip_ids from the realtime feed + their current delay
  2. For each trip, look up the scheduled arrival at stop 6087 from GTFS static
  3. Apply the delay to get the predicted arrival time
  4. Notify if < 6 minutes away

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
STOP_ID                 = "6087"
ALERT_THRESHOLD_MINUTES = 6

AT_BASE             = "https://api.at.govt.nz"
AT_TRIP_UPDATES_URL = f"{AT_BASE}/realtime/legacy/tripupdates"
AT_STOP_TIMES_URL   = f"{AT_BASE}/gtfs/v3/stop_times"
NTFY_URL            = f"https://ntfy.sh/{NTFY_TOPIC}"

# ── Helpers ──────────────────────────────────────────────────────────────────

def at_headers():
    return {"Ocp-Apim-Subscription-Key": AT_API_KEY}


def get_active_712_trips(feed: dict) -> list[dict]:
    """
    Return list of {trip_id, delay_seconds} for all active route 712 trips.
    delay_seconds is taken from the most recent stop_time_update in the trip,
    or from the trip-level delay if stop_time_update is empty.
    """
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

        # Extract delay from stop_time_updates if present
        delay = 0
        updates = tu.get("stop_time_update", [])
        for stu in updates:
            if not isinstance(stu, dict):
                continue
            arr = stu.get("arrival") or stu.get("departure")
            if isinstance(arr, dict) and arr.get("delay") is not None:
                delay = arr["delay"]
                break  # use delay from first valid update

        trips.append({"trip_id": trip_id, "delay_seconds": delay})

    print(f"DEBUG: Found {len(trips)} active 712 trips in realtime feed")
    return trips


def get_scheduled_arrival(trip_id: str, stop_id: str) -> str | None:
    """
    Look up the scheduled arrival time for a specific trip+stop from GTFS static.
    Returns time string like "07:28:00" or None.
    """
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
    """
    Parse a GTFS time string (which can exceed 24:00 for overnight trips)
    into a datetime relative to base_date (today in NZT).
    """
    parts = time_str.split(":")
    hours, minutes, seconds = int(parts[0]), int(parts[1]), int(parts[2])
    return base_date + timedelta(hours=hours, minutes=minutes, seconds=seconds)


def get_minutes_away() -> int | None:
    """
    Main logic: combine realtime delays with scheduled GTFS times
    to predict arrival at stop 6087.
    """
    resp = requests.get(AT_TRIP_UPDATES_URL, headers=at_headers(), timeout=10)
    resp.raise_for_status()
    feed = resp.json()

    now = datetime.now(timezone.utc)

    # Use Auckland time (UTC+12) as the base date for GTFS time parsing
    nzt_offset = timezone(timedelta(hours=12))
    today_nzt = datetime.now(nzt_offset).replace(
        hour=0, minute=0, second=0, microsecond=0
    )

    active_trips = get_active_712_trips(feed)
    if not active_trips:
        return None

    soonest = None

    for trip in active_trips:
        trip_id = trip["trip_id"]
        delay   = trip["delay_seconds"]

        scheduled = get_scheduled_arrival(trip_id, STOP_ID)
        if not scheduled:
            print(f"DEBUG: No scheduled time found for trip {trip_id} at stop {STOP_ID}")
            continue

        print(f"DEBUG: trip={trip_id}, scheduled={scheduled}, delay={delay}s")

        try:
            arr_time = parse_gtfs_time(scheduled, today_nzt)
            arr_time_utc = arr_time.astimezone(timezone.utc)
            predicted = arr_time_utc + timedelta(seconds=delay)
            minutes = (predicted - now).total_seconds() / 60
            print(f"DEBUG: predicted arrival in {minutes:.1f} minutes")
            if minutes > 0 and (soonest is None or minutes < soonest):
                soonest = minutes
        except Exception as e:
            print(f"DEBUG: Error parsing time '{scheduled}': {e}")
            continue

    return round(soonest) if soonest is not None else None


def send_notification(minutes: int):
    """Push a notification via Ntfy."""
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
    print(f"Checking bus {ROUTE_SHORT_NAME} at stop {STOP_ID}...")
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

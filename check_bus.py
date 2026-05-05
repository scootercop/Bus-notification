"""
Bus 712 arrival notifier for Fordyce Avenue stop.

FIRST TIME SETUP:
  Run with --find-stop to discover your stop ID:
    AT_API_KEY=your_key python check_bus.py --find-stop

  Then paste the correct stop ID into STOP_ID below and remove --find-stop.

Required environment variables:
  AT_API_KEY   - Your Auckland Transport API subscription key
  NTFY_TOPIC   - Your private Ntfy topic name (e.g. "jane-bus-alert-x7k2")
"""

import os
import sys
import requests
from datetime import datetime, timezone

# ── Configuration ────────────────────────────────────────────────────────────

AT_API_KEY = os.environ["AT_API_KEY"]
NTFY_TOPIC = os.environ.get("NTFY_TOPIC", "")

ROUTE_SHORT_NAME = "712"
STOP_ID = "6087"

ALERT_THRESHOLD_MINUTES = 6

AT_BASE             = "https://api.at.govt.nz"
AT_TRIP_UPDATES_URL = f"{AT_BASE}/realtime/legacy/tripupdates"
AT_STOPS_URL        = f"{AT_BASE}/gtfs/v3/stops"
NTFY_URL            = f"https://ntfy.sh/{NTFY_TOPIC}"

# ── Helpers ──────────────────────────────────────────────────────────────────

def at_headers():
    return {"Ocp-Apim-Subscription-Key": AT_API_KEY}


def find_stop():
    """
    Scan the live realtime feed for all stops on route 712 and print their IDs.
    Run once: AT_API_KEY=your_key python check_bus.py --find-stop
    """
    print(f"Scanning realtime feed for route {ROUTE_SHORT_NAME} stops...")
    resp = requests.get(AT_TRIP_UPDATES_URL, headers=at_headers(), timeout=10)
    resp.raise_for_status()
    feed = resp.json()

    seen = {}

    for entity in feed.get("response", {}).get("entity", []):
        tu = entity.get("trip_update", {})
        route_id = tu.get("trip", {}).get("route_id", "")
        if ROUTE_SHORT_NAME not in route_id:
            continue
        for stu in tu.get("stop_time_update", []):
            if not isinstance(stu, dict):
                continue
            sid = str(stu.get("stop_id", ""))
            if sid and sid not in seen:
                seen[sid] = True

    if not seen:
        print("No route 712 trips found in the live feed right now.")
        print("Try again when buses are running (e.g. between 7-8am).")
        return

    print(f"\nFound {len(seen)} unique stops on route 712 in the live feed.")
    print("Fetching stop names...\n")

    for sid in sorted(seen):
        try:
            r = requests.get(f"{AT_STOPS_URL}/{sid}", headers=at_headers(), timeout=10)
            if r.status_code == 200:
                name = r.json().get("data", {}).get("attributes", {}).get("stop_name", "?")
            else:
                name = "(name unavailable)"
        except Exception:
            name = "(error)"
        print(f"  {sid:30s}  {name}")

    print("\nFind 'Fordyce' above, copy its Stop ID, and paste into STOP_ID in the script.")


def get_minutes_away(stop_id: str) -> int | None:
    """Return minutes until next bus 712 arrives at stop_id, or None."""
    resp = requests.get(AT_TRIP_UPDATES_URL, headers=at_headers(), timeout=10)
    resp.raise_for_status()
    feed = resp.json()

    now_ts = datetime.now(timezone.utc).timestamp()
    soonest = None

    entities = feed.get("response", {}).get("entity", [])
    print(f"DEBUG: total entities in feed: {len(entities)}")

    # Print all route_ids that contain "712" so we can verify the format
    all_route_ids = set(
        e.get("trip_update", {}).get("trip", {}).get("route_id", "")
        for e in entities
    )
    matching = sorted(r for r in all_route_ids if ROUTE_SHORT_NAME in r)
    print(f"DEBUG: route_ids containing '712': {matching[:10]}")

    for entity in entities:
        tu = entity.get("trip_update", {})
        route_id = tu.get("trip", {}).get("route_id", "")

        # Use 'in' instead of startswith to catch any format e.g. "71201-20240101"
        if ROUTE_SHORT_NAME not in route_id:
            continue

        # Print the stop_ids in this trip so we can verify stop 6087 appears
        stop_ids_in_trip = [
            str(s.get("stop_id", ""))
            for s in tu.get("stop_time_update", [])
            if isinstance(s, dict)
        ]
        print(f"DEBUG: route={route_id}, stops={stop_ids_in_trip[:8]}")

        for stu in tu.get("stop_time_update", []):
            if not isinstance(stu, dict):
                continue
            if str(stu.get("stop_id", "")) != str(stop_id):
                continue
            arrival = stu.get("arrival") or stu.get("departure")
            if not isinstance(arrival, dict):
                continue
            arr_time = arrival.get("time")
            if arr_time and arr_time > now_ts:
                minutes = (arr_time - now_ts) / 60
                if soonest is None or minutes < soonest:
                    soonest = minutes

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
    if "--find-stop" in sys.argv:
        find_stop()
        return

    if not STOP_ID:
        print("ERROR: STOP_ID is not set in the script.")
        print("Run with --find-stop first, find your stop, then paste its ID into STOP_ID.")
        sys.exit(1)

    print(f"Checking bus {ROUTE_SHORT_NAME} at stop {STOP_ID}...")
    minutes = get_minutes_away(STOP_ID)

    if minutes is None:
        print("No upcoming arrival found in realtime feed.")
        return

    print(f"Bus {ROUTE_SHORT_NAME} is ~{minutes} minute(s) away.")

    if minutes < ALERT_THRESHOLD_MINUTES:
        send_notification(minutes)
    else:
        print("More than 5 minutes away — no notification sent.")


if __name__ == "__main__":
    main()

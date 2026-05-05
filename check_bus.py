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

# Paste your stop ID here after running --find-stop (e.g. "1234-20240101")
# Leave empty to require --find-stop mode
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

    seen = {}  # stop_id -> set of stop_ids seen across trips

    for entity in feed.get("response", {}).get("entity", []):
        tu = entity.get("trip_update", {})
        route_id = tu.get("trip", {}).get("route_id", "")
        if not route_id.startswith(ROUTE_SHORT_NAME):
            continue
        for stu in tu.get("stop_time_update", []):
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

    for entity in feed.get("response", {}).get("entity", []):
        tu = entity.get("trip_update", {})
        if not tu.get("trip", {}).get("route_id", "").startswith(ROUTE_SHORT_NAME):
            continue
        for stu in tu.get("stop_time_update", []):
            if str(stu.get("stop_id", "")) != str(stop_id):
                continue
            arrival = stu.get("arrival") or stu.get("departure")
            if not arrival:
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

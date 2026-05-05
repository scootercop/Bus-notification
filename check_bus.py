"""
Bus 712 arrival notifier for Fordyce Avenue (stop 6087).
Uses the AT departures endpoint which powers the AT real-time board.

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

ROUTE_SHORT_NAME        = "712"
STOP_ID                 = "6087"
ALERT_THRESHOLD_MINUTES = 6

# AT departures endpoint — same one used by the AT real-time board
AT_DEPARTURES_URL = f"https://api.at.govt.nz/v2/public-restricted/departures/{STOP_ID}"
NTFY_URL          = f"https://ntfy.sh/{NTFY_TOPIC}"

# ── Helpers ──────────────────────────────────────────────────────────────────

def at_headers():
    return {"Ocp-Apim-Subscription-Key": AT_API_KEY}


def get_minutes_away() -> int | None:
    """
    Query the AT departures endpoint for stop 6087 and return the minutes
    until the next bus 712 departs, or None if not found.
    """
    resp = requests.get(AT_DEPARTURES_URL, headers=at_headers(), timeout=10)
    resp.raise_for_status()
    data = resp.json()

    now = datetime.now(timezone.utc)
    soonest = None

    departures = data.get("data", [])
    print(f"DEBUG: {len(departures)} departures returned for stop {STOP_ID}")

    for dep in departures:
        route = dep.get("route_short_name", "") or dep.get("route_id", "")
        if ROUTE_SHORT_NAME not in str(route):
            continue

        # Try real-time departure first, fall back to scheduled
        time_str = dep.get("departure_time_realtime") or dep.get("departure_time")
        if not time_str:
            continue

        print(f"DEBUG: Found 712 departure at {time_str}")

        try:
            # AT times come back as ISO 8601 strings e.g. "2024-05-01T07:25:00+12:00"
            dep_time = datetime.fromisoformat(time_str)
            if dep_time.tzinfo is None:
                dep_time = dep_time.replace(tzinfo=timezone.utc)
            minutes = (dep_time - now).total_seconds() / 60
            if minutes > 0 and (soonest is None or minutes < soonest):
                soonest = minutes
        except ValueError:
            print(f"DEBUG: Could not parse time '{time_str}'")
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

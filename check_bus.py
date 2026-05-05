"""
Bus 712 arrival notifier for Fordyce Avenue stop.
Queries Auckland Transport Realtime API and sends a push notification
via Ntfy when bus 712 is less than 6 minutes away.

Required environment variables:
  AT_API_KEY   - Your Auckland Transport API subscription key
  NTFY_TOPIC   - Your private Ntfy topic name (e.g. "jane-bus-alert-x7k2")
"""

import os
import sys
import requests
from datetime import datetime, timezone

# ── Configuration ────────────────────────────────────────────────────────────

AT_API_KEY   = os.environ["AT_API_KEY"]
NTFY_TOPIC   = os.environ["NTFY_TOPIC"]

ROUTE_SHORT_NAME = "712"
STOP_NAME_KEYWORD = "Fordyce"   # partial match against stop name from AT API

AT_TRIP_UPDATES_URL = "https://api.at.govt.nz/realtime/legacy/tripupdates"
AT_STOPS_URL        = "https://api.at.govt.nz/gtfs/v3/stops"
NTFY_URL            = f"https://ntfy.sh/{NTFY_TOPIC}"

ALERT_THRESHOLD_MINUTES = 6

# ── Helpers ──────────────────────────────────────────────────────────────────

def at_headers():
    return {"Ocp-Apim-Subscription-Key": AT_API_KEY}


def get_stop_id(keyword: str) -> str | None:
    """Look up the AT stop ID for Fordyce Avenue using the GTFS static API."""
    resp = requests.get(
        AT_STOPS_URL,
        params={"filter[stop_name]": keyword},
        headers=at_headers(),
        timeout=10,
    )
    resp.raise_for_status()
    data = resp.json()
    stops = data.get("data", [])
    if not stops:
        print(f"No stops found matching '{keyword}'")
        return None
    # Pick the first match — print all found so you can verify
    for s in stops:
        attrs = s.get("attributes", {})
        print(f"  Found stop: {attrs.get('stop_name')} (id={s['id']})")
    chosen = stops[0]
    return chosen["id"]


def get_minutes_away(stop_id: str, route_short_name: str) -> int | None:
    """
    Query the AT Realtime trip updates feed and return the minutes until
    the next bus on `route_short_name` arrives at `stop_id`.
    Returns None if no upcoming arrival is found.
    """
    resp = requests.get(
        AT_TRIP_UPDATES_URL,
        headers=at_headers(),
        timeout=10,
    )
    resp.raise_for_status()
    feed = resp.json()

    now_ts = datetime.now(timezone.utc).timestamp()
    soonest = None

    for entity in feed.get("response", {}).get("entity", []):
        trip_update = entity.get("trip_update", {})
        trip        = trip_update.get("trip", {})
        route_id    = trip.get("route_id", "")

        # AT route_ids often look like "71200-20240101" — match on prefix
        if not route_id.startswith(route_short_name):
            continue

        for stu in trip_update.get("stop_time_update", []):
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
    resp = requests.post(
        NTFY_URL,
        data=message.encode("utf-8"),
        headers={
            "Title": "🚌 Bus Alert – Fordyce Ave",
            "Priority": "high",
            "Tags": "bus,alarm",
        },
        timeout=10,
    )
    resp.raise_for_status()
    print(f"Notification sent: {message}")


# ── Main ─────────────────────────────────────────────────────────────────────

def main():
    print(f"Checking bus {ROUTE_SHORT_NAME} at stop matching '{STOP_NAME_KEYWORD}'...")

    stop_id = get_stop_id(STOP_NAME_KEYWORD)
    if not stop_id:
        print("Could not find stop. Check STOP_NAME_KEYWORD.")
        sys.exit(1)

    minutes = get_minutes_away(stop_id, ROUTE_SHORT_NAME)

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

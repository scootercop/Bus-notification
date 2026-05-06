"""
Bus 712 arrival notifier for Fordyce Avenue.

Polls Auckland Transport's GTFS API and sends a push notification via Ntfy
when bus 712 is less than 6 minutes from arriving at Fordyce Avenue (stop 6087).

Required environment variables:
  AT_API_KEY   Auckland Transport API subscription key
  NTFY_TOPIC   Private Ntfy topic name for receiving notifications
"""

import os
import requests
from datetime import datetime, timezone, timedelta

# ── Configuration ────────────────────────────────────────────────────────────

AT_API_KEY = os.environ["AT_API_KEY"]
NTFY_TOPIC = os.environ["NTFY_TOPIC"]

ROUTE_SHORT_NAME = "712"
STOP_CODE        = "6087"

AT_BASE             = "https://api.at.govt.nz"
AT_TRIP_UPDATES_URL = f"{AT_BASE}/realtime/legacy/tripupdates"
AT_TRIPS_URL        = f"{AT_BASE}/gtfs/v3/trips"
AT_STOPS_URL        = f"{AT_BASE}/gtfs/v3/stops"
NTFY_URL            = f"https://ntfy.sh/{NTFY_TOPIC}"

NZT = timezone(timedelta(hours=12))

# ── API helpers ──────────────────────────────────────────────────────────────

def at_get(url: str, params: dict | None = None) -> dict | None:
    """GET an AT API endpoint and return parsed JSON, or None on failure."""
    resp = requests.get(
        url,
        params=params,
        headers={"Ocp-Apim-Subscription-Key": AT_API_KEY},
        timeout=10,
    )
    if resp.status_code != 200:
        return None
    return resp.json()


def resolve_stop_id(stop_code: str) -> str | None:
    """Map a public stop_code (the number on the bus stop sign) to internal stop_id."""
    data = at_get(AT_STOPS_URL, {"filter[stop_code]": stop_code})
    items = (data or {}).get("data", [])
    return items[0]["id"] if items else None


def get_active_trips() -> list[dict]:
    """Return [{trip_id, delay_seconds}, ...] for all active route 712 trips."""
    feed = at_get(AT_TRIP_UPDATES_URL) or {}
    trips = []
    for entity in feed.get("response", {}).get("entity", []):
        tu = entity.get("trip_update", {})
        trip = tu.get("trip", {})

        if ROUTE_SHORT_NAME not in trip.get("route_id", ""):
            continue
        trip_id = trip.get("trip_id")
        if not trip_id:
            continue

        # Pull the realtime delay from the first valid stop_time_update if present
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


def get_scheduled_arrival(trip_id: str, stop_id: str) -> str | None:
    """Return scheduled arrival time string (e.g. '07:28:00') for stop_id on trip_id."""
    data = at_get(f"{AT_TRIPS_URL}/{trip_id}/stoptimes")
    for st in (data or {}).get("data", []):
        attrs = st.get("attributes", {})
        if str(attrs.get("stop_id")) == stop_id:
            return attrs.get("arrival_time") or attrs.get("departure_time")
    return None


# ── Time math ────────────────────────────────────────────────────────────────

def parse_gtfs_time(time_str: str) -> datetime:
    """Parse a GTFS time string (HH:MM:SS, may exceed 24h) as today's NZT datetime."""
    h, m, s = (int(p) for p in time_str.split(":"))
    midnight_nzt = datetime.now(NZT).replace(hour=0, minute=0, second=0, microsecond=0)
    return midnight_nzt + timedelta(hours=h, minutes=m, seconds=s)


def minutes_until_next_arrival(stop_id: str) -> int | None:
    """Return minutes until the next bus 712 arrives at stop_id, or None if none upcoming."""
    now = datetime.now(timezone.utc)
    soonest = None

    for trip in get_active_trips():
        scheduled = get_scheduled_arrival(trip["trip_id"], stop_id)
        if not scheduled:
            continue

        predicted = parse_gtfs_time(scheduled) + timedelta(seconds=trip["delay_seconds"])
        minutes = (predicted - now).total_seconds() / 60

        if minutes > 0 and (soonest is None or minutes < soonest):
            soonest = minutes

    return round(soonest) if soonest is not None else None


# ── Notification ─────────────────────────────────────────────────────────────

def send_notification(minutes: int) -> None:
    """Push an Ntfy notification."""
    plural = "" if minutes == 1 else "s"
    requests.post(
        NTFY_URL,
        data=f"Bus 712 is {minutes} minute{plural} away!".encode("utf-8"),
        headers={
            "Title": "Bus Alert - Fordyce Ave",
            "Priority": "high",
            "Tags": "bus",
        },
        timeout=10,
    ).raise_for_status()


# ── Main ─────────────────────────────────────────────────────────────────────

def main() -> None:
    stop_id = resolve_stop_id(STOP_CODE)
    if not stop_id:
        print(f"ERROR: Could not resolve stop_code {STOP_CODE}")
        return

    minutes = minutes_until_next_arrival(stop_id)
    if minutes is None:
        print("No upcoming arrival.")
        return

    print(f"Bus {ROUTE_SHORT_NAME} is ~{minutes} minute(s) away.")
    send_notification(minutes)
    print("Notification sent.")


if __name__ == "__main__":
    main()

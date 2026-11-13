"""
Biometric attendance demo data: a registered gateway, Hikvision terminals, staff linked to device user numbers, and a
day of scans sent exactly as the attendance gateway would send them (with its key), so every case shows up:
recorded days, a double scan, a scan with no direction (review), an unknown person, a refused second clock-in, and a
device reported offline with a fast clock.

Called from seed_local.py after HR. The gateway key is written to backend/target/dev-gateway.env so the real gateway
(attendance-gateway/) can be started against this backend with its simulator.
"""
from __future__ import annotations

import os
from datetime import datetime, timedelta, timezone

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def _ok(res) -> bool:
    return not (isinstance(res, dict) and "_error" in res)


def iso(dt: datetime) -> str:
    return dt.astimezone(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def seed_devices(api, A: str, branches: dict, step=print) -> dict:
    step("Attendance devices: gateway, terminals, linked staff")
    osu, elg = branches["OSU"]["id"], branches["ELG"]["id"]
    created = api.call("POST", "/hr/attendance-devices/gateways",
                       {"name": "Osu back-office PC", "gatewayIdentifier": "GATEWAY-001", "branchId": osu}, A, ok=False)
    if not _ok(created):
        return {"devices": "skipped (gateway exists)"}
    key, gateway_id = created["key"], created["gateway"]["id"]
    os.makedirs(os.path.join(ROOT, "backend", "target"), exist_ok=True)
    with open(os.path.join(ROOT, "backend", "target", "dev-gateway.env"), "w", encoding="utf-8") as fh:
        fh.write(f"GATEWAY_BACKEND_URL={api.base}\nGATEWAY_ID=GATEWAY-001\nGATEWAY_KEY={key}\nGATEWAY_SIMULATOR=true\n")

    def device(name, serial, branch, direction, ip, model="DS-K1T341AMF"):
        return api.call("POST", "/hr/attendance-devices/devices", {
            "name": name, "serialNumber": serial, "vendor": "HIKVISION", "model": model, "ipAddress": ip, "port": 80,
            "gatewayId": gateway_id, "branchId": branch, "directionMode": direction, "active": True}, A)

    entrance = device("Osu staff entrance", "DS-K1T341AMF-OSU01", osu, "DEVICE_REPORTED", "192.168.10.60")
    device("Osu back door (exit only)", "DS-K1T320MFWX-OSU02", osu, "CLOCK_OUT", "192.168.10.61", "DS-K1T320MFWX")
    device("East Legon staff room", "DS-K1T341AMF-ELG01", elg, "DEVICE_REPORTED", "192.168.20.60")

    employees = {e["fullName"]: e for e in api.call("GET", "/hr/employees?size=200", token=A)["items"]}
    # Device user numbers as enrolled on the terminals (fictional)
    numbers = {"Selorm Agbeko": "1005", "Kobby Ansah": "1007", "Afia Boakye": "1006", "Kwesi Arthur": "1001",
               "Fiifi Mensah": "1003", "Dzifa Tetteh": "1012", "Adjoa Badu": "2010", "Kojo Asamoah": "2011"}
    linked = 0
    for name, number in numbers.items():
        if name in employees:
            linked += _ok(api.call("POST", "/hr/attendance-devices/device-ids",
                                   {"employeeId": employees[name]["id"], "deviceUserId": number}, A, ok=False))

    step("Attendance devices: scans from the gateway")
    serial = entrance["serialNumber"]
    now = datetime.now(timezone.utc).replace(second=0, microsecond=0)
    yesterday = (now - timedelta(days=1)).replace(hour=7, minute=55)
    seq = iter(range(5001, 6000))

    def scan(user, status, at, auth="FACE", dev=serial):
        return {"deviceSerialNumber": dev, "deviceEventId": str(next(seq)), "employeeDeviceId": user, "eventType": status,
                "authenticationMethod": auth, "deviceTimestamp": iso(at), "gatewayReceivedAt": iso(at + timedelta(seconds=2)),
                "rawEvent": f'{{"major":5,"minor":{75 if auth == "FACE" else 38},"employeeNoString":"{user}","attendanceStatus":"{status or ""}"}}'}

    events = [
        # Selorm: a full day yesterday, scanned twice on arrival
        scan("1005", "checkIn", yesterday), scan("1005", "checkIn", yesterday + timedelta(minutes=1)),
        scan("1005", "breakOut", yesterday + timedelta(hours=4, minutes=5)),
        scan("1005", "breakIn", yesterday + timedelta(hours=5, minutes=2)),
        scan("1005", "checkOut", yesterday + timedelta(hours=9, minutes=15)),
        # Kobby: yesterday, fingerprint, left by the back door (exit-only reader: no status needed)
        scan("1007", "checkIn", yesterday + timedelta(hours=6, minutes=3), "FINGERPRINT"),
        scan("1007", None, yesterday + timedelta(hours=14, minutes=10), "FINGERPRINT", "DS-K1T320MFWX-OSU02"),
        # Afia: in this morning, on a break now
        scan("1006", "checkIn", now - timedelta(hours=3, minutes=20)),
        scan("1006", "breakOut", now - timedelta(minutes=25)),
        # Afia scanned without choosing a status on the terminal: waits for a decision
        scan("1006", None, now - timedelta(hours=5)),
        # Someone enrolled on the terminal but not linked to an employee
        scan("1099", "checkIn", now - timedelta(hours=2, minutes=40)),
        # Kwesi was already clocked in at the POS this morning: a second clock-in is refused, never turned into a clock-out
        scan("1001", "checkIn", now - timedelta(minutes=50)),
    ]
    result = api.call("POST", "/attendance/gateway/events", {"gatewayId": "GATEWAY-001", "events": events},
                      headers={"X-Gateway-Key": key})
    statuses: dict[str, int] = {}
    for r in result["results"]:
        statuses[r["status"]] = statuses.get(r["status"], 0) + 1

    api.call("POST", "/attendance/gateway/heartbeat", {
        "gatewayId": "GATEWAY-001", "softwareVersion": "1.0.0", "pendingEvents": 0, "failedSyncAttempts": 0, "lastSyncAt": iso(now),
        "devices": [
            {"serialNumber": "DS-K1T341AMF-OSU01", "status": "ONLINE", "clockOffsetSeconds": 4},
            {"serialNumber": "DS-K1T320MFWX-OSU02", "status": "ONLINE", "clockOffsetSeconds": 410},
            {"serialNumber": "DS-K1T341AMF-ELG01", "status": "OFFLINE", "message": "No route to host 192.168.20.60"},
        ]}, headers={"X-Gateway-Key": key})
    return {"devices": 3, "linked": linked, "scans": statuses}


def print_devices_summary(summary: dict):
    print("  Attendance devices (HR > Attendance Devices):")
    if isinstance(summary.get("devices"), str):
        print(f"    {summary['devices']}")
        return
    print(f"    Gateway GATEWAY-001, 3 Hikvision terminals, {summary.get('linked', 0)} staff linked to device user numbers")
    print(f"    Scans: {', '.join(f'{v} {k.lower()}' for k, v in sorted(summary.get('scans', {}).items()))}")
    print("    Review queue: a scan with no direction (Afia), an unknown person (1099), a refused second clock-in (Kwesi)")
    print("    East Legon terminal reported offline; back-door reader clock ~7 min fast")
    print("    Gateway key saved to backend/target/dev-gateway.env (run the real gateway with its simulator, see attendance-gateway/README.md)")


def main():
    """Adds only the device demo to an already-seeded backend: python scripts/dev/seed_devices.py [--base URL]"""
    import argparse
    import sys
    import time
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from seed_local import PASSWORD, STAFF, Api

    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base", default="http://localhost:8080")
    args = parser.parse_args()
    api = Api(args.base, "unused")
    token = api.call("POST", "/auth/login", {"phone": STAFF[0][0], "password": PASSWORD})["accessToken"]
    time.sleep(1.1)
    branches = {b["code"]: b for b in api.call("GET", "/branches", token=token)}
    print_devices_summary(seed_devices(api, token, branches, step=lambda label: print(f"- {label}", flush=True)))


if __name__ == "__main__":
    main()

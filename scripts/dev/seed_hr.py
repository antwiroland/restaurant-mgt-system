"""
HR test data for a LOCAL backend, created through the real HR API.

Called from seed_local.py after accounts and branches exist, or on its own against an already-seeded backend:
  python scripts/dev/seed_hr.py                      # http://localhost:8080
  python scripts/dev/seed_hr.py --base http://localhost:8082

All people below are fictional test data for local development only.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import date, timedelta

# Departments and positions (same defaults as migration V27). Created only when missing, so this also
# works on PostgreSQL where Flyway already seeded them.
ORG = [
    ("MGMT", "Management", "MANAGEMENT", [("GM", "General Manager"), ("RM", "Restaurant Manager"), ("AM", "Assistant Manager"), ("SHIFT-SUP", "Shift Supervisor")]),
    ("FOH", "Front of House", "FRONT_OF_HOUSE", [("WAITER", "Waiter"), ("HOST", "Host"), ("CASHIER", "Cashier"), ("FLOOR-SUP", "Floor Supervisor")]),
    ("KITCHEN", "Kitchen", "KITCHEN", [("HEAD-CHEF", "Head Chef"), ("SOUS-CHEF", "Sous Chef"), ("LINE-COOK", "Line Cook"), ("PREP-COOK", "Prep Cook"), ("KITCHEN-ASST", "Kitchen Assistant")]),
    ("BAR", "Bar", "BAR", [("BARTENDER", "Bartender"), ("BARISTA", "Barista"), ("BAR-SUP", "Bar Supervisor")]),
    ("OPS", "Operations", "OPERATIONS", [("OPS-OFFICER", "Operations Officer"), ("PROCUREMENT", "Procurement Officer"), ("INVENTORY", "Inventory Officer")]),
    ("SUPPORT", "Support", "SUPPORT", [("CLEANER", "Cleaner"), ("STEWARD", "Steward"), ("SECURITY", "Security Officer"), ("RIDER", "Delivery Rider")]),
    ("ADMIN", "Administration", "ADMINISTRATION", [("HR-OFFICER", "HR Officer"), ("ACCOUNTANT", "Accountant"), ("ADMIN-OFFICER", "Administrative Officer")]),
]

# Staff without a login: first, last, gender, position code, branch code, type, hire date, phone
PEOPLE = [
    ("Kwesi", "Arthur", "MALE", "HEAD-CHEF", "OSU", "FULL_TIME", "2022-06-01", "+233501000001"),
    ("Akua", "Nyarko", "FEMALE", "SOUS-CHEF", "OSU", "FULL_TIME", "2023-02-13", "+233501000002"),
    ("Fiifi", "Mensah", "MALE", "LINE-COOK", "OSU", "FULL_TIME", "2024-01-08", "+233501000003"),
    ("Maame", "Ofori", "FEMALE", "PREP-COOK", "OSU", "PART_TIME", "2025-04-22", "+233501000004"),
    ("Selorm", "Agbeko", "MALE", "WAITER", "OSU", "FULL_TIME", "2023-09-04", "+233501000005"),
    ("Afia", "Boakye", "FEMALE", "WAITER", "OSU", "CASUAL", "2025-08-01", "+233501000006"),
    ("Kobby", "Ansah", "MALE", "BARTENDER", "OSU", "FULL_TIME", "2024-05-15", "+233501000007"),
    ("Gifty", "Amoah", "FEMALE", "CLEANER", "OSU", "PART_TIME", "2023-11-20", "+233501000008"),
    ("Edem", "Kpodo", "MALE", "SECURITY", "OSU", "CONTRACT", "2025-01-06", "+233501000009"),
    ("Adjoa", "Badu", "FEMALE", "HEAD-CHEF", "ELG", "FULL_TIME", "2023-03-01", "+233501000010"),
    ("Kojo", "Asamoah", "MALE", "LINE-COOK", "ELG", "FULL_TIME", "2024-07-29", "+233501000011"),
    ("Dzifa", "Tetteh", "FEMALE", "WAITER", "ELG", "FULL_TIME", "2024-02-05", "+233501000012"),
    ("Nii", "Lartey", "MALE", "WAITER", "ELG", "SEASONAL", "2025-10-01", "+233501000013"),
    ("Ewurabena", "Quansah", "FEMALE", "BARISTA", "ELG", "PART_TIME", "2025-03-10", "+233501000014"),
    ("Yaw", "Frimpong", "MALE", "STEWARD", "ELG", "CASUAL", "2025-06-16", "+233501000015"),
    ("Akosua", "Danquah", "FEMALE", "HR-OFFICER", None, "FULL_TIME", "2023-01-09", "+233501000016"),
]

# Imported staff accounts (from seed_local.STAFF): phone -> position code
LOGIN_POSITIONS = {
    "+233200000001": "GM",
    "+233200000002": "RM",
    "+233200000003": "CASHIER",
    "+233200000004": "CASHIER",
    "+233200000005": "RIDER",
    "+233200000006": "RIDER",
}


def minimal_pdf(title: str) -> bytes:
    """A tiny valid one-page PDF containing the title, enough to upload and open."""
    text = title.replace("(", "").replace(")", "")
    stream = f"BT /F1 18 Tf 72 720 Td ({text}) Tj ET".encode()
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
        b"<< /Length " + str(len(stream)).encode() + b" >>\nstream\n" + stream + b"\nendstream",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    ]
    out = b"%PDF-1.4\n"
    offsets = []
    for i, body in enumerate(objects, start=1):
        offsets.append(len(out))
        out += f"{i} 0 obj\n".encode() + body + b"\nendobj\n"
    xref = len(out)
    out += f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode()
    out += b"".join(f"{o:010d} 00000 n \n".encode() for o in offsets)
    out += f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    return out


def upload(base: str, token: str, employee_id: str, title: str, doc_type: str, expires: str | None = None) -> dict:
    boundary = "----seed" + uuid.uuid4().hex
    content = minimal_pdf(title)
    filename = title.lower().replace(" ", "-") + ".pdf"
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{filename}\"\r\n"
            f"Content-Type: application/pdf\r\n\r\n").encode() + content + f"\r\n--{boundary}--\r\n".encode()
    query = f"title={urllib.parse.quote(title)}&documentType={doc_type}" + (f"&expiresOn={expires}" if expires else "")
    req = urllib.request.Request(f"{base}/hr/employees/{employee_id}/documents?{query}", data=body, method="POST")
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return json.loads(response.read().decode())
    except urllib.error.HTTPError as err:
        raise SystemExit(f"\nupload {title} -> {err.code}: {err.read().decode(errors='replace')[:300]}")


def employee_body(detail: dict, **changes) -> dict:
    """Builds a full update request from an employee detail (the API replaces the record)."""
    s = detail["summary"]
    body = {
        "firstName": detail["firstName"], "middleName": detail.get("middleName"), "lastName": detail.get("lastName"),
        "gender": detail.get("gender"), "dateOfBirth": detail.get("dateOfBirth"), "email": s.get("email"), "phone": s.get("phone"),
        "address": detail.get("address"), "departmentId": s.get("departmentId"), "positionId": s.get("positionId"),
        "employmentType": s["employmentType"], "hireDate": s["hireDate"], "reportingManagerId": detail.get("reportingManagerId"),
        "primaryBranchId": s.get("primaryBranchId"), "qualifications": detail.get("qualifications"), "experience": detail.get("experience"),
        "emergencyContacts": [{k: c.get(k) for k in ("name", "relationship", "phone", "email")} for c in detail.get("emergencyContacts", [])],
        "version": detail["version"],
    }
    body.update(changes)
    return body


def seed_hr(api, admin_token: str, branches: dict, users_by_phone: dict, step=print) -> dict:
    """Seeds HR data. `branches` maps branch code -> branch record. Returns a summary for printing."""
    A = admin_token
    today = date.today()

    step("HR: departments & positions")
    departments = {d["code"]: d for d in api.call("GET", "/hr/departments?includeArchived=true", token=A)}
    positions = {p["code"]: p for p in api.call("GET", "/hr/positions?includeArchived=true", token=A)}
    for code, name, category, roles in ORG:
        if code not in departments:
            departments[code] = api.call("POST", "/hr/departments", {"code": code, "name": name, "category": category}, A)
        for pcode, title in roles:
            if pcode not in positions:
                positions[pcode] = api.call("POST", "/hr/positions", {"code": pcode, "title": title, "departmentId": departments[code]["id"]}, A)
    pos = {code: p["id"] for code, p in positions.items()}

    step("HR: import staff logins as employees")
    result = api.call("POST", "/hr/employees/import-users", {"employmentType": "FULL_TIME", "hireDate": "2024-03-01"}, A)
    linked = {}
    for employee_id in result["employeeIds"]:
        detail = api.call("GET", f"/hr/employees/{employee_id}", token=A)
        phone = detail["summary"].get("phone")
        if phone in LOGIN_POSITIONS:
            linked[phone] = api.call("PUT", f"/hr/employees/{employee_id}",
                                     employee_body(detail, positionId=pos[LOGIN_POSITIONS[phone]], departmentId=None), A)
    manager = linked.get("+233200000002")

    step("HR: employees without logins")
    people = {}
    for first, last, gender, pcode, branch, etype, hired, phone in PEOPLE:
        body = {
            "firstName": first, "lastName": last, "gender": gender, "positionId": pos[pcode], "employmentType": etype,
            "hireDate": hired, "phone": phone, "primaryBranchId": branches[branch]["id"] if branch else None,
            "dateOfBirth": f"{1985 + (len(first) * 3) % 15}-0{1 + len(last) % 9}-1{len(first) % 9}",
            "address": f"{len(last) * 7} Ring Road, Accra",
            "reportingManagerId": manager["summary"]["id"] if manager and branch == "OSU" else None,
            "emergencyContacts": [{"name": f"{'Kofi' if gender == 'FEMALE' else 'Ama'} {last}", "relationship": "Spouse" if len(first) % 2 else "Sibling",
                                   "phone": "+2332" + phone[-8:]}],
        }
        people[f"{first} {last}"] = api.call("POST", "/hr/employees", body, A)

    def eid(name):
        return people[name]["summary"]["id"]

    step("HR: contracts")
    salaries = {
        "Kwesi Arthur": ("PERMANENT", "MONTHLY", "6500.00", None), "Akua Nyarko": ("PERMANENT", "MONTHLY", "4800.00", None),
        "Fiifi Mensah": ("FIXED_TERM", "MONTHLY", "3200.00", (today + timedelta(days=20)).isoformat()),
        "Selorm Agbeko": ("FIXED_TERM", "MONTHLY", "2600.00", (today + timedelta(days=45)).isoformat()),
        "Afia Boakye": ("CASUAL", "DAILY", "120.00", (today + timedelta(days=90)).isoformat()),
        "Kobby Ansah": ("PERMANENT", "MONTHLY", "3000.00", None), "Gifty Amoah": ("PART_TIME", "HOURLY", "18.50", None),
        "Edem Kpodo": ("FIXED_TERM", "MONTHLY", "2400.00", (today + timedelta(days=10)).isoformat()),
        "Adjoa Badu": ("PERMANENT", "MONTHLY", "6200.00", None), "Dzifa Tetteh": ("PERMANENT", "MONTHLY", "2700.00", None),
        "Nii Lartey": ("SEASONAL", "DAILY", "110.00", (today + timedelta(days=60)).isoformat()),
        "Ewurabena Quansah": ("PART_TIME", "HOURLY", "20.00", None), "Akosua Danquah": ("PERMANENT", "MONTHLY", "5500.00", None),
    }
    for name, (ctype, stype, amount, end) in salaries.items():
        hired = people[name]["summary"]["hireDate"]
        body = {"contractType": ctype, "startDate": hired, "endDate": end, "salaryType": stype, "salaryAmount": amount,
                "weeklyHours": 20 if ctype == "PART_TIME" else 45, "terms": "Standard terms of employment. Test data."}
        contract = api.call("POST", f"/hr/employees/{eid(name)}/contracts", body, A)
        api.call("POST", f"/hr/contracts/{contract['id']}/activate", {}, A)
    # probation ending soon, and a draft renewal waiting for approval
    probation = api.call("POST", f"/hr/employees/{eid('Kojo Asamoah')}/contracts",
                         {"contractType": "PROBATION", "startDate": "2024-07-29", "probationEndDate": (today + timedelta(days=14)).isoformat(),
                          "salaryType": "MONTHLY", "salaryAmount": "2900.00", "weeklyHours": 45}, A)
    api.call("POST", f"/hr/contracts/{probation['id']}/activate", {}, A)
    api.call("POST", f"/hr/employees/{eid('Maame Ofori')}/contracts",
             {"contractType": "PART_TIME", "startDate": "2025-04-22", "salaryType": "HOURLY", "salaryAmount": "17.00", "weeklyHours": 24}, A)

    step("HR: documents")
    upload(api.base, A, eid("Kwesi Arthur"), "Signed employment contract", "EMPLOYMENT_CONTRACT")
    upload(api.base, A, eid("Kwesi Arthur"), "Food safety certificate", "FOOD_SAFETY_CERTIFICATE", (today + timedelta(days=25)).isoformat())
    upload(api.base, A, eid("Akua Nyarko"), "Food hygiene certificate", "FOOD_SAFETY_CERTIFICATE", (today - timedelta(days=5)).isoformat())
    upload(api.base, A, eid("Selorm Agbeko"), "CV", "CV")
    upload(api.base, A, eid("Adjoa Badu"), "Ghana Card", "IDENTIFICATION", (today + timedelta(days=700)).isoformat())

    step("HR: branch moves, cover and statuses")
    # Dzifa moved from East Legon to Osu five weeks ago (shows assignment history)
    api.call("POST", f"/hr/employees/{eid('Dzifa Tetteh')}/transfer",
             {"toBranchId": branches["OSU"]["id"], "effectiveDate": (today - timedelta(days=35)).isoformat(), "reason": "Osu needed a senior waiter"}, A)
    # Nii (East Legon) also works weekends at Osu: Yaw (Osu manager) sees him read-only
    api.call("POST", f"/hr/employees/{eid('Nii Lartey')}/assignments",
             {"branchId": branches["OSU"]["id"], "assignmentType": "SECONDARY", "startDate": (today - timedelta(days=7)).isoformat(),
              "reason": "Weekend cover"}, A)
    # Kobby covers the East Legon bar for two weeks
    api.call("POST", f"/hr/employees/{eid('Kobby Ansah')}/assignments",
             {"branchId": branches["ELG"]["id"], "assignmentType": "TEMPORARY", "startDate": (today - timedelta(days=3)).isoformat(),
              "endDate": (today + timedelta(days=11)).isoformat(), "reason": "Bar supervisor on leave"}, A)
    api.call("POST", f"/hr/employees/{eid('Akua Nyarko')}/status", {"status": "ON_LEAVE", "reason": "Annual leave"}, A)
    api.call("POST", f"/hr/employees/{eid('Edem Kpodo')}/status", {"status": "SUSPENDED", "reason": "Pending investigation (test data)"}, A)
    api.call("POST", f"/hr/employees/{eid('Yaw Frimpong')}/status",
             {"status": "RESIGNED", "reason": "Relocated to Kumasi", "effectiveDate": (today - timedelta(days=12)).isoformat()}, A)

    step("HR: permission grant")
    # Abena (cashier) helps with HR filing: may view employees and documents in her branch, nothing else.
    abena = users_by_phone.get("+233200000004")
    if abena:
        api.call("PUT", f"/hr/access/grants/{abena['id']}", {"permissions": ["hr.employee.view", "hr.document.view"]}, A)

    schedule_summary = seed_scheduling(api, A, branches, people, step)
    return {"imported": result["created"], "created": len(people), "skipped": result["skipped"], **schedule_summary}


def seed_scheduling(api, A: str, branches: dict, people: dict, step=print) -> dict:
    """Templates, a published current week, next week in draft, availability and a pending swap."""
    today = date.today()
    monday = today - timedelta(days=today.weekday())
    next_monday = monday + timedelta(days=7)
    osu, elg = branches["OSU"]["id"], branches["ELG"]["id"]

    def eid(name):
        return people[name]["summary"]["id"]

    step("HR: shift templates")
    every_day = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"]
    breakfast = api.call("POST", "/hr/shift-templates", {"name": "Breakfast", "shiftType": "MORNING", "branchId": osu, "startTime": "07:00",
                                                          "endTime": "15:00", "breakMinutes": 30, "days": every_day, "minStaff": 2, "maxStaff": 4}, A)
    evening = api.call("POST", "/hr/shift-templates", {"name": "Evening service", "shiftType": "EVENING", "branchId": osu, "startTime": "15:00",
                                                        "endTime": "23:00", "breakMinutes": 30, "days": every_day, "minStaff": 3, "maxStaff": 5}, A)
    late_bar = api.call("POST", "/hr/shift-templates", {"name": "Late bar", "shiftType": "NIGHT", "branchId": osu, "startTime": "20:00",
                                                         "endTime": "02:00", "breakMinutes": 15, "days": ["FRI", "SAT"], "minStaff": 1, "maxStaff": 2}, A)
    lunch = api.call("POST", "/hr/shift-templates", {"name": "Lunch", "shiftType": "AFTERNOON", "branchId": elg, "startTime": "10:00",
                                                      "endTime": "18:00", "breakMinutes": 45, "days": every_day, "minStaff": 2}, A)

    # name -> (template, weekday indexes 0=Mon); deliberately leaves some slots short so coverage warnings show up
    pattern = {
        "Kwesi Arthur": (breakfast, [0, 1, 2, 3, 4]),
        "Maame Ofori": (breakfast, [5, 6]),
        "Gifty Amoah": (breakfast, [1, 3, 5]),
        "Fiifi Mensah": (evening, [0, 1, 2, 3, 4]),
        "Dzifa Tetteh": (evening, [2, 3, 4, 5, 6]),
        "Afia Boakye": (evening, [4, 5, 6]),
        "Kobby Ansah": (late_bar, [4, 5]),
        "Adjoa Badu": (lunch, [0, 1, 2, 3, 4]),
        "Kojo Asamoah": (lunch, [2, 3, 4, 5, 6]),
        "Ewurabena Quansah": (lunch, [5, 6]),
    }

    def place(week_start):
        created = 0
        for name, (template, days) in pattern.items():
            for d in days:
                body = {"employeeId": eid(name), "branchId": template["branchId"], "templateId": template["id"],
                        "date": (week_start + timedelta(days=d)).isoformat(), "acknowledgeWarnings": True}
                res = api.call("POST", "/hr/schedules", body, A, ok=False)
                if isinstance(res, dict) and "_error" not in res:
                    created += 1
        return created

    step("HR: this week's schedule (published)")
    this_week = place(monday)
    for branch in (osu, elg):
        api.call("POST", "/hr/schedules/publish", {"branchId": branch, "from": monday.isoformat(), "to": (monday + timedelta(days=6)).isoformat()}, A)

    step("HR: next week's schedule (draft)")
    next_week = place(next_monday)
    # two open evening shifts nobody has picked up yet
    for d in (4, 5):
        api.call("POST", "/hr/schedules", {"branchId": osu, "templateId": evening["id"], "date": (next_monday + timedelta(days=d)).isoformat()}, A)

    step("HR: availability")
    api.call("POST", f"/hr/employees/{eid('Gifty Amoah')}/availability",
             {"kind": "UNAVAILABLE", "dayOfWeek": "SAT", "note": "Weekend classes"}, A)
    api.call("POST", f"/hr/employees/{eid('Afia Boakye')}/availability",
             {"kind": "PREFERRED", "dayOfWeek": "FRI", "startTime": "15:00", "endTime": "23:00", "note": "Prefers evenings"}, A)

    step("HR: swap request")
    swap_note = "none (no suitable shift)"
    time.sleep(1.1)
    kofi = api.call("POST", "/auth/login", {"phone": "+233200000003", "password": "Passw0rd!"}, ok=False)
    if isinstance(kofi, dict) and "accessToken" in kofi:
        K = kofi["accessToken"]
        api.call("POST", "/hr/me/availability", {"kind": "UNAVAILABLE", "dayOfWeek": "SUN", "note": "Church"}, K, ok=False)
        mine = api.call("GET", f"/hr/me/shifts?from={(today + timedelta(days=1)).isoformat()}&to={(today + timedelta(days=6)).isoformat()}", token=K)
        for shift in mine:
            res = api.call("POST", "/hr/me/swaps", {"scheduleId": shift["id"], "colleagueEmployeeId": eid("Selorm Agbeko"),
                                                    "reason": "Family funeral in Kumasi"}, K, ok=False)
            if isinstance(res, dict) and "_error" not in res:
                swap_note = "Kofi -> Selorm, pending"
                break
    return {"shifts_this_week": this_week, "shifts_next_week": next_week, "swap": swap_note}


def print_hr_summary(summary: dict):
    print("  HR:")
    print(f"    {summary['imported']} staff logins imported as employees, {summary['created']} employees without logins")
    print("    Try: Yaw (manager, Osu) sees Osu staff + Nii Lartey read-only (East Legon, covering Osu); no pay figures")
    print("         Ama (admin) sees everything incl. pay; Abena (cashier) has HR view + documents only (granted)")
    print("         Kofi (cashier) only has self-service: 'My Schedule' and 'My HR profile'")
    print("         Contracts due within 30 days: Edem Kpodo, Fiifi Mensah, Kojo Asamoah (probation); draft: Maame Ofori")
    print(f"    Schedule: {summary.get('shifts_this_week', 0)} shifts published this week, {summary.get('shifts_next_week', 0)} drafted for next week (+2 open)")
    print(f"         Swap request: {summary.get('swap')}. Kofi has 'unavailable Sundays'; Gifty unavailable Saturdays")


def main():
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from seed_local import Api, PASSWORD, STAFF  # noqa: E402  (reuse the seeder's HTTP client and accounts)

    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base", default="http://localhost:8080")
    args = parser.parse_args()
    api = Api(args.base, "unused")
    token = api.call("POST", "/auth/login", {"phone": STAFF[0][0], "password": PASSWORD})["accessToken"]
    time.sleep(1.1)
    if api.call("GET", "/hr/employees?size=1", token=token)["total"] > 0:
        raise SystemExit("This backend already has employees. Start from an empty database before seeding HR.")
    branches = {b["code"]: b for b in api.call("GET", "/branches", token=token)}
    if "OSU" not in branches or "ELG" not in branches:
        raise SystemExit("Run seed_local.py first (branches OSU and ELG are needed).")
    users = {u["phone"]: u for u in api.call("GET", "/users", token=token)}
    print_hr_summary(seed_hr(api, token, branches, users, step=lambda label: print(f"- {label}", flush=True)))


if __name__ == "__main__":
    sys.exit(main())

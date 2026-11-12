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
from datetime import date, datetime, timedelta

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


def seed_hr(api, admin_token: str, branches: dict, users_by_phone: dict, step=print, db_url: str | None = None) -> dict:
    """Seeds HR data. `branches` maps branch code -> branch record. `db_url` (local H2 or Docker Postgres) enables the attendance
    history behind timesheets, tip pools and payroll. Returns a summary for printing."""
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
    attendance_summary = seed_attendance(api, A, branches, people, linked, step)
    leave_summary = seed_leave(api, A, people, step)
    pay_summary = seed_pay(api, A, branches, people, linked, users_by_phone, db_url, step)
    recruitment_summary = seed_recruitment(api, A, branches, people, users_by_phone, pos, step)
    return {"imported": result["created"], "created": len(people), "skipped": result["skipped"], **schedule_summary, **attendance_summary,
            **leave_summary, **pay_summary, **recruitment_summary}


TEST_CLOCK_PIN = "2580"


def seed_attendance(api, A: str, branches: dict, people: dict, linked: dict, step=print) -> dict:
    """Clock PINs, people clocked in today (manager and POS routes) and a missing record waiting for approval."""
    import uuid as _uuid
    from datetime import datetime, timezone
    osu = branches["OSU"]["id"]
    step("HR: clock PINs")
    ids = [p["summary"]["id"] for p in people.values()] + [e["summary"]["id"] for e in linked.values()]
    for employee_id in ids:
        api.call("PUT", f"/hr/employees/{employee_id}/clock-pin", {"pin": TEST_CLOCK_PIN}, A, ok=False)

    step("HR: attendance today")
    clocked = []
    for name in ("Kwesi Arthur", "Fiifi Mensah", "Gifty Amoah"):
        res = api.call("POST", "/hr/attendance/clock", {"employeeId": people[name]["summary"]["id"], "branchId": osu, "action": "CLOCK_IN",
                                                      "idempotencyKey": str(_uuid.uuid4()), "reason": "Seed data"}, A, ok=False)
        if isinstance(res, dict) and "_error" not in res:
            clocked.append(name)
    # one person clocks in through the POS terminal signed in as Kofi (cashier), then takes a break
    time.sleep(1.1)
    kofi = api.call("POST", "/auth/login", {"phone": "+233200000003", "password": "Passw0rd!"}, ok=False)
    if isinstance(kofi, dict) and "accessToken" in kofi:
        K = kofi["accessToken"]
        dzifa = people["Dzifa Tetteh"]["summary"]["id"]
        res = api.call("POST", "/hr/pos/clock", {"employeeId": dzifa, "pin": TEST_CLOCK_PIN, "action": "CLOCK_IN",
                                                "idempotencyKey": str(_uuid.uuid4()), "terminalId": "till-seed"}, K, ok=False)
        if isinstance(res, dict) and "_error" not in res:
            clocked.append("Dzifa Tetteh (POS)")
            api.call("POST", "/hr/pos/clock", {"employeeId": dzifa, "pin": TEST_CLOCK_PIN, "action": "BREAK_START", "breakType": "MEAL",
                                              "idempotencyKey": str(_uuid.uuid4()), "terminalId": "till-seed"}, K, ok=False)

    step("HR: correction waiting for approval")
    yesterday = date.today() - timedelta(days=1)
    clock_in = datetime(yesterday.year, yesterday.month, yesterday.day, 7, 18, tzinfo=timezone.utc)
    clock_out = datetime(yesterday.year, yesterday.month, yesterday.day, 15, 4, tzinfo=timezone.utc)
    correction = api.call("POST", "/hr/attendance/corrections", {"employeeId": people["Kwesi Arthur"]["summary"]["id"], "branchId": osu,
                                                                 "clockInAt": clock_in.isoformat().replace("+00:00", "Z"),
                                                                 "clockOutAt": clock_out.isoformat().replace("+00:00", "Z"),
                                                                 "breakMinutes": 30, "reason": "Tablet was offline at opening"}, A, ok=False)
    pending = isinstance(correction, dict) and correction.get("status") == "PENDING"
    return {"clocked_in": clocked, "correction_pending": pending}


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


# Same definitions as migration V32; created only when missing (local H2 without Flyway).
LEAVE_TYPES = [
    ("ANNUAL", "Annual leave", True, True, False, False, "WORKDAYS", ["BRANCH_MANAGER"], 1),
    ("SICK", "Sick leave", True, True, True, True, "WORKDAYS", ["BRANCH_MANAGER"], 2),
    ("STUDY", "Study leave", True, True, False, False, "WORKDAYS", ["BRANCH_MANAGER", "HR"], 7),
]


def _weekday_on_or_before(day: date) -> date:
    while day.weekday() >= 5:
        day -= timedelta(days=1)
    return day


def seed_leave(api, A: str, people: dict, step=print) -> dict:
    """Leave entitlements (test values), an HR adjustment and requests in each state."""
    today = date.today()

    def eid(name):
        return people[name]["summary"]["id"]

    step("HR: leave types & entitlements")
    types = {t["code"]: t for t in api.call("GET", "/hr/leave-types", token=A)}
    for code, name, paid, deducts, document, negative, basis, steps, order in LEAVE_TYPES:
        if code not in types:
            types[code] = api.call("POST", "/hr/leave-types", {
                "code": code, "name": name, "paid": paid, "deductsBalance": deducts, "requiresDocument": document,
                "allowNegative": negative, "countBasis": basis, "approvalSteps": steps, "displayOrder": order}, A)
    # Test values only: real entitlements are business and legal decisions configured by HR.
    for code, days, accrual, carry, etype in (("ANNUAL", 15, "UPFRONT", 5, None), ("ANNUAL", 8, "MONTHLY", 0, "PART_TIME"),
                                               ("SICK", 10, "UPFRONT", 0, None), ("STUDY", 5, "UPFRONT", 0, None)):
        api.call("POST", "/hr/leave-policies", {"leaveTypeId": types[code]["id"], "employmentType": etype, "annualDays": days,
                                                "accrual": accrual, "carryOverMaxDays": carry, "effectiveFrom": "2020-01-01"}, A, ok=False)

    step("HR: leave balances & requests")
    api.call("POST", f"/hr/employees/{eid('Akua Nyarko')}/leave-balances/adjust",
             {"leaveTypeId": types["ANNUAL"]["id"], "year": today.year, "days": -3, "note": "Taken before HR went live (test data)"}, A, ok=False)

    # a Monday-to-Friday block three weeks out, kept inside one year
    start = today + timedelta(days=21 + (7 - (today + timedelta(days=21)).weekday()) % 7)
    if (start + timedelta(days=4)).year != start.year:
        start += timedelta(days=14)
    pending = api.call("POST", "/hr/leave-requests", {"employeeId": eid("Kwesi Arthur"), "leaveTypeId": types["ANNUAL"]["id"],
                                                      "startDate": start.isoformat(), "endDate": (start + timedelta(days=4)).isoformat(),
                                                      "reason": "Family visit (test data)"}, A, ok=False)

    # approved by HR for two days next week, clashing with drafted evening shifts
    next_monday = today + timedelta(days=7 - today.weekday())
    thursday = next_monday + timedelta(days=3)
    if thursday.year == (thursday + timedelta(days=1)).year:
        api.call("POST", "/hr/leave-requests", {"employeeId": eid("Fiifi Mensah"), "leaveTypeId": types["ANNUAL"]["id"],
                                                "startDate": thursday.isoformat(), "endDate": (thursday + timedelta(days=1)).isoformat(),
                                                "reason": "Wedding (test data)", "recordApproved": True}, A, ok=False)

    api.call("POST", "/hr/leave-requests", {"employeeId": eid("Adjoa Badu"), "leaveTypeId": types["STUDY"]["id"],
                                            "startDate": start.isoformat(), "endDate": start.isoformat(), "reason": "Food safety exam"}, A, ok=False)

    # Kofi (cashier, linked login) reports sick for the last working day through self-service; the sick note is still missing
    sick_note = "not created"
    time.sleep(1.1)
    kofi = api.call("POST", "/auth/login", {"phone": "+233200000003", "password": "Passw0rd!"}, ok=False)
    if isinstance(kofi, dict) and "accessToken" in kofi:
        day = _weekday_on_or_before(today - timedelta(days=1))
        res = api.call("POST", "/hr/me/leave-requests", {"leaveTypeId": types["SICK"]["id"], "startDate": day.isoformat(),
                                                         "endDate": day.isoformat(), "reason": "Fever"}, kofi["accessToken"], ok=False)
        if isinstance(res, dict) and "_error" not in res:
            sick_note = f"Kofi sick on {day}, pending, document missing"
    return {"leave_pending": isinstance(pending, dict) and pending.get("status") == "PENDING", "leave_sick": sick_note}


# ==================================================================== attendance history, timesheets, tips, payroll

# Who worked where in the past (feeds timesheets, tip pools and payroll): branch, start, end, break minutes, weekdays (0=Mon).
WORK_HISTORY = {
    "Kwesi Arthur": ("OSU", "07:00", "15:00", 30, [0, 1, 2, 3, 4]),
    "Akua Nyarko": ("OSU", "07:00", "15:00", 30, [1, 2, 3, 4, 5]),
    "Maame Ofori": ("OSU", "07:00", "15:00", 30, [5, 6]),
    "Gifty Amoah": ("OSU", "07:00", "15:00", 30, [1, 3, 5]),
    "Selorm Agbeko": ("OSU", "11:00", "19:00", 30, [0, 1, 3, 4, 5]),
    "Fiifi Mensah": ("OSU", "15:00", "23:00", 30, [0, 1, 2, 3, 4]),
    "Dzifa Tetteh": ("OSU", "15:00", "23:00", 30, [2, 3, 4, 5, 6]),
    "Afia Boakye": ("OSU", "15:00", "23:00", 30, [4, 5, 6]),
    "Kobby Ansah": ("OSU", "20:00", "02:00", 15, [3, 4, 5]),
    "Adjoa Badu": ("ELG", "10:00", "18:00", 45, [0, 1, 2, 3, 4]),
    "Kojo Asamoah": ("ELG", "10:00", "18:00", 45, [2, 3, 4, 5, 6]),
    "Nii Lartey": ("ELG", "10:00", "18:00", 45, [0, 1, 4, 5, 6]),
    "Ewurabena Quansah": ("ELG", "10:00", "18:00", 45, [5, 6]),
    "Yaw Frimpong": ("ELG", "07:00", "15:00", 30, [0, 1, 2, 3, 4]),
}
KOFI_HISTORY = ("OSU", "09:00", "17:00", 30, [0, 1, 2, 3, 4])

# Test values only, NOT tax or legal advice: real rates are configured by HR/finance.
PAYROLL_RULES = [
    {"code": "SSNIT_EE", "name": "Social security (employee)", "kind": "EMPLOYEE_DEDUCTION", "method": "PERCENT", "base": "BASIC",
     "ratePercent": 5.5, "preTax": True, "displayOrder": 1},
    {"code": "PAYE", "name": "Income tax (PAYE)", "kind": "EMPLOYEE_DEDUCTION", "method": "BRACKETS", "base": "TAXABLE", "displayOrder": 2,
     "brackets": [{"upTo": 490, "ratePercent": 0}, {"upTo": 600, "ratePercent": 5}, {"upTo": 730, "ratePercent": 10},
                  {"upTo": 3896.67, "ratePercent": 17.5}, {"upTo": 19896.67, "ratePercent": 25}, {"upTo": 50416.67, "ratePercent": 30},
                  {"upTo": None, "ratePercent": 35}]},
    {"code": "WELFARE", "name": "Staff welfare fund", "kind": "EMPLOYEE_DEDUCTION", "method": "FIXED", "base": "GROSS", "fixedAmount": 20,
     "employmentTypes": ["FULL_TIME"], "displayOrder": 3},
    {"code": "SSNIT_ER", "name": "Social security (employer)", "kind": "EMPLOYER_CONTRIBUTION", "method": "PERCENT", "base": "BASIC",
     "ratePercent": 13, "displayOrder": 4},
]

# Service charge points per position (test values)
SERVICE_POINTS = {"HEAD-CHEF": 3, "SOUS-CHEF": 2.5, "LINE-COOK": 2, "PREP-COOK": 1.5, "KITCHEN-ASST": 1, "WAITER": 2, "HOST": 1.5,
                  "CASHIER": 1.5, "BARTENDER": 2, "BARISTA": 1.5, "CLEANER": 1, "STEWARD": 1, "SECURITY": 1}


def _login(api, phone: str) -> str | None:
    time.sleep(1.1)  # refresh tokens are second-granular
    res = api.call("POST", "/auth/login", {"phone": phone, "password": "Passw0rd!"}, ok=False)
    return res.get("accessToken") if isinstance(res, dict) else None


def _ok(res) -> bool:
    return isinstance(res, dict) and "_error" not in res


def _rows(res) -> list:
    return res.get("items", res.get("content", [])) if isinstance(res, dict) else (res or [])


def backfill_attendance(db_url: str, branches: dict, people: dict, linked: dict, users_by_phone: dict) -> int:
    """Closed time entries from the 1st of last month to two days ago. There is no API for past clock-ins, so this writes
    to the dev database directly (like the order back-dating in seed_local.py)."""
    import random as _random
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from seed_local import h2_sql  # noqa: E402

    rng = _random.Random(11)
    today = date.today()
    last_month_end = today.replace(day=1) - timedelta(days=1)
    first = last_month_end.replace(day=1)
    transfer_day = today - timedelta(days=35)  # Dzifa moved from East Legon to Osu
    cover_from = today - timedelta(days=3)  # Kobby covering the East Legon bar
    exit_day = today - timedelta(days=12)  # Yaw Frimpong resigned

    workers = [(name, people[name]["summary"]["id"], None, plan) for name, plan in WORK_HISTORY.items()]
    kofi = linked.get("+233200000003")
    if kofi:
        workers.append(("Kofi Boateng", kofi["summary"]["id"], users_by_phone["+233200000003"]["id"], KOFI_HISTORY))

    def ts(dt):
        return f"TIMESTAMP WITH TIME ZONE '{dt.strftime('%Y-%m-%d %H:%M:%S')}+00'"

    def q(value):
        return "NULL" if value is None else "'" + str(value).replace("'", "''") + "'"

    statements = []
    day = first
    while day <= today - timedelta(days=2):
        for name, employee_id, user_id, (branch, start, end, brk, weekdays) in workers:
            if day.weekday() not in weekdays:
                continue
            if name in ("Kofi Boateng", "Akua Nyarko") and day > last_month_end:
                continue  # Kofi's current month is self-service (clock-ins, sick day); Akua is on leave
            if name == "Yaw Frimpong" and day > exit_day:
                continue
            if rng.random() < 0.04:
                continue  # an absence now and then
            where = "ELG" if (name == "Dzifa Tetteh" and day < transfer_day) or (name == "Kobby Ansah" and day >= cover_from) else branch
            sh, sm = map(int, start.split(":"))
            eh, em = map(int, end.split(":"))
            clock_in = datetime(day.year, day.month, day.day, sh, sm) + timedelta(minutes=rng.choice([-9, -5, -3, 0, 2, 4, 7, 14, 22]))
            out_day = day + timedelta(days=1) if (eh, em) <= (sh, sm) else day
            clock_out = datetime(out_day.year, out_day.month, out_day.day, eh, em) + timedelta(minutes=rng.choice([-4, 0, 3, 6, 11, 18]))
            note = "Seed history"
            if name == "Fiifi Mensah" and day.day in (10, 24):
                clock_out += timedelta(hours=2, minutes=30)  # overtime for the timesheet rules to pick up
                note = "Stayed for a private event"
            statements.append(
                "INSERT INTO time_entries (id, user_id, employee_id, source, clock_out_source, unscheduled, version, branch_id, clock_in_at, "
                f"clock_out_at, break_minutes, notes, created_at, updated_at) VALUES ({q(uuid.uuid4())}, {q(user_id)}, {q(employee_id)}, "
                f"'MANAGER', 'MANAGER', FALSE, 0, {q(branches[where]['id'])}, {ts(clock_in)}, {ts(clock_out)}, {brk}, {q(note)}, "
                f"{ts(clock_in)}, {ts(clock_out)})")
        day += timedelta(days=1)
    batch: list[str] = []
    for statement in statements + [None]:
        # the H2 shell gets the SQL on the command line, which Windows caps at ~32k characters
        if batch and (statement is None or sum(len(b) + 1 for b in batch) + len(statement) > 20000):
            h2_sql(db_url, ";".join(batch))
            batch = []
        if statement is not None:
            batch.append(statement)
    return len(statements)


def seed_pay(api, A: str, branches: dict, people: dict, linked: dict, users_by_phone: dict, db_url: str | None, step=print) -> dict:
    """Payroll and tip rules, timesheets, tip and service charge pools, and payroll runs in several states.
    Ama (admin) prepares; Yaw (Osu manager, granted payroll approval) approves, so every run and pool has two people."""
    today = date.today()
    last_month_end = today.replace(day=1) - timedelta(days=1)
    first = last_month_end.replace(day=1)
    month = {"periodStart": first.isoformat(), "periodEnd": last_month_end.isoformat()}
    osu, elg = branches["OSU"]["id"], branches["ELG"]["id"]
    notes: list[str] = []
    summary = {"pay": notes}

    def eid(name):
        return people[name]["summary"]["id"]

    step("HR: payroll rules, sharing rules & approver grant")
    for rule in PAYROLL_RULES:
        api.call("POST", "/hr/payroll-rules", {**rule, "effectiveFrom": "2020-01-01", "note": "Test value only; set real rates in HR settings"}, A)
    positions = {p["code"]: p["id"] for p in api.call("GET", "/hr/positions?includeArchived=true", token=A)}
    api.call("POST", "/hr/tip-rules", {"source": "TIPS", "method": "HOURS", "housePercent": 0, "taxable": True, "minMinutes": 60,
                                       "effectiveFrom": "2020-01-01", "note": "Tips shared by hours worked (test data)"}, A)
    api.call("POST", "/hr/tip-rules", {"source": "SERVICE_CHARGE", "method": "POINTS", "housePercent": 20, "taxable": True, "minMinutes": 120,
                                       "positionPoints": {positions[c]: v for c, v in SERVICE_POINTS.items() if c in positions},
                                       "effectiveFrom": "2020-01-01", "note": "House keeps 20%, the rest by position points (test data)"}, A)
    yaw = users_by_phone.get("+233200000002")
    if yaw:
        api.call("PUT", f"/hr/access/grants/{yaw['id']}", {"permissions": ["hr.payroll.view", "hr.payroll.approve", "hr.tips.manage",
                                                                           "hr.service_charge.manage"]}, A)
    kofi = linked.get("+233200000003")
    if kofi:  # gives Kofi (cashier login) a payslip under 'My payslips'
        contract = api.call("POST", f"/hr/employees/{kofi['summary']['id']}/contracts",
                            {"contractType": "PERMANENT", "startDate": kofi["summary"]["hireDate"], "salaryType": "MONTHLY", "salaryAmount": "2500.00",
                             "weeklyHours": 40, "terms": "Standard terms of employment. Test data."}, A)
        api.call("POST", f"/hr/contracts/{contract['id']}/activate", {}, A)

    if not db_url:
        notes.append("no attendance history (no H2 path): timesheets, pools and payroll runs skipped")
        return summary

    step("HR: attendance history (start of last month to two days ago)")
    summary["entries"] = backfill_attendance(db_url, branches, people, linked, users_by_phone)

    step("HR: timesheets")
    for branch in (osu, elg):
        api.call("POST", "/hr/timesheets/generate", {"branchId": branch, "from": month["periodStart"], "to": month["periodEnd"]}, A)
    def month_sheets():
        return [s for b in (osu, elg) for s in _rows(api.call("GET", f"/hr/timesheets?branchId={b}&from={month['periodStart']}&to={month['periodEnd']}", token=A))]

    held = []
    for sheet in month_sheets():
        sid = sheet["id"]
        api.call("POST", f"/hr/timesheets/{sid}/submit", {}, A)
        if sheet["employeeName"] == "Ewurabena Quansah":
            held.append(sheet["employeeName"])  # left submitted: the East Legon payroll run flags her unlocked hours
            continue
        api.call("POST", f"/hr/timesheets/{sid}/review", {"note": "Checked against the rota"}, A)
        api.call("POST", f"/hr/timesheets/{sid}/approve", {"note": "Approved (test data)"}, A)
        api.call("POST", f"/hr/timesheets/{sid}/lock", {}, A)
    locked = sum(1 for s in month_sheets() if s["status"] == "LOCKED")
    notes.append(f"Timesheets {first:%B}: {locked} locked; {', '.join(held) or 'none'} left submitted")
    # first week of this month at Osu: every other sheet submitted for Yaw to review, the rest still draft
    week_start = today.replace(day=1)
    week_end = min(week_start + timedelta(days=6), today - timedelta(days=2))
    if week_end >= week_start:
        api.call("POST", "/hr/timesheets/generate", {"branchId": osu, "from": week_start.isoformat(), "to": week_end.isoformat()}, A)
        current = _rows(api.call("GET", f"/hr/timesheets?branchId={osu}&from={week_start.isoformat()}&to={week_end.isoformat()}", token=A))
        for sheet in current[::2]:
            api.call("POST", f"/hr/timesheets/{sheet['id']}/submit", {}, A, ok=False)
        notes.append(f"Timesheets {week_start:%d}-{week_end:%d %b} (Osu): {len(current[::2])} submitted for Yaw to review, {len(current[1::2])} draft")

    step("HR: tip & service charge pools")
    Y = _login(api, "+233200000002")
    for source in ("TIPS", "SERVICE_CHARGE"):
        pool = api.call("POST", "/hr/tip-pools", {"branchId": osu, "source": source, **month}, A)
        pool = pool.get("summary", pool)
        calc = api.call("POST", f"/hr/tip-pools/{pool['id']}/calculate", {}, A)
        approved = Y and _ok(api.call("POST", f"/hr/tip-pools/{pool['id']}/approve", {}, Y, ok=False))
        s = calc.get("summary", calc)
        notes.append(f"Osu {source.lower().replace('_', ' ')} {first:%B}: GHS {s.get('collectedAmount')} from {s.get('orderCount')} orders, "
                     f"{'approved by Yaw' if approved else 'calculated'}")
    if today.day > 2:
        pool = api.call("POST", "/hr/tip-pools", {"branchId": osu, "source": "TIPS", "periodStart": today.replace(day=1).isoformat(),
                                                  "periodEnd": (today - timedelta(days=1)).isoformat()}, A)
        api.call("POST", f"/hr/tip-pools/{pool.get('summary', pool)['id']}/calculate", {}, A)
        notes.append(f"Osu tips {today:%B} to date: calculated, waiting for approval (approve as Yaw)")

    step("HR: payroll runs")
    run = api.call("POST", "/hr/payroll-runs", {"branchId": osu, **month, "payDate": month["periodEnd"],
                                                "notes": f"{first:%B %Y} salaries (test data)"}, A)
    rid = run.get("summary", run)["id"]
    api.call("POST", f"/hr/payroll-runs/{rid}/adjustments", {"employeeId": eid("Kwesi Arthur"), "kind": "EARNING", "taxable": True,
                                                             "description": "Kitchen hygiene inspection bonus", "amount": 300}, A)
    api.call("POST", f"/hr/payroll-runs/{rid}/adjustments", {"employeeId": eid("Selorm Agbeko"), "kind": "DEDUCTION", "taxable": False,
                                                             "description": "Salary advance recovery (1 of 3)", "amount": 200}, A)
    api.call("POST", f"/hr/payroll-runs/{rid}/calculate", {}, A)
    status = "calculated"
    res = api.call("POST", f"/hr/payroll-runs/{rid}/approve", {}, Y, ok=False) if Y else None
    if _ok(res):
        status = "approved by Yaw"
        if _ok(api.call("POST", f"/hr/payroll-runs/{rid}/finalize", {}, A, ok=False)):
            status = "finalized"
            if _ok(api.call("POST", f"/hr/payroll-runs/{rid}/pay", {"method": "MOBILE_MONEY", "reference": f"MOMO-BULK-{first:%Y%m}"}, A, ok=False)):
                status = "paid by mobile money"
    elif res:
        status += f" (approval refused: {res.get('_detail', '')[:120]})"
    s = api.call("GET", f"/hr/payroll-runs/{rid}", token=A)
    s = s.get("summary", s)
    notes.append(f"Osu {first:%B} payroll {s.get('runNumber', '')}: {status}; {s.get('employeeCount')} payslips, net GHS {s.get('netTotal')}")

    run = api.call("POST", "/hr/payroll-runs", {"branchId": elg, **month, "payDate": month["periodEnd"]}, A)
    api.call("POST", f"/hr/payroll-runs/{run.get('summary', run)['id']}/calculate", {}, A)
    notes.append(f"East Legon {first:%B} payroll: calculated; cannot be approved until Ewurabena's timesheet is approved and locked")
    return summary


# ==================================================================== recruitment

# Candidates are fictional: first, last, phone, source, notes
CANDIDATES = {
    "esi": ("Esi", "Larbi", "+233552000001", "WALK_IN", "Dropped off a CV at the Osu front desk"),
    "kwaku": ("Kwaku", "Owusu-Ansah", "+233552000002", "JOB_BOARD", "Two years at a hotel restaurant in Takoradi"),
    "mawuli": ("Mawuli", "Dzokoto", "+233552000003", "REFERRAL", "Referred by Selorm Agbeko"),
    "naa": ("Naa Okailey", "Tetteh", "+233552000004", "SOCIAL_MEDIA", "Applied through the Instagram post"),
    "abla": ("Abla", "Gbeve", "+233552000005", "JOB_BOARD", "Strong wine knowledge"),
    "kwabena": ("Kwabena", "Sarfo", "+233552000006", "WALK_IN", None),
    "comfort": ("Comfort", "Adu", "+233552000007", "AGENCY", "Placed by a catering staffing agency"),
    "eyram": ("Eyram", "Kumah", "+233552000008", "JOB_BOARD", None),
    "baaba": ("Baaba", "Essien", "+233552000009", "WALK_IN", "Previously worked security at a mall"),
}


def seed_recruitment(api, A: str, branches: dict, people: dict, users_by_phone: dict, pos: dict, step=print) -> dict:
    """Vacancies in each state, a full pipeline at Osu, interviews for Yaw and one completed hire at East Legon."""
    from datetime import timezone
    today = date.today()
    osu, elg = branches["OSU"]["id"], branches["ELG"]["id"]
    yaw = (users_by_phone.get("+233200000002") or {}).get("id")

    def at(days, hour):
        d = today + timedelta(days=days)
        return datetime(d.year, d.month, d.day, hour, 0, tzinfo=timezone.utc).isoformat().replace("+00:00", "Z")

    def vacancy(branch, pcode, title, etype, openings, lo, hi, closes, desc, reqs):
        v = api.call("POST", "/hr/vacancies", {"branchId": branch, "positionId": pos[pcode], "title": title, "employmentType": etype,
                                               "openings": openings, "salaryMin": lo, "salaryMax": hi,
                                               "closingDate": (today + timedelta(days=closes)).isoformat() if closes else None,
                                               "hiringManagerId": yaw if branch == osu else None, "description": desc, "requirements": reqs}, A)
        return v.get("summary", v)

    def apply(v, key):
        first, last, phone, source, notes = CANDIDATES[key]
        cand = {"firstName": first, "lastName": last, "phone": phone, "source": source, "notes": notes,
                "email": f"{first.split()[0].lower()}.{last.lower()}@example.test"}
        if source == "REFERRAL":
            cand["referredByEmployeeId"] = people["Selorm Agbeko"]["summary"]["id"]
        app = api.call("POST", f"/hr/vacancies/{v['id']}/applications", {"candidate": cand}, A)
        return app.get("summary", app)

    def move(app, *stages, note=None):
        for stage in stages:
            api.call("POST", f"/hr/applications/{app['id']}/stage", {"stage": stage, "note": note}, A)

    def interview(app, body):
        res = api.call("POST", f"/hr/applications/{app['id']}/interviews", body, A)
        return res.get("interviews", [])

    step("HR: vacancies")
    waiter = vacancy(osu, "WAITER", "Waiter / Waitress", "FULL_TIME", 2, 2400, 2800, 21,
                     "Join the Osu floor team for lunch and evening service. You'll look after guests from the moment they sit down: "
                     "taking orders on the POS, recommending dishes and drinks, and keeping your section running smoothly on busy nights.\n\n"
                     "Shifts are 8 hours on a rotating rota, with two days off a week. Staff meals, uniform and tips shared through payroll.",
                     "At least six months' experience as a waiter or in hospitality\nAvailable on weekends and public holidays\n"
                     "Confident speaking English; Twi or Ga is a plus\nFriendly, calm under pressure and a team player")
    cook = vacancy(elg, "LINE-COOK", "Line Cook", "FULL_TIME", 1, 2800, 3300, 10,
                   "Run the grill and stew station at East Legon, preparing jollof, charcoal chicken and our soups to recipe and on time.",
                   "Food handler certificate\nTwo years on a busy line\nComfortable with a charcoal grill")
    vacancy(osu, "BARISTA", "Barista (part-time)", "PART_TIME", 1, None, None, None, "Weekend coffee bar at Osu.", "Latte art a plus.")
    host = vacancy(elg, "HOST", "Host / Hostess", "PART_TIME", 1, None, None, 28,
                   "Be the first face our guests see at East Legon. You'll greet guests, manage reservations and the waiting list, "
                   "seat tables and keep the front door welcoming during lunch and dinner.\n\nAbout 24 hours a week, mostly evenings and weekends.",
                   "Warm, well-presented and organised\nGood phone manner\nBasic computer skills (bookings are on a tablet)")
    kitchen_assistant = vacancy(osu, "KITCHEN-ASST", "Kitchen Assistant (internal)", "CASUAL", 1, None, None, None,
                                "Help the Osu kitchen with prep and dishwashing. Open to current staff and referrals only.", None)
    guard = vacancy(elg, "SECURITY", "Night Security Officer", "CONTRACT", 1, 2000, 2300, 30,
                    "Night cover for the East Legon site.", "Licensed security officer.")
    for v in (waiter, cook, host, kitchen_assistant, guard):
        api.call("POST", f"/hr/vacancies/{v['id']}/status", {"status": "OPEN"}, A)
    # Careers page: waiter and host are advertised; the kitchen assistant job stays internal (open, not published);
    # the line cook is published but gets filled below, which takes it off the page; the guard job goes on hold.
    for v in (waiter, cook, host, guard):
        api.call("POST", f"/hr/vacancies/{v['id']}/publish", {"published": True}, A)

    step("HR: applicants, interviews & offers")
    apply(waiter, "esi")
    move(apply(waiter, "kwaku"), "SCREENING", note="Good phone manner")
    mawuli = apply(waiter, "mawuli")
    move(mawuli, "SCREENING", "INTERVIEW")
    interview(mawuli, {"kind": "IN_PERSON", "scheduledAt": at(1, 10), "durationMinutes": 30, "interviewerId": yaw, "location": "Osu, upstairs office"})
    naa = apply(waiter, "naa")
    move(naa, "SCREENING", "INTERVIEW")
    for i in interview(naa, {"kind": "PHONE", "scheduledAt": at(-4, 11), "durationMinutes": 20, "interviewerId": yaw}):
        api.call("PUT", f"/hr/interviews/{i['id']}", {"outcome": "PASSED", "score": 4, "feedback": "Warm and confident; knows the menu style"}, A)
    move(naa, "TRIAL_SHIFT")
    interview(naa, {"kind": "TRIAL_SHIFT", "scheduledAt": at(2, 15), "durationMinutes": 240, "interviewerId": yaw, "location": "Osu floor, evening service"})
    abla = apply(waiter, "abla")
    move(abla, "SCREENING", "INTERVIEW", "OFFER")
    api.call("PUT", f"/hr/applications/{abla['id']}/offer", {"contractType": "PERMANENT", "salaryType": "MONTHLY", "salaryAmount": 2600,
                                                            "weeklyHours": 45, "startDate": (today + timedelta(days=14)).isoformat()}, A)
    move(apply(waiter, "kwabena"), "REJECTED", note="No weekend availability")

    step("HR: completed hire (East Legon line cook)")
    comfort = apply(cook, "comfort")
    apply(cook, "eyram")
    move(comfort, "SCREENING", "INTERVIEW")
    for i in interview(comfort, {"kind": "IN_PERSON", "scheduledAt": at(-6, 9), "durationMinutes": 45}):
        api.call("PUT", f"/hr/interviews/{i['id']}", {"outcome": "PASSED", "score": 5, "feedback": "Excellent knife skills in the cook test"}, A)
    move(comfort, "OFFER")
    api.call("PUT", f"/hr/applications/{comfort['id']}/offer", {"contractType": "PERMANENT", "salaryType": "MONTHLY", "salaryAmount": 3000,
                                                               "weeklyHours": 45, "startDate": today.isoformat()}, A)
    api.call("POST", f"/hr/applications/{comfort['id']}/offer-response", {"accepted": True, "note": "Accepted by phone"}, A)
    hired = api.call("POST", f"/hr/applications/{comfort['id']}/hire", {"hireDate": today.isoformat()}, A)

    step("HR: vacancy on hold")
    apply(guard, "baaba")
    api.call("POST", f"/hr/vacancies/{guard['id']}/status", {"status": "ON_HOLD", "reason": "Waiting for the new security contract"}, A)
    return {"recruitment_hired": "employeeId" in hired}


def print_hr_summary(summary: dict):
    print("  HR:")
    print(f"    {summary['imported']} staff logins imported as employees, {summary['created']} employees without logins")
    print("    Try: Yaw (manager, Osu) sees Osu staff + Nii Lartey read-only (East Legon, covering Osu); no pay figures")
    print("         Ama (admin) sees everything incl. pay; Abena (cashier) has HR view + documents only (granted)")
    print("         Kofi (cashier) only has self-service: My Schedule, Leave, Payslips, Tips and HR profile")
    print("         Contracts due within 30 days: Edem Kpodo, Fiifi Mensah, Kojo Asamoah (probation); draft: Maame Ofori")
    print(f"    Schedule: {summary.get('shifts_this_week', 0)} shifts published this week, {summary.get('shifts_next_week', 0)} drafted for next week (+2 open)")
    print(f"         Swap request: {summary.get('swap')}. Kofi has 'unavailable Sundays'; Gifty unavailable Saturdays")
    print(f"    Attendance: every employee's POS clock PIN is {TEST_CLOCK_PIN} (test data). Clocked in now: {', '.join(summary.get('clocked_in', [])) or 'nobody'}")
    print(f"         Missing record for Kwesi Arthur yesterday {'waiting for approval (requested by Ama; approve as Yaw)' if summary.get('correction_pending') else 'not created'}")
    print("    Leave (test entitlements: annual 15 days, part-time 8 monthly, sick 10, study 5):")
    print(f"         Kwesi Arthur annual leave {'pending (approve as Yaw)' if summary.get('leave_pending') else 'not created'}; Fiifi Mensah approved")
    print("         for next Thu-Fri (clashes with drafted shifts); Adjoa Badu study leave needs branch manager then HR")
    print(f"         {summary.get('leave_sick')}; Akua Nyarko annual balance adjusted -3")
    print(f"    Pay ({summary.get('entries', 0)} past clock records; payroll rules are TEST values, not tax advice):")
    for line in summary.get("pay", []):
        print(f"         {line}")
    print("         Kofi (cashier) sees his payslip and tip share under 'My payslips' / 'My tips'")
    print("    Recruitment: Osu waiter vacancy with a full pipeline (offer out to Abla Gbeve); Yaw has an interview tomorrow")
    print(f"         and a trial shift; East Legon line cook {'filled (Comfort Adu hired)' if summary.get('recruitment_hired') else 'open'};"
          " barista draft; security on hold")
    print("         Careers page (/careers): Waiter / Waitress and Host / Hostess are published; Kitchen Assistant is open but")
    print("         internal (not published); filled, on-hold and draft vacancies never show")


def main():
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from seed_local import Api, PASSWORD, STAFF  # noqa: E402  (reuse the seeder's HTTP client and accounts)

    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base", default="http://localhost:8080")
    parser.add_argument("--db", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "backend", "target", "dev-db", "rm"),
                        help="H2 file path (without .mv.db), for the attendance history behind timesheets, tips and payroll")
    parser.add_argument("--pg", metavar="CONTAINER", nargs="?", const="restaurant_postgres",
                        help="Use PostgreSQL in this Docker container (default restaurant_postgres) instead of H2")
    args = parser.parse_args()
    from seed_local import database_target  # noqa: E402
    db_url = database_target(args.pg, args.db)
    api = Api(args.base, "unused")
    token = api.call("POST", "/auth/login", {"phone": STAFF[0][0], "password": PASSWORD})["accessToken"]
    time.sleep(1.1)
    if api.call("GET", "/hr/employees?size=1", token=token)["total"] > 0:
        raise SystemExit("This backend already has employees. Start from an empty database before seeding HR.")
    branches = {b["code"]: b for b in api.call("GET", "/branches", token=token)}
    if "OSU" not in branches or "ELG" not in branches:
        raise SystemExit("Run seed_local.py first (branches OSU and ELG are needed).")
    users = {u["phone"]: u for u in api.call("GET", "/users", token=token)}
    print_hr_summary(seed_hr(api, token, branches, users, step=lambda label: print(f"- {label}", flush=True), db_url=db_url))


if __name__ == "__main__":
    sys.exit(main())

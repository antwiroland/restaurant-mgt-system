# Attendance Gateway

A small program that runs on a computer **at the restaurant**, reads the biometric attendance terminals on the local
network (Hikvision, over ISAPI), keeps every scan in a local SQLite queue, and sends it to the Restaurant Manager
backend whenever the internet is available.

```
Hikvision terminals ──LAN──> Attendance Gateway (this program, SQLite queue) ──HTTPS──> Restaurant Manager backend
```

- Works without the web app being open, and keeps collecting scans while the internet is down. A scan stays in the
  queue until the backend has confirmed it; nothing unsent is ever deleted.
- After a restart or a network cut it asks each terminal for the scans it stored in the meantime (event search), so
  missed live events are recovered. Scans read twice are stored once.
- Device logins stay on this computer (encrypted). The backend never receives them, nor any biometric template:
  only the person's device user number, the time, the action and how they authenticated.
- Device time is kept: the backend records the time of the scan, not the time it arrived.

Requires **Java 21**.

## 1. Register the gateway and devices (in the web app)

As an admin: **HR → Attendance Devices**.

1. **Gateways → Register**: give it a name and an id (e.g. `GATEWAY-001`). Copy the key shown (it is shown once).
2. **Devices → Register device** for each terminal: serial number (from the terminal's system information), IP address,
   port, branch, and this gateway. Leave *Which action is a scan?* on **As the device reports** for terminals where staff
   choose check-in / break / check-out (attendance mode). Choose a fixed action only for a reader that does one thing
   (for example an exit door that only clocks people out).
3. **People on devices**: link each enrolled person's device user number (the "employee no." used when enrolling their
   face, fingerprint or card) to their employee record.

## 2. Configure the gateway

Create `config/application.yml` next to the jar (or use environment variables):

```yaml
gateway:
  backend-url: https://your-restaurant-backend.example.com
  gateway-id: GATEWAY-001
  key: rmgw_xxxxxxxxxx_...           # or set GATEWAY_KEY
  device-time-zone: Africa/Accra      # the zone the terminals' clocks are set to
  devices:                            # logins for each terminal registered in the web app
    - serial-number: DS-K1T341AMF-OSU01
      username: admin
      password: enc:6l1b0...          # encrypted, see below
```

### Encrypting device passwords

```bash
java -jar attendance-gateway.jar new-master-key            # once; keep the value safe
export GATEWAY_MASTER_KEY=<that value>                      # Windows: setx GATEWAY_MASTER_KEY "<value>"
java -jar attendance-gateway.jar encrypt "DevicePassw0rd"   # prints enc:..., paste it into the config
```

The gateway needs `GATEWAY_MASTER_KEY` set whenever it runs. Without it, a copy of the config file does not reveal the
device passwords. Restrict the config folder to the account that runs the gateway.

## 3. Prepare the terminals

On each Hikvision terminal (web interface or iVMS-4200):

- Enable **ISAPI / HTTP** access and create a user for the gateway (Digest authentication is used).
- Set the time from **NTP** in the restaurant's time zone. The web app warns when a terminal's clock drifts more than
  two minutes.
- For check-in / break / check-out, enable the **attendance mode** that asks staff for their status (or schedules it by
  time). Without it, scans from a "device reports" terminal wait in the review queue. They are never guessed.
- Give the gateway computer a fixed IP and allow it to reach the terminals (ports 80/443).

Attendance scans are access-control events with major type 5 and an "authentication passed" minor type. The defaults
are 1 (card), 38 (fingerprint) and 75 (face). If your terminal model reports other codes for successful
authentication, add them under `gateway.accepted-minor-codes`.

## 4. Run

```bash
java -jar attendance-gateway.jar
```

- Local status: <http://127.0.0.1:8091/local/status> (pending scans, last send, each device's state).
- Data and logs: `./gateway-data/` (`gateway.db`, `gateway.log`). Back this folder up; do not delete it while scans are
  pending.

Run it as a service so it starts with the computer, for example with
[WinSW](https://github.com/winsw/winsw) on Windows or a systemd unit on Linux:

```ini
# /etc/systemd/system/attendance-gateway.service
[Unit]
Description=Restaurant attendance gateway
After=network-online.target

[Service]
WorkingDirectory=/opt/attendance-gateway
Environment=GATEWAY_MASTER_KEY=...
ExecStart=/usr/bin/java -jar /opt/attendance-gateway/attendance-gateway.jar
Restart=always

[Install]
WantedBy=multi-user.target
```

## Testing without a terminal

Start with the simulator on (never in production), then send scans as if a terminal had:

```bash
GATEWAY_SIMULATOR=true java -jar attendance-gateway.jar
curl -X POST http://127.0.0.1:8091/local/simulate -H "Content-Type: application/json" \
  -d '{"deviceSerialNumber":"DS-K1T341AMF-OSU01","employeeDeviceId":"1005","eventType":"checkIn"}'
```

`eventType` is what the terminal would report: `checkIn`, `breakOut`, `breakIn`, `checkOut`, or leave it out to see the
review queue.

The local development seed (`scripts/dev/seed_local.py` or `scripts/dev/seed_devices.py`) registers `GATEWAY-001` and
writes its key to `backend/target/dev-gateway.env`:

```bash
set -a; . backend/target/dev-gateway.env; set +a
java -jar attendance-gateway/target/attendance-gateway-1.0.0.jar
```

## What happens to a scan

| Situation | Result in the web app (HR → Attendance Devices → Review) |
|---|---|
| Terminal reports check-in / break / check-out, person linked | Recorded as that clock action at the terminal's time |
| Same action again within 5 minutes | Ignored as a double scan |
| Terminal does not say, and has no fixed direction | **Needs a decision**: a manager chooses the action |
| Device user number not linked to anyone | **Unknown person**: link them, then *Try again* |
| Attendance rules refuse it (e.g. a second clock-in) | **Refused by the rules**, with the reason. Never turned into a clock-out |
| Terminal clock ahead of the server | **Needs a decision** (fix the terminal's time) |
| Sent twice (retry after a lost answer) | Answered as a duplicate; nothing recorded twice |

Accepted scans become ordinary attendance records (source `DEVICE`), so late arrivals, early departures, breaks,
missed clock-outs, timesheets, overtime and payroll work as they do for POS clock-ins.

## Build and test

```bash
mvn test              # parser, ISAPI client against a fake Digest-protected device, offline queue and sync
mvn -DskipTests package
```

## Other makes

The gateway talks to devices through `DeviceConnector`. Hikvision is `HikvisionIsapiConnector`. Supporting ZKTeco or
another make means adding a connector and choosing it by the device's make in `DeviceManager`. The backend, the queue
and the web app stay as they are.

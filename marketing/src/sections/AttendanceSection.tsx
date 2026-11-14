import { motion } from "framer-motion";
import { Coffee, Fingerprint, LogIn, LogOut, Router, ScanFace, TimerReset, WifiOff } from "lucide-react";
import { BrowserFrame } from "../components/Media";
import { Badge, Reveal, SectionHeader } from "../components/ui";

const events = [
  { icon: LogIn, label: "Clock in", time: "09:00", tone: "bg-leaf-soft text-leaf" },
  { icon: Coffee, label: "Break out", time: "13:00", tone: "bg-gold/15 text-[#8a6a2a]" },
  { icon: TimerReset, label: "Break in", time: "13:30", tone: "bg-gold/15 text-[#8a6a2a]" },
  { icon: LogOut, label: "Clock out", time: "17:00", tone: "bg-terra-soft text-terra" },
];

export default function AttendanceSection() {
  return (
    <section id="attendance" aria-labelledby="attendance-title" className="py-24 sm:py-28">
      <div className="container-x">
        <div className="grid items-end gap-8 lg:grid-cols-2">
          <SectionHeader
            id="attendance-title"
            eyebrow="Employee attendance"
            title={<>Know When Your <span className="text-terra">Team Works.</span></>}
            lead="Clock-ins come from the POS staff clock or from biometric terminals, and every record feeds straight into timesheets and payroll. Late arrivals, absences and missed clock-outs are flagged as they happen, not discovered at month end."
          />
          <Reveal delay={0.1} className="lg:pb-2">
            <ol className="grid grid-cols-2 gap-3 sm:grid-cols-4" aria-label="A working day">
              {events.map((e, i) => (
                <motion.li
                  key={e.label}
                  initial={{ opacity: 0, y: 12 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true }}
                  transition={{ delay: i * 0.1 }}
                  className="rounded-2xl bg-white p-4 shadow-soft ring-1 ring-ink/6"
                >
                  <span className={`flex size-9 items-center justify-center rounded-xl ${e.tone}`}><e.icon className="size-[18px]" aria-hidden /></span>
                  <p className="mt-3 text-sm font-bold text-ink">{e.label}</p>
                  <p className="font-mono text-xs text-ink-muted">example {e.time}</p>
                </motion.li>
              ))}
            </ol>
          </Reveal>
        </div>

        <Reveal className="mt-14">
          <BrowserFrame name="attendance" caption="The attendance day board: working now, on break, late arrivals, absences and missed clock-outs, with correction actions." sizes="(min-width: 1024px) 72rem, 100vw" url="HR · Attendance" />
        </Reveal>

        <div className="mt-14 grid gap-8 lg:grid-cols-[1.1fr_0.9fr] lg:items-center">
          <Reveal>
            <div className="rounded-[2rem] bg-kenvo-ink p-7 text-white sm:p-9">
              <div className="flex flex-wrap items-center gap-3">
                <Badge tone="kenvo">Hikvision integration</Badge>
                <span className="text-xs font-semibold text-white/60">Face · fingerprint · card terminals</span>
              </div>
              <h3 className="mt-4 font-display text-3xl font-semibold">Biometric terminals, connected through a local gateway.</h3>
              <p className="mt-3 leading-relaxed text-white/75">
                A small attendance gateway runs on the restaurant's own network, reads Hikvision terminals and forwards each scan to the platform.
                Accepted scans become ordinary attendance records, so lateness, breaks, overtime and payroll work the same as POS clock-ins.
              </p>
              <ul className="mt-6 grid gap-4 sm:grid-cols-2">
                {[
                  [WifiOff, "Offline queue", "Scans are kept locally until the platform confirms them, so none are lost when the internet drops."],
                  [ScanFace, "No guessing", "The terminal's own check-in, break and check-out status is used. Unclear scans wait for a person to review."],
                  [Router, "Health monitoring", "Alerts when a device goes offline, the gateway goes quiet or a clock drifts."],
                  [Fingerprint, "Secure by design", "Device logins are encrypted and never leave the restaurant."],
                ].map(([Icon, t, d]) => {
                  const I = Icon as React.ComponentType<{ className?: string }>;
                  return (
                    <li key={t as string} className="flex gap-3">
                      <I className="mt-0.5 size-5 shrink-0 text-kenvo-bright" aria-hidden />
                      <span><span className="block text-sm font-bold">{t as string}</span><span className="block text-sm leading-relaxed text-white/65">{d as string}</span></span>
                    </li>
                  );
                })}
              </ul>
            </div>
          </Reveal>
          <Reveal delay={0.1} className="grid gap-5">
            <BrowserFrame name="devices" caption="Attendance devices: devices and gateways online, scans to review and unknown people." sizes="(min-width: 1024px) 30rem, 100vw" url="Attendance devices" />
            <BrowserFrame name="schedule" caption="The weekly rota and timesheet: worked, scheduled, overtime, late and missed." sizes="(min-width: 1024px) 30rem, 100vw" url="Schedule" />
          </Reveal>
        </div>
      </div>
    </section>
  );
}

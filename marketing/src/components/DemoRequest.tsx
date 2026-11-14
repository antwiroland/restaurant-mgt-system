import { AnimatePresence, motion } from "framer-motion";
import { CircleAlert, Info, Mail, MessageCircle, Send } from "lucide-react";
import { useId, useState } from "react";
import { company, whatsapp } from "../data/site";
import { KenvoMark } from "./Logo";

type Fields = { name: string; business: string; email: string; phone: string; type: string; branches: string; message: string };
const empty: Fields = { name: "", business: "", email: "", phone: "", type: "", branches: "", message: "" };
const types = ["Fine dining", "Fast food", "Café", "Hotel restaurant", "Casual dining", "Restaurant chain", "Other"];
const branchOptions = ["1", "2 – 3", "4 – 10", "More than 10"];

function validate(f: Fields): Partial<Record<keyof Fields, string>> {
  const e: Partial<Record<keyof Fields, string>> = {};
  if (f.name.trim().length < 2) e.name = "Please enter your full name.";
  if (f.business.trim().length < 2) e.business = "Please enter your restaurant or business name.";
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(f.email.trim())) e.email = "Please enter a valid email address.";
  const digits = f.phone.replace(/[^\d]/g, "");
  if (digits.length < 9 || digits.length > 15) e.phone = "Please enter a valid phone number, for example 024 123 4567.";
  if (!f.type) e.type = "Please choose a restaurant type.";
  if (!f.branches) e.branches = "Please choose the number of branches.";
  if (f.message.length > 1500) e.message = "Please keep the message under 1,500 characters.";
  return e;
}

function summary(f: Fields) {
  return [
    "Demo request: Restaurant Management System",
    "",
    `Name: ${f.name}`,
    `Business: ${f.business}`,
    `Email: ${f.email}`,
    `Phone: ${f.phone}`,
    `Restaurant type: ${f.type}`,
    `Branches: ${f.branches}`,
    "",
    f.message ? `Message: ${f.message}` : "",
  ].join("\n");
}

export default function DemoRequest() {
  const [f, setF] = useState<Fields>(empty);
  const [errors, setErrors] = useState<Partial<Record<keyof Fields, string>>>({});
  const [ready, setReady] = useState(false);
  const uid = useId();

  const set = (k: keyof Fields) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
    setF((p) => ({ ...p, [k]: e.target.value }));
    if (errors[k]) setErrors((p) => ({ ...p, [k]: undefined }));
    setReady(false);
  };

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const found = validate(f);
    setErrors(found);
    const first = Object.keys(found)[0];
    if (first) {
      document.getElementById(`${uid}-${first}`)?.focus();
      return;
    }
    setReady(true);
    window.location.href = mailto;
  };

  const text = summary(f);
  const mailto = `mailto:${company.email}?cc=${company.directEmail}&subject=${encodeURIComponent(`Demo request: ${f.business || "Restaurant Management System"}`)}&body=${encodeURIComponent(text)}`;

  const field = (k: keyof Fields, label: string, input: React.ReactNode, hint?: string) => (
    <div className={k === "message" ? "sm:col-span-2" : ""}>
      <label htmlFor={`${uid}-${k}`} className="block text-sm font-semibold text-ink">{label}</label>
      {input}
      {errors[k] ? (
        <p id={`${uid}-${k}-err`} className="mt-1.5 flex items-center gap-1.5 text-sm text-[#b42318]"><CircleAlert className="size-4" aria-hidden />{errors[k]}</p>
      ) : hint ? <p className="mt-1.5 text-xs text-ink-muted">{hint}</p> : null}
    </div>
  );
  const cls = (k: keyof Fields) =>
    `mt-1.5 block w-full rounded-xl border bg-white px-4 py-3 text-[15px] text-ink outline-none transition placeholder:text-ink-muted focus:border-kenvo focus:ring-4 focus:ring-kenvo/15 ${errors[k] ? "border-[#b42318]" : "border-line"}`;
  const aria = (k: keyof Fields) => ({ id: `${uid}-${k}`, "aria-invalid": !!errors[k], "aria-describedby": errors[k] ? `${uid}-${k}-err` : undefined });

  return (
    <section id="demo" aria-labelledby="demo-title" className="py-24 sm:py-28">
      <div className="container-x grid gap-12 lg:grid-cols-[0.8fr_1.2fr]">
        <div>
          <p className="eyebrow">Request a demo</p>
          <h2 id="demo-title" className="mt-3 font-display text-4xl leading-[1.08] font-semibold tracking-tight sm:text-5xl">See it working on your own menu.</h2>
          <p className="mt-5 text-lg leading-relaxed text-ink-soft">
            Tell us about your restaurant and the KENVO team will arrange a guided demonstration of the guest QR journey, the POS, the kitchen display,
            payments, attendance, payroll and the owner's dashboard.
          </p>
          <div className="mt-8 flex items-center gap-3 rounded-2xl bg-kenvo-ink p-4 text-white">
            <KenvoMark className="size-11" />
            <p className="text-sm leading-relaxed text-white/80">
              Requests go to <a className="font-semibold text-white underline decoration-kenvo-bright underline-offset-4" href={`mailto:${company.email}`}>{company.email}</a>,
              the KENVO Integrated Systems business inbox.
            </p>
          </div>
        </div>

        <form noValidate onSubmit={onSubmit} className="rounded-[2rem] bg-white p-6 shadow-lift ring-1 ring-ink/6 sm:p-9" aria-describedby={`${uid}-note`}>
          <div className="grid gap-5 sm:grid-cols-2">
            {field("name", "Full name", <input {...aria("name")} className={cls("name")} value={f.name} onChange={set("name")} autoComplete="name" />)}
            {field("business", "Restaurant / business name", <input {...aria("business")} className={cls("business")} value={f.business} onChange={set("business")} autoComplete="organization" />)}
            {field("email", "Email", <input {...aria("email")} type="email" className={cls("email")} value={f.email} onChange={set("email")} autoComplete="email" />)}
            {field("phone", "Phone number", <input {...aria("phone")} type="tel" className={cls("phone")} value={f.phone} onChange={set("phone")} autoComplete="tel" placeholder="024 123 4567" />)}
            {field("type", "Restaurant type", (
              <select {...aria("type")} className={cls("type")} value={f.type} onChange={set("type")}>
                <option value="">Choose one</option>
                {types.map((t) => <option key={t}>{t}</option>)}
              </select>
            ))}
            {field("branches", "Number of branches", (
              <select {...aria("branches")} className={cls("branches")} value={f.branches} onChange={set("branches")}>
                <option value="">Choose one</option>
                {branchOptions.map((t) => <option key={t}>{t}</option>)}
              </select>
            ))}
            {field("message", "Message (optional)", <textarea {...aria("message")} rows={4} className={cls("message")} value={f.message} onChange={set("message")} placeholder="What would you like to see in the demo?" />)}
          </div>

          <p id={`${uid}-note`} className="mt-6 flex gap-2.5 rounded-xl bg-cream-2/70 p-3.5 text-[13px] leading-relaxed text-ink-soft">
            <Info className="mt-0.5 size-4 shrink-0 text-kenvo" aria-hidden />
            This form doesn't send anything by itself. It opens your email app with your details filled in, addressed to KENVO. You can also send them by WhatsApp.
          </p>

          <div className="mt-6 flex flex-col gap-3 sm:flex-row">
            <motion.button whileHover={{ y: -2 }} whileTap={{ scale: 0.98 }} type="submit" className="inline-flex min-h-12 flex-1 items-center justify-center gap-2 rounded-full bg-terra px-6 font-semibold text-white shadow-[0_10px_30px_-10px_rgb(194_86_43/0.7)] hover:bg-terra-deep">
              <Send className="size-4" aria-hidden /> Request a Demo by email
            </motion.button>
            <a
              href={whatsapp(company.phones[0].wa, text)}
              target="_blank"
              rel="noopener"
              onClick={(e) => {
                const found = validate(f);
                setErrors(found);
                if (Object.keys(found).length) e.preventDefault();
              }}
              className="inline-flex min-h-12 items-center justify-center gap-2 rounded-full bg-[#1f8f4e] px-6 font-semibold text-white hover:bg-[#187a42]"
            >
              <MessageCircle className="size-4" aria-hidden /> Send by WhatsApp
            </a>
          </div>

          <AnimatePresence>
            {ready && (
              <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} role="status" className="mt-5 rounded-xl bg-leaf-soft p-4 text-sm text-ink">
                <p className="font-semibold">Your email app should now be open with the request ready to send.</p>
                <p className="mt-1 text-ink-soft">
                  Nothing happened? <a className="font-semibold text-kenvo underline underline-offset-2" href={mailto}><Mail className="mr-1 inline size-4" aria-hidden />Open the email again</a>, or write to {company.email}.
                </p>
              </motion.div>
            )}
          </AnimatePresence>
        </form>
      </div>
    </section>
  );
}

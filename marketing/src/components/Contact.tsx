import { motion } from "framer-motion";
import { Globe, Mail, MessageCircle, Phone } from "lucide-react";
import { company, whatsapp } from "../data/site";
import { KenvoMark } from "./Logo";
import { Button, Reveal } from "./ui";

export default function Contact() {
  return (
    <section id="contact" aria-labelledby="contact-title" className="bg-kenvo-night py-24 text-white sm:py-28">
      <div className="container-x">
        <Reveal className="flex flex-col items-start gap-6 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <div className="flex items-center gap-3">
              <KenvoMark className="size-11" />
              <p className="font-brand text-xs font-bold tracking-[0.2em] text-kenvo-bright uppercase">KENVO Integrated Systems</p>
            </div>
            <h2 id="contact-title" className="mt-5 font-display text-4xl leading-[1.08] font-semibold sm:text-5xl">Talk to our team.</h2>
            <p className="mt-4 max-w-xl text-lg leading-relaxed text-white/70">
              Questions about the Restaurant Management System, pricing or setup? Call, WhatsApp or email KENVO Integrated Systems directly.
            </p>
          </div>
          <Button href="#demo">Request a Demo</Button>
        </Reveal>

        <div className="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-[1fr_1fr_1.45fr_1fr]">
          {company.phones.map((p, i) => (
            <motion.div
              key={p.intl}
              initial={{ opacity: 0, y: 16 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: i * 0.08 }}
              className="rounded-3xl bg-white/5 p-6 ring-1 ring-white/10"
            >
              <Phone className="size-6 text-kenvo-bright" aria-hidden />
              <p className="mt-4 text-sm text-white/60">Phone {i + 1}</p>
              <a href={`tel:${p.intl}`} className="mt-1 block text-xl font-semibold hover:text-kenvo-bright">{p.display}</a>
              <a
                href={whatsapp(p.wa)}
                target="_blank"
                rel="noopener"
                className="mt-4 inline-flex min-h-10 items-center gap-2 rounded-full bg-[#1f8f4e] px-4 text-sm font-semibold hover:bg-[#187a42]"
                aria-label={`Chat on WhatsApp with ${p.display}`}
              >
                <MessageCircle className="size-4" aria-hidden /> WhatsApp
              </a>
            </motion.div>
          ))}
          <motion.div initial={{ opacity: 0, y: 16 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ delay: 0.16 }} className="rounded-3xl bg-white/5 p-6 ring-1 ring-white/10">
            <Mail className="size-6 text-kenvo-bright" aria-hidden />
            <p className="mt-4 text-sm text-white/60">Business email</p>
            <a href={`mailto:${company.email}`} className="mt-1 block text-[15px] font-semibold [overflow-wrap:anywhere] hover:text-kenvo-bright">{company.email}</a>
            <p className="mt-4 text-sm text-white/60">Direct contact</p>
            <a href={`mailto:${company.directEmail}`} className="mt-1 block text-sm font-semibold break-all hover:text-kenvo-bright">{company.directEmail}</a>
          </motion.div>
          <motion.div initial={{ opacity: 0, y: 16 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ delay: 0.24 }} className="rounded-3xl bg-kenvo p-6">
            <Globe className="size-6 text-white" aria-hidden />
            <p className="mt-4 text-sm text-white/80">Company website</p>
            <a href={company.website} target="_blank" rel="noopener" className="mt-1 block font-semibold break-all underline-offset-4 hover:underline">kenvointegratedsystems.com</a>
            <p className="mt-4 text-sm leading-relaxed text-white/80">Electrical, networking, CCTV and software solutions.</p>
          </motion.div>
        </div>
      </div>
    </section>
  );
}

import { motion } from "framer-motion";
import { BellRing, ChefHat, CircleCheck, QrCode } from "lucide-react";
import { product } from "../data/site";
import { BrowserFrame, Photo, PhoneFrame } from "./Media";
import { Button } from "./ui";

const ease = [0.22, 1, 0.36, 1] as const;

function FloatingTag({ icon: Icon, title, sub, className, delay, tone = "leaf" }: {
  icon: React.ComponentType<{ className?: string }>; title: string; sub: string; className: string; delay: number; tone?: "leaf" | "terra" | "kenvo";
}) {
  const t = { leaf: "bg-leaf-soft text-leaf", terra: "bg-terra-soft text-terra", kenvo: "bg-kenvo/10 text-kenvo" }[tone];
  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.9, y: 10 }}
      animate={{ opacity: 1, scale: 1, y: 0 }}
      transition={{ delay, duration: 0.6, ease }}
      className={`absolute z-20 ${className}`}
    >
      <motion.div
        animate={{ y: [0, -6, 0] }}
        transition={{ duration: 5, repeat: Infinity, ease: "easeInOut", delay }}
        className="flex items-center gap-3 rounded-2xl bg-white/95 py-2.5 pr-4 pl-2.5 shadow-lift ring-1 ring-ink/5 backdrop-blur"
      >
        <span className={`flex size-9 items-center justify-center rounded-xl ${t}`}><Icon className="size-[18px]" aria-hidden /></span>
        <span className="leading-tight">
          <span className="block text-[13px] font-bold text-ink">{title}</span>
          <span className="block text-[11.5px] text-ink-muted">{sub}</span>
        </span>
      </motion.div>
    </motion.div>
  );
}

export default function Hero() {
  return (
    <section id="home" aria-labelledby="hero-title" className="relative overflow-hidden pt-28 pb-20 sm:pt-32 lg:pb-28">
      {/* warm backdrop */}
      <div aria-hidden className="grain absolute inset-0 -z-10" />
      <div aria-hidden className="absolute -top-40 -right-40 -z-10 size-[42rem] rounded-full bg-[radial-gradient(circle,rgb(194_86_43/0.16),transparent_65%)]" />
      <div aria-hidden className="absolute top-1/2 -left-48 -z-10 size-[36rem] rounded-full bg-[radial-gradient(circle,rgb(47_122_79/0.12),transparent_65%)]" />

      <div className="container-x grid items-center gap-14 lg:grid-cols-[1.02fr_1fr] lg:gap-10">
        <div>
          <motion.a
            href="https://kenvointegratedsystems.com"
            target="_blank"
            rel="noopener"
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, ease }}
            className="inline-flex items-center gap-2 rounded-full bg-kenvo-ink py-1.5 pr-4 pl-1.5 text-xs font-semibold text-white/90 ring-1 ring-kenvo/40 transition hover:ring-kenvo"
          >
            <span className="rounded-full bg-kenvo px-2.5 py-0.5 font-brand tracking-wider">KENVO</span>
            {product.headline.replace(".", "")}
          </motion.a>

          <motion.h1
            id="hero-title"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.08, ease }}
            className="mt-6 font-display text-[2.9rem] leading-[1.02] font-semibold tracking-tight text-ink sm:text-6xl lg:text-[4.4rem]"
          >
            Your Restaurant.
            <br />
            <span className="text-terra italic">Smarter.</span> Faster.
            <br />
            Better.
          </motion.h1>

          <motion.p
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.16, ease }}
            className="mt-6 max-w-xl text-lg leading-relaxed text-ink-soft sm:text-xl"
          >
            Everything your restaurant needs to serve customers better, manage operations effortlessly, and grow with confidence.
          </motion.p>

          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.24, ease }}
            className="mt-9 flex flex-col gap-3 sm:flex-row"
          >
            <Button href="#demo">Request a Demo</Button>
            <Button href="#features" variant="secondary">Explore Features</Button>
          </motion.div>

          <motion.ul
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.8, delay: 0.4 }}
            className="mt-10 flex flex-wrap gap-x-6 gap-y-3 text-sm font-semibold text-ink-soft"
          >
            {["Table QR ordering", "POS & kitchen display", "Mobile Money, card & cash", "Attendance & payroll"].map((t) => (
              <li key={t} className="flex items-center gap-2"><CircleCheck className="size-4 text-leaf" aria-hidden />{t}</li>
            ))}
          </motion.ul>
        </div>

        {/* composition: food photo + real screens */}
        <div className="relative mx-auto w-full max-w-[36rem] lg:max-w-none">
          <motion.div
            initial={{ opacity: 0, scale: 0.94 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 1, ease }}
            className="relative ml-auto aspect-[4/5] w-[78%] overflow-hidden rounded-[2.5rem] shadow-lift sm:w-[72%]"
          >
            <Photo name="rice-pan" eager sizes="(min-width: 1024px) 34vw, 72vw" className="size-full scale-110" />
            <div aria-hidden className="absolute inset-0 bg-gradient-to-t from-ink/35 via-transparent to-transparent" />
          </motion.div>

          <motion.div
            initial={{ opacity: 0, x: -30, y: 20 }}
            animate={{ opacity: 1, x: 0, y: 0 }}
            transition={{ duration: 0.9, delay: 0.3, ease }}
            className="absolute bottom-[8%] left-0 z-10 w-[78%] sm:w-[74%]"
          >
            <BrowserFrame name="dashboard" eager sizes="(min-width: 1024px) 30vw, 75vw" url="Owner dashboard" />
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 40 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.9, delay: 0.45, ease }}
            className="absolute -bottom-6 right-0 z-10 hidden sm:block"
          >
            <PhoneFrame name="qr-tracker" width="w-36 lg:w-40" eager />
          </motion.div>

          <FloatingTag icon={QrCode} title="Order sent to the kitchen" sub="Table 8 · from the table QR" className="top-[6%] left-0 sm:left-[2%]" delay={0.7} />
          <FloatingTag icon={BellRing} title="Table 8 wants a waiter" sub="On my way · Done" className="top-[38%] -right-1 hidden sm:block lg:-right-6" delay={0.9} tone="terra" />
          <FloatingTag icon={ChefHat} title="Kitchen display" sub="Accepted → Cooking → Ready" className="bottom-[-2.5rem] left-[6%]" delay={1.1} tone="kenvo" />
        </div>
      </div>
    </section>
  );
}

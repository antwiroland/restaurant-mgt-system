import { Photo } from "./Media";
import { Button, Reveal } from "./ui";
import { product } from "../data/site";

export default function CTASection() {
  return (
    <section aria-labelledby="cta-title" className="px-3 sm:px-5">
      <div className="relative isolate mx-auto max-w-[78rem] overflow-hidden rounded-[2.5rem] px-6 py-20 text-center sm:px-12 sm:py-28">
        <Photo name="chef-flame" sizes="100vw" className="absolute inset-0 -z-20 size-full" alt="" />
        <div aria-hidden className="absolute inset-0 -z-10 bg-gradient-to-br from-ink/95 via-ink/80 to-terra-deep/70" />
        <Reveal>
          <p className="eyebrow text-gold">{product.tagline}</p>
          <h2 id="cta-title" className="mx-auto mt-4 max-w-3xl font-display text-4xl leading-[1.08] font-semibold text-balance text-white sm:text-6xl">
            Ready to Take Your Restaurant to the Next Level?
          </h2>
          <p className="mx-auto mt-6 max-w-2xl text-lg leading-relaxed text-white/80">
            Discover how smarter restaurant management can transform the way you serve customers and run your business.
          </p>
          <div className="mt-10 flex flex-col justify-center gap-3 sm:flex-row">
            <Button href="#demo">Request a Demo</Button>
            <Button href="#contact" variant="ghost-dark">Contact Our Team</Button>
          </div>
          <p className="mt-10 text-sm text-white/60">{product.closing}</p>
        </Reveal>
      </div>
    </section>
  );
}

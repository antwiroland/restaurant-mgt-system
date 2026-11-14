import { KenvoMark } from "../components/Logo";
import { Photo } from "../components/Media";
import { Reveal } from "../components/ui";
import { company, product } from "../data/site";

export default function AboutProduct() {
  return (
    <section aria-labelledby="about-title" className="py-24 sm:py-28">
      <div className="container-x grid items-center gap-12 lg:grid-cols-2 lg:gap-16">
        <Reveal className="relative">
          <div className="grid grid-cols-5 gap-4">
            <div className="col-span-3 aspect-[3/4] overflow-hidden rounded-[2rem] shadow-soft">
              <Photo name="chef-plating" sizes="(min-width: 1024px) 28vw, 60vw" className="size-full" />
            </div>
            <div className="col-span-2 flex flex-col gap-4 pt-10">
              <div className="aspect-square overflow-hidden rounded-[1.6rem] shadow-soft">
                <Photo name="served-plate" sizes="(min-width: 1024px) 18vw, 40vw" className="size-full" />
              </div>
              <div className="aspect-[4/5] overflow-hidden rounded-[1.6rem] shadow-soft">
                <Photo name="cocktails" sizes="(min-width: 1024px) 18vw, 40vw" className="size-full" />
              </div>
            </div>
          </div>
          <div className="absolute -bottom-6 left-6 flex items-center gap-3 rounded-2xl bg-kenvo-ink py-3 pr-5 pl-3 text-white shadow-lift ring-1 ring-kenvo/40">
            <KenvoMark className="size-10" />
            <span className="leading-tight">
              <span className="block font-brand text-[11px] font-bold tracking-[0.16em] text-kenvo-bright uppercase">Developed by</span>
              <span className="block font-brand text-[15px] font-semibold">KENVO Integrated Systems</span>
            </span>
          </div>
        </Reveal>

        <Reveal delay={0.1}>
          <p className="eyebrow">About the solution</p>
          <h2 id="about-title" className="mt-3 font-display text-4xl leading-[1.08] font-semibold tracking-tight text-balance sm:text-5xl">
            {product.headline.replace("Powered by KENVO.", "")}
            <span className="text-kenvo">Powered by KENVO.</span>
          </h2>
          <p className="mt-6 text-lg leading-relaxed text-ink-soft">{product.statement}</p>
          <p className="mt-4 text-[17px] leading-relaxed text-ink-soft">
            KENVO Integrated Systems works across technology solutions, security systems, networking, electrical solutions and software development.
            The Restaurant Management System is part of KENVO's software and digital solutions portfolio.
          </p>

          <ul className="mt-8 flex flex-wrap gap-2" aria-label="What KENVO stands for">
            {company.acronym.map(([letter, word]) => (
              <li key={letter} className="flex items-center gap-2 rounded-full bg-white py-1.5 pr-4 pl-1.5 text-sm font-semibold text-ink shadow-soft ring-1 ring-ink/6">
                <span className="flex size-7 items-center justify-center rounded-full bg-kenvo-ink font-brand text-xs font-bold text-kenvo-bright">{letter}</span>
                {word}
              </li>
            ))}
          </ul>

          <p className="mt-8 font-brand text-sm font-bold tracking-[0.22em] text-terra">{product.tagline}</p>
        </Reveal>
      </div>
    </section>
  );
}

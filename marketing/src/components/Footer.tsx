import { company, nav, product, whatsapp } from "../data/site";
import { Logo } from "./Logo";

export default function Footer() {
  const year = new Date().getFullYear();
  return (
    <footer className="bg-kenvo-night pb-10 text-white/70">
      <div className="container-x border-t border-white/10 pt-14">
        <div className="grid gap-10 md:grid-cols-[1.4fr_1fr_1fr]">
          <div>
            <Logo dark />
            <p className="mt-5 max-w-sm text-sm leading-relaxed">{product.statement}</p>
            <p className="mt-5 font-brand text-xs font-bold tracking-[0.22em] text-kenvo-bright">{company.corporateTagline}</p>
          </div>
          <nav aria-label="Footer">
            <p className="text-sm font-semibold text-white">Explore</p>
            <ul className="mt-4 grid gap-2.5 text-sm">
              {nav.map((n) => <li key={n.href}><a href={n.href} className="hover:text-white">{n.label}</a></li>)}
              <li><a href="#demo" className="hover:text-white">Request a Demo</a></li>
            </ul>
          </nav>
          <div>
            <p className="text-sm font-semibold text-white">Contact KENVO</p>
            <ul className="mt-4 grid gap-2.5 text-sm">
              <li><a href={`mailto:${company.email}`} className="break-all hover:text-white">{company.email}</a></li>
              <li><a href={`mailto:${company.directEmail}`} className="break-all hover:text-white">{company.directEmail}</a></li>
              {company.phones.map((p) => (
                <li key={p.intl} className="flex flex-wrap gap-x-3">
                  <a href={`tel:${p.intl}`} className="hover:text-white">{p.display}</a>
                  <a href={whatsapp(p.wa)} target="_blank" rel="noopener" className="text-[#5fd38a] hover:text-white">WhatsApp</a>
                </li>
              ))}
              <li><a href={company.website} target="_blank" rel="noopener" className="hover:text-white">kenvointegratedsystems.com</a></li>
            </ul>
          </div>
        </div>

        <div className="mt-12 flex flex-col gap-3 border-t border-white/10 pt-6 text-xs sm:flex-row sm:items-center sm:justify-between">
          <p>
            <span className="text-white">Restaurant Management System</span> — A Software Solution by{" "}
            <a href={company.website} target="_blank" rel="noopener" className="font-semibold text-white underline decoration-kenvo underline-offset-4">KENVO Integrated Systems</a>.
          </p>
          <p>© {year} KENVO Integrated Systems. Food photography from Unsplash.</p>
        </div>
        <div className="mt-4 flex flex-col gap-1 text-xs text-white/50 sm:flex-row sm:justify-between">
          <p>Restaurant Management System — Developed by KENVO Integrated Systems.</p>
          <p>{product.closing}</p>
        </div>
      </div>
    </footer>
  );
}

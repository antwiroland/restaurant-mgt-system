"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { Icon } from "@/components/Icon";
import { BrandMark } from "@/components/NavSidebar";
import { useBrand } from "@/components/BrandProvider";
import { useStaffSession } from "@/components/SessionProvider";
import { cartCount, useCart } from "@/lib/cart";

const links = [
  { href: "/", label: "Home" },
  { href: "/explore", label: "Order online" },
  { href: "/reserve", label: "Reservations" },
  { href: "/#about", label: "About" },
  { href: "/#visit", label: "Visit" },
];

export function SiteHeader() {
  const brand = useBrand();
  const { session } = useStaffSession();
  const count = cartCount(useCart());
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  const [lastPath, setLastPath] = useState(pathname);

  if (lastPath !== pathname) {
    setLastPath(pathname);
    setOpen(false);
  }

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    const frame = window.requestAnimationFrame(onScroll);
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      window.cancelAnimationFrame(frame);
      window.removeEventListener("scroll", onScroll);
    };
  }, []);

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => event.key === "Escape" && setOpen(false);
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open]);

  const isActive = (href: string) => (href === "/" ? pathname === "/" : !href.includes("#") && pathname.startsWith(href));

  return (
    <header
      className={`sticky top-0 z-[var(--z-sticky)] transition-[background-color,box-shadow,border-color] duration-300 ${
        scrolled || open ? "border-b border-line bg-ivory/90 backdrop-blur-md" : "border-b border-transparent bg-ivory"
      }`}
    >
      <div className="site flex h-[72px] items-center justify-between gap-4">
        <Link href="/" className="flex items-center gap-3" aria-label={`${brand.name} home`}>
          <BrandMark size={38} />
          <span className="grid leading-none">
            <span className="font-serif text-xl font-semibold tracking-tight text-ink">{brand.name}</span>
            <span className="mt-1 hidden text-[10.5px] font-bold uppercase tracking-[0.18em] text-ink-muted sm:block">{brand.tagline}</span>
          </span>
        </Link>

        <nav aria-label="Main" className="hidden items-center gap-1 md:flex">
          {links.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              aria-current={isActive(link.href) ? "page" : undefined}
              className={`rounded-lg px-3.5 py-2 text-[15px] font-semibold transition-colors ${
                isActive(link.href) ? "text-brand" : "text-ink-soft hover:text-ink"
              }`}
            >
              {link.label}
            </Link>
          ))}
        </nav>

        <div className="flex items-center gap-1.5">
          <Link href="/account" className="btn btn-ghost btn-icon" aria-label={session?.user.role === "CUSTOMER" ? `Account: ${session.user.name}` : "Sign in"}>
            <Icon name="user" />
          </Link>
          <Link href="/cart" className="btn btn-ghost btn-icon relative" aria-label={count > 0 ? `Cart, ${count} item(s)` : "Cart"}>
            <Icon name="bag" />
            {count > 0 ? <span className="absolute -right-0.5 -top-0.5 grid h-5 min-w-5 place-items-center rounded-full bg-brand px-1 text-[11px] font-bold text-white" suppressHydrationWarning>{count}</span> : null}
          </Link>
          <Link href="/explore#search" className="btn btn-ghost btn-icon" aria-label="Search the menu">
            <Icon name="search" />
          </Link>
          <Link href="/reserve" className="btn btn-primary hidden sm:inline-flex">
            Book a table
          </Link>
          <button
            type="button"
            className="btn btn-ghost btn-icon md:hidden"
            aria-label={open ? "Close menu" : "Open menu"}
            aria-expanded={open}
            aria-controls="site-mobile-nav"
            onClick={() => setOpen((value) => !value)}
          >
            <Icon name={open ? "close" : "menu"} />
          </button>
        </div>
      </div>

      {open ? (
        <nav id="site-mobile-nav" aria-label="Main" className="site grid gap-1 border-t border-line pb-5 pt-3 md:hidden">
          {links.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className="flex min-h-[48px] items-center justify-between rounded-lg px-2 text-lg font-semibold text-ink"
            >
              {link.label}
              <Icon name="chevronRight" size={18} className="text-ink-muted" />
            </Link>
          ))}
          <Link href="/reserve" className="btn btn-primary btn-lg btn-block mt-3">Book a table</Link>
        </nav>
      ) : null}
    </header>
  );
}

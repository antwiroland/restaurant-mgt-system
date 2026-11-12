"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AccountMenu } from "@/components/AccountMenu";
import { Icon } from "@/components/Icon";
import { BrandMark, NavSidebar } from "@/components/NavSidebar";
import { useStaffSession } from "@/components/SessionProvider";
import { Skeleton } from "@/components/Skeleton";
import { ServiceRequestsBell } from "@/components/ServiceRequestsBell";
import { StaffAlerts } from "@/components/StaffAlerts";
import { routeByRole } from "@/features/auth/auth";
import { useBrand } from "@/components/BrandProvider";
import { HrAccessProvider } from "@/features/hr/HrAccessContext";
import { bottomNavForRole, canAccessPath, isActivePath, navItemForPath } from "@/lib/navigation";

const COLLAPSE_KEY = "staff-nav-collapsed";

function readCollapsed(): boolean {
  try {
    return window.localStorage.getItem(COLLAPSE_KEY) === "1";
  } catch {
    return false;
  }
}

function todayLabel() {
  return new Date().toLocaleDateString(undefined, { weekday: "long", day: "numeric", month: "long" });
}

function CenteredCard({ children }: { children: React.ReactNode }) {
  return (
    <main className="grid min-h-dvh place-items-center bg-canvas px-4 py-10">
      <section className="panel w-full max-w-md p-8 text-center shadow-card">{children}</section>
    </main>
  );
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const brand = useBrand();
  const pathname = usePathname();
  const router = useRouter();
  const { session, loading, logout } = useStaffSession();
  const signOut = () => {
    void logout().finally(() => router.replace("/login"));
  };
  // Safe as lazy state: the first (server + hydration) render is the loading
  // skeleton, which does not depend on this preference.
  const [collapsed, setCollapsed] = useState(() => (typeof window === "undefined" ? false : readCollapsed()));
  const [mobileOpen, setMobileOpen] = useState(false);
  const [lastPath, setLastPath] = useState(pathname);
  const date = todayLabel();

  // Close the drawer on navigation (render-time reset, no effect needed).
  if (lastPath !== pathname) {
    setLastPath(pathname);
    setMobileOpen(false);
  }

  useEffect(() => {
    if (!mobileOpen) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") setMobileOpen(false);
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [mobileOpen]);

  function toggleCollapsed() {
    setCollapsed((value) => {
      const next = !value;
      try {
        window.localStorage.setItem(COLLAPSE_KEY, next ? "1" : "0");
      } catch {
        /* storage unavailable — preference just won't persist */
      }
      return next;
    });
  }

  const role = session?.user.role;
  const current = navItemForPath(pathname);
  const immersive = pathname.startsWith("/kds");
  const dense = pathname.startsWith("/pos") || immersive;

  /* ---------------- Gates (UX only; the API enforces access) ---------------- */
  if (loading) {
    return (
      <div className="grid min-h-dvh bg-canvas lg:grid-cols-[var(--layout-sidebar)_1fr]">
        <div className="hidden bg-espresso lg:block" />
        <div className="grid content-start gap-4 p-6">
          <Skeleton className="h-10 w-64" />
          <div className="grid gap-4 sm:grid-cols-3">
            <Skeleton className="h-28" />
            <Skeleton className="h-28" />
            <Skeleton className="h-28" />
          </div>
          <Skeleton className="h-72" />
        </div>
      </div>
    );
  }

  if (!session) {
    return (
      <CenteredCard>
        <div className="mx-auto mb-5 w-fit"><BrandMark size={48} /></div>
        <p className="kicker">{brand.name} Operations</p>
        <h1 className="heading-lg mt-2">Sign in to continue</h1>
        <p className="mt-2 text-sm text-ink-soft">This area is for restaurant staff. Use your staff phone number and password.</p>
        <Link href={`/login?next=${encodeURIComponent(pathname)}`} className="btn btn-primary btn-md btn-block mt-6">
          Go to staff sign-in <Icon name="arrowRight" size={18} />
        </Link>
        <Link href="/" className="btn btn-ghost btn-sm mt-2">Back to the restaurant site</Link>
      </CenteredCard>
    );
  }

  if (session.user.role === "CUSTOMER") {
    return (
      <CenteredCard>
        <h1 className="heading-lg">Staff area</h1>
        <p className="mt-2 text-sm text-ink-soft">Customer accounts can&apos;t open the staff console.</p>
        <div className="mt-6 grid gap-2">
          <Link href="/" className="btn btn-primary btn-md btn-block">Browse the menu</Link>
          <button type="button" className="btn btn-secondary btn-md btn-block" onClick={() => void logout()}>Sign out</button>
        </div>
      </CenteredCard>
    );
  }

  const allowed = canAccessPath(role, pathname);
  const bottomItems = bottomNavForRole(role);
  const homeHref = role ? routeByRole(role as Parameters<typeof routeByRole>[0]) : "/login";

  return (
    <HrAccessProvider>
    <div className={`min-h-dvh ${immersive ? "bg-night" : "bg-canvas"} text-ink`}>
      {/* Desktop sidebar */}
      <aside
        className={`fixed inset-y-0 left-0 z-[var(--z-sticky)] hidden transition-[width] duration-200 lg:block ${collapsed ? "w-[var(--layout-sidebar-rail)]" : "w-[var(--layout-sidebar)]"}`}
      >
        <NavSidebar
          role={role}
          userName={session.user.name}
          collapsed={collapsed}
          onSignOut={signOut}
        />
      </aside>

      <div className={`min-w-0 transition-[padding] duration-200 ${collapsed ? "lg:pl-[var(--layout-sidebar-rail)]" : "lg:pl-[var(--layout-sidebar)]"}`}>
        <header
          className={[
            "sticky top-0 z-[calc(var(--z-sticky)-1)] border-b backdrop-blur-md",
            immersive ? "border-night-line bg-night/90 text-night-ink" : "border-line bg-canvas/85",
          ].join(" ")}
        >
          <div className="flex min-h-[64px] items-center justify-between gap-3 px-4 md:px-6">
            <div className="flex min-w-0 items-center gap-2">
              <button
                type="button"
                className={`btn btn-icon btn-sm lg:hidden ${immersive ? "btn-ghost text-night-ink hover:bg-night-card" : "btn-ghost"}`}
                onClick={() => setMobileOpen(true)}
                aria-label="Open navigation"
              >
                <Icon name="menu" />
              </button>
              <button
                type="button"
                className={`btn btn-icon btn-sm hidden lg:inline-flex ${immersive ? "btn-ghost text-night-soft hover:bg-night-card hover:text-night-ink" : "btn-ghost"}`}
                onClick={toggleCollapsed}
                aria-label={collapsed ? "Expand navigation" : "Collapse navigation"}
                aria-pressed={collapsed}
              >
                <Icon name="panel" />
              </button>
              <div className="min-w-0 pl-1">
                <p className={`truncate font-display text-[17px] font-bold leading-tight ${immersive ? "text-night-ink" : "text-ink"}`}>
                  {current?.label ?? "Staff"}
                </p>
                <p className={`truncate text-xs ${immersive ? "text-night-soft" : "text-ink-muted"}`} suppressHydrationWarning>
                  {date}
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2">
              {role !== "RIDER" && role !== "HR" && !pathname.startsWith("/pos") ? (
                <Link href="/pos" className="btn btn-primary btn-sm hidden sm:inline-flex">
                  <Icon name="plus" size={16} /> New order
                </Link>
              ) : null}
              {role === "ADMIN" || role === "MANAGER" || role === "CASHIER" ? <ServiceRequestsBell dark={immersive} /> : null}
              <AccountMenu name={session.user.name} role={role} onSignOut={signOut} dark={immersive} />
            </div>
          </div>
        </header>

        <div
          className={[
            immersive ? "px-3 py-4 md:px-5" : dense ? "px-3 py-4 md:px-5 md:py-5" : "px-4 py-6 md:px-6 lg:px-8 lg:py-8",
            bottomItems.length > 1 ? "pb-28 lg:pb-8" : "",
          ].join(" ")}
        >
          {allowed ? (
            children
          ) : (
            <section className="panel mx-auto mt-10 max-w-lg p-8 text-center">
              <span className="mx-auto grid h-12 w-12 place-items-center rounded-full bg-muted text-ink-soft"><Icon name="shield" /></span>
              <h1 className="heading-md mt-4">Not available for your role</h1>
              <p className="mt-2 text-sm text-ink-soft">
                {current?.label ?? "This page"} is limited to {current?.roles.map((r) => r.toLowerCase()).join(", ")} accounts.
              </p>
              <Link href={homeHref} className="btn btn-primary btn-md mt-6">Go to my workspace</Link>
            </section>
          )}
        </div>
      </div>

      {/* Live manager alerts (low stock, auto-86, low ratings) */}
      <StaffAlerts role={role} />

      {/* Mobile / tablet drawer */}
      {mobileOpen ? (
        <div className="fixed inset-0 z-[var(--z-drawer)] lg:hidden" role="dialog" aria-modal="true" aria-label="Navigation">
          <button type="button" aria-label="Close navigation" className="absolute inset-0 bg-overlay backdrop-blur-[2px]" onClick={() => setMobileOpen(false)} />
          <aside className="absolute inset-y-0 left-0 w-[84vw] max-w-[300px] animate-[fadeUp_240ms_var(--ease-default)] shadow-xl">
            <NavSidebar
              role={role}
              userName={session.user.name}
              mobile
              onNavigate={() => setMobileOpen(false)}
              onSignOut={signOut}
            />
          </aside>
        </div>
      ) : null}

      {/* Phone bottom bar */}
      {bottomItems.length > 1 ? (
        <nav
          aria-label="Quick navigation"
          className="fixed inset-x-0 bottom-0 z-[var(--z-sticky)] border-t border-line bg-surface/95 px-2 pb-[max(env(safe-area-inset-bottom),8px)] pt-2 backdrop-blur-md lg:hidden"
          style={{ display: "grid", gridTemplateColumns: `repeat(${bottomItems.length}, minmax(0, 1fr))` }}
        >
          {bottomItems.map((item) => {
            const active = current?.href === item.href || (!current && isActivePath(pathname, item.href));
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={`flex min-h-[52px] flex-col items-center justify-center gap-1 rounded-lg text-[11px] font-semibold transition-colors ${active ? "text-brand" : "text-ink-muted hover:text-ink"}`}
              >
                <Icon name={item.icon} size={22} strokeWidth={active ? 2 : 1.75} />
                <span className="max-w-full truncate">{item.label.replace("Point of Sale", "POS").replace("Kitchen Display", "Kitchen").replace("Floor & Tables", "Tables")}</span>
              </Link>
            );
          })}
        </nav>
      ) : null}
    </div>
    </HrAccessProvider>
  );
}

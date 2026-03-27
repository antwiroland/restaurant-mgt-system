"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useMemo, useState } from "react";
import { NavSidebar } from "@/components/NavSidebar";
import { hasStaffRole, useStaffSession } from "@/components/SessionProvider";

const bottomNavItems = [
  { label: "Dashboard", href: "/dashboard" },
  { label: "POS", href: "/pos" },
  { label: "Orders", href: "/orders" },
  { label: "KDS", href: "/kds" },
  { label: "Tables", href: "/tables" },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { session, logout } = useStaffSession();
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  const role = session?.user.role;
  const canViewAdmin = hasStaffRole(role, ["ADMIN", "MANAGER"]);

  const pageTitle = useMemo(() => {
    const section = pathname.split("/").filter(Boolean)[0] ?? "dashboard";
    return section
      .split("-")
      .map((token) => token.charAt(0).toUpperCase() + token.slice(1))
      .join(" ");
  }, [pathname]);

  return (
    <div className="min-h-dvh bg-canvas text-ink">
      <div className="lg:grid lg:grid-cols-[var(--layout-sidebar)_1fr]">
        <aside
          className={[
            "hidden border-r border-line bg-surface lg:block",
            collapsed ? "lg:w-16" : "lg:w-[var(--layout-sidebar)]",
          ].join(" ")}
        >
          <NavSidebar role={role} collapsed={collapsed} />
        </aside>

        <div className="min-w-0">
          <header className="sticky top-0 z-[var(--z-sticky)] border-b border-line bg-surface/95 px-3 py-3 backdrop-blur md:px-4 lg:px-6">
            <div className="flex min-h-[var(--touch-md)] items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <button
                  className="btn btn-secondary btn-sm lg:hidden"
                  onClick={() => setMobileOpen(true)}
                  aria-label="Open navigation"
                >
                  Menu
                </button>
                <button
                  className="btn btn-ghost btn-sm hidden lg:inline-flex"
                  onClick={() => setCollapsed((value) => !value)}
                  aria-label={collapsed ? "Expand navigation" : "Collapse navigation"}
                >
                  {collapsed ? "Expand" : "Collapse"}
                </button>
                <div>
                  <p className="kicker">Staff</p>
                  <p className="text-base font-semibold leading-none text-ink">{pageTitle}</p>
                </div>
              </div>

              <div className="flex items-center gap-2">
                {session ? <span className="hidden text-sm text-ink-soft md:inline">{session.user.name}</span> : null}
                {canViewAdmin ? (
                  <Link href="/dashboard" className="btn btn-secondary btn-sm hidden sm:inline-flex">
                    Dashboard
                  </Link>
                ) : null}
                <button className="btn btn-secondary btn-sm" onClick={() => void logout()}>
                  Sign Out
                </button>
              </div>
            </div>
          </header>

          <div className="px-3 py-4 pb-24 md:px-4 md:py-6 lg:px-6 lg:py-8 lg:pb-8">{children}</div>
        </div>
      </div>

      {mobileOpen ? (
        <div className="fixed inset-0 z-[var(--z-drawer)] bg-overlay lg:hidden" onClick={() => setMobileOpen(false)}>
          <aside
            className="h-full w-[84vw] max-w-[320px] border-r border-line bg-surface shadow-xl"
            onClick={(event) => event.stopPropagation()}
          >
            <NavSidebar role={role} mobile onNavigate={() => setMobileOpen(false)} />
          </aside>
        </div>
      ) : null}

      <nav className="fixed inset-x-0 bottom-0 z-[var(--z-sticky)] grid grid-cols-5 border-t border-line bg-surface p-2 lg:hidden">
        {bottomNavItems.map((item) => {
          const isActive = pathname === item.href || pathname.startsWith(`${item.href}/`);
          return (
            <Link
              key={item.href}
              href={item.href}
              className={[
                "flex min-h-[var(--touch-sm)] items-center justify-center rounded-md px-2 text-xs font-medium transition",
                isActive ? "bg-brand-subtle text-brand" : "text-ink-soft hover:bg-sunken hover:text-ink",
              ].join(" ")}
            >
              {item.label}
            </Link>
          );
        })}
      </nav>
    </div>
  );
}

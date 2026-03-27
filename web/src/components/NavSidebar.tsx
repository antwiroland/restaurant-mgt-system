"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { StaffRole } from "@/lib/apiClient";

type NavItem = {
  label: string;
  href: string;
  short: string;
  roles?: StaffRole[];
};

type NavGroup = {
  title: string;
  items: NavItem[];
};

const groups: NavGroup[] = [
  {
    title: "Operations",
    items: [
      { label: "Dashboard", href: "/dashboard", short: "DB" },
      { label: "POS", href: "/pos", short: "PO" },
      { label: "Orders", href: "/orders", short: "OR" },
      { label: "KDS", href: "/kds", short: "KD" },
      { label: "Tables", href: "/tables", short: "TB" },
      { label: "Reservations", href: "/reservations", short: "RS" },
      { label: "Group Ordering", href: "/group-ordering", short: "GO" },
      { label: "Pickup", href: "/orders/pickup", short: "PK" },
    ],
  },
  {
    title: "Management",
    items: [
      { label: "Menu", href: "/menu", short: "MN" },
      { label: "Payments", href: "/payments", short: "PY" },
      { label: "Financial", href: "/financial", short: "FI" },
      { label: "Receipts", href: "/receipts", short: "RC" },
      { label: "Shifts", href: "/shifts", short: "SH" },
    ],
  },
  {
    title: "Admin",
    items: [
      { label: "Users", href: "/users", short: "US", roles: ["ADMIN", "MANAGER"] },
      { label: "Branches", href: "/branches", short: "BR", roles: ["ADMIN", "MANAGER"] },
      { label: "Audit Log", href: "/audit", short: "AL", roles: ["ADMIN", "MANAGER"] },
      { label: "Admin Panel", href: "/admin", short: "AP", roles: ["ADMIN", "MANAGER"] },
    ],
  },
];

function allowed(item: NavItem, role: StaffRole | undefined) {
  if (!item.roles) return true;
  return !!role && item.roles.includes(role);
}

function active(pathname: string, href: string) {
  return pathname === href || pathname.startsWith(`${href}/`);
}

type NavSidebarProps = {
  role?: StaffRole;
  collapsed?: boolean;
  onNavigate?: () => void;
  mobile?: boolean;
};

export function NavSidebar({ role, collapsed = false, onNavigate, mobile = false }: NavSidebarProps) {
  const pathname = usePathname();
  const content = groups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => allowed(item, role)),
    }))
    .filter((group) => group.items.length > 0);

  return (
    <nav className={`h-full overflow-y-auto px-3 py-4 ${mobile ? "pt-5" : ""}`} aria-label="Staff Navigation">
      <div className="mb-6 px-2">
        <p className="kicker">Restaurant Manager</p>
        {!collapsed ? <p className="mt-1 text-sm text-ink-soft">Staff Console</p> : null}
      </div>

      <div className="grid gap-5">
        {content.map((group) => (
          <section key={group.title} className="grid gap-2">
            {!collapsed ? <p className="kicker px-2">{group.title}</p> : null}
            <div className="grid gap-1">
              {group.items.map((item) => {
                const isActive = active(pathname, item.href);
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    onClick={onNavigate}
                    className={[
                      "grid min-h-[var(--touch-md)] items-center rounded-md border-l-4 px-4 text-sm font-medium transition",
                      collapsed ? "justify-center border-l-transparent px-2 text-xs" : "",
                      isActive
                        ? "border-l-brand bg-brand-subtle text-brand"
                        : "border-l-transparent text-ink-soft hover:bg-sunken hover:text-ink",
                    ].join(" ")}
                    title={collapsed ? item.label : undefined}
                  >
                    {collapsed ? item.short : item.label}
                  </Link>
                );
              })}
            </div>
          </section>
        ))}
      </div>
    </nav>
  );
}


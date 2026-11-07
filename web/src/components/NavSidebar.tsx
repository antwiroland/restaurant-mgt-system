"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Icon } from "@/components/Icon";
import { brand } from "@/lib/brand";
import type { StaffRole } from "@/lib/apiClient";
import { isActivePath, navForRole, navItemForPath, roleLabel } from "@/lib/navigation";

type NavSidebarProps = {
  role?: StaffRole;
  userName?: string;
  collapsed?: boolean;
  onNavigate?: () => void;
  onSignOut?: () => void;
  mobile?: boolean;
};

export function BrandMark({ size = 36 }: { size?: number }) {
  return (
    <span
      className="grid shrink-0 place-items-center rounded-[10px] bg-brand font-serif font-semibold text-white shadow-[inset_0_1px_0_rgba(255,255,255,0.2)]"
      style={{ width: size, height: size, fontSize: size * 0.5 }}
      aria-hidden="true"
    >
      {brand.name.charAt(0)}
    </span>
  );
}

export function NavSidebar({ role, userName, collapsed = false, onNavigate, onSignOut, mobile = false }: NavSidebarProps) {
  const pathname = usePathname();
  const groups = navForRole(role);
  const current = navItemForPath(pathname);

  return (
    <nav
      className="flex h-full flex-col bg-espresso text-espresso-ink"
      aria-label="Staff navigation"
    >
      <div className={`flex items-center gap-3 border-b border-espresso-line ${collapsed ? "justify-center px-2 py-5" : "px-5 py-5"} ${mobile ? "pt-6" : ""}`}>
        <BrandMark />
        {!collapsed ? (
          <div className="min-w-0">
            <p className="truncate font-serif text-lg font-semibold leading-tight text-espresso-ink">{brand.name}</p>
            <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-espresso-soft">Operations</p>
          </div>
        ) : null}
      </div>

      <div className="flex-1 overflow-y-auto px-3 py-4 [scrollbar-color:var(--color-espresso-line)_transparent]">
        <div className="grid gap-5">
          {groups.map((group) => (
            <section key={group.title} className="grid gap-1">
              {!collapsed ? (
                <p className="px-3 pb-1 text-[10.5px] font-bold uppercase tracking-[0.16em] text-espresso-soft/80">{group.title}</p>
              ) : (
                <div className="mx-auto my-1 h-px w-6 bg-espresso-line" aria-hidden="true" />
              )}
              {group.items.map((item) => {
                const active = current?.href === item.href || (!current && isActivePath(pathname, item.href));
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    onClick={onNavigate}
                    title={collapsed ? item.label : undefined}
                    aria-label={collapsed ? item.label : undefined}
                    aria-current={active ? "page" : undefined}
                    className={[
                      "group relative flex min-h-[42px] items-center gap-3 rounded-lg text-[14px] font-semibold transition-colors duration-150",
                      collapsed ? "justify-center px-0" : "px-3",
                      active
                        ? "bg-espresso-raised text-white"
                        : "text-espresso-soft hover:bg-espresso-raised/60 hover:text-espresso-ink",
                    ].join(" ")}
                  >
                    {active ? <span className="absolute inset-y-2 left-0 w-[3px] rounded-r-full bg-brand" aria-hidden="true" /> : null}
                    <Icon name={item.icon} size={19} className={active ? "text-gold" : "text-espresso-soft group-hover:text-espresso-ink"} />
                    {!collapsed ? <span className="truncate">{item.label}</span> : null}
                  </Link>
                );
              })}
            </section>
          ))}
        </div>
      </div>

      <div className={`border-t border-espresso-line ${collapsed ? "px-2 py-3" : "p-3"}`}>
        {!collapsed && userName ? (
          <div className="mb-2 flex items-center gap-3 rounded-lg px-2 py-2">
            <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-espresso-raised text-sm font-bold text-gold">
              {userName.charAt(0).toUpperCase()}
            </span>
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-espresso-ink">{userName}</p>
              {role ? <p className="text-xs text-espresso-soft">{roleLabel[role]}</p> : null}
            </div>
          </div>
        ) : null}
        {onSignOut ? (
          <button
            type="button"
            onClick={onSignOut}
            title={collapsed ? "Sign out" : undefined}
            aria-label={collapsed ? "Sign out" : undefined}
            className={`flex min-h-[42px] w-full items-center gap-3 rounded-lg text-sm font-semibold text-espresso-soft transition-colors hover:bg-espresso-raised hover:text-espresso-ink ${collapsed ? "justify-center" : "px-3"}`}
          >
            <Icon name="logout" size={19} />
            {!collapsed ? "Sign out" : null}
          </button>
        ) : null}
      </div>
    </nav>
  );
}

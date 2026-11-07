import type { IconName } from "@/components/Icon";
import type { StaffRole } from "@/lib/apiClient";

/**
 * Single source of truth for staff navigation.
 *
 * `roles` mirrors the backend @PreAuthorize guards for the endpoints each page
 * depends on. Hiding a link is a usability measure only — the API still
 * enforces authorization on every request.
 */
export type NavItem = {
  label: string;
  href: string;
  icon: IconName;
  roles: StaffRole[];
  description?: string;
};

export type NavGroup = { title: string; items: NavItem[] };

const OPS: StaffRole[] = ["ADMIN", "MANAGER", "CASHIER"];
const MGMT: StaffRole[] = ["ADMIN", "MANAGER"];

export const navGroups: NavGroup[] = [
  {
    title: "Service",
    items: [
      { label: "Dashboard", href: "/dashboard", icon: "dashboard", roles: MGMT, description: "Today's performance" },
      { label: "Point of Sale", href: "/pos", icon: "register", roles: OPS, description: "Take orders and payments" },
      { label: "Orders", href: "/orders", icon: "receipt", roles: OPS, description: "Track every order" },
      { label: "Kitchen Display", href: "/kds", icon: "chef", roles: OPS, description: "Live kitchen tickets" },
      { label: "Floor & Tables", href: "/tables", icon: "table", roles: OPS, description: "Seating and bills" },
      { label: "Reservations", href: "/reservations", icon: "calendar", roles: OPS, description: "Bookings" },
      { label: "Pickup", href: "/orders/pickup", icon: "bag", roles: OPS, description: "Hand over by code" },
      { label: "Group Orders", href: "/group-ordering", icon: "group", roles: OPS, description: "Shared carts" },
      { label: "Delivery", href: "/delivery", icon: "scooter", roles: OPS, description: "Dispatch and zones" },
      { label: "My Deliveries", href: "/rider", icon: "route", roles: ["RIDER"], description: "Your assigned drops" },
    ],
  },
  {
    title: "Money",
    items: [
      { label: "Payments", href: "/payments", icon: "card", roles: OPS },
      { label: "Receipts", href: "/receipts", icon: "note", roles: OPS },
      { label: "Financial", href: "/financial", icon: "chart", roles: OPS },
      { label: "Cash Shifts", href: "/shifts", icon: "clock", roles: OPS },
    ],
  },
  {
    title: "Menu",
    items: [
      { label: "Menu Builder", href: "/menu", icon: "book", roles: MGMT },
      { label: "Offers", href: "/offers", icon: "tag", roles: MGMT },
    ],
  },
  {
    title: "Admin",
    items: [
      { label: "Staff & Users", href: "/users", icon: "users", roles: ["ADMIN"] },
      { label: "Branches", href: "/branches", icon: "store", roles: MGMT },
      { label: "Audit Log", href: "/audit", icon: "shield", roles: MGMT },
      { label: "Admin Panel", href: "/admin", icon: "settings", roles: MGMT },
    ],
  },
];

const allItems = navGroups.flatMap((group) => group.items);

export function navForRole(role: StaffRole | undefined): NavGroup[] {
  if (!role) return [];
  return navGroups
    .map((group) => ({ ...group, items: group.items.filter((item) => item.roles.includes(role)) }))
    .filter((group) => group.items.length > 0);
}

export function isActivePath(pathname: string, href: string): boolean {
  return pathname === href || pathname.startsWith(`${href}/`);
}

/** Most specific nav item matching the path (so /orders/pickup beats /orders). */
export function navItemForPath(pathname: string): NavItem | undefined {
  return allItems
    .filter((item) => isActivePath(pathname, item.href))
    .sort((a, b) => b.href.length - a.href.length)[0];
}

/** Whether a role may open a staff route. Unknown routes are allowed. */
export function canAccessPath(role: StaffRole | undefined, pathname: string): boolean {
  if (!role) return false;
  const item = navItemForPath(pathname);
  if (!item) return role !== "RIDER" && role !== "CUSTOMER";
  return item.roles.includes(role);
}

/** Thumb-reach bar on phones: the five most-used destinations per role. */
const bottomByRole: Partial<Record<StaffRole, string[]>> = {
  ADMIN: ["/dashboard", "/pos", "/orders", "/kds", "/tables"],
  MANAGER: ["/dashboard", "/pos", "/orders", "/kds", "/tables"],
  CASHIER: ["/pos", "/orders", "/kds", "/tables", "/orders/pickup"],
  RIDER: ["/rider"],
};

export function bottomNavForRole(role: StaffRole | undefined): NavItem[] {
  if (!role) return [];
  return (bottomByRole[role] ?? [])
    .map((href) => allItems.find((item) => item.href === href))
    .filter((item): item is NavItem => !!item);
}

export const roleLabel: Record<StaffRole, string> = {
  ADMIN: "Owner / Admin",
  MANAGER: "Manager",
  CASHIER: "Cashier",
  RIDER: "Rider",
  CUSTOMER: "Customer",
};

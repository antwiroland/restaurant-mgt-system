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
  /**
   * HR items: shown only when the user holds at least one of these HR permission codes
   * ("self" = the account is linked to an employee record). Checked against /hr/me/access.
   */
  hr?: string[];
};

export type NavGroup = { title: string; items: NavItem[] };

const OPS: StaffRole[] = ["ADMIN", "MANAGER", "CASHIER"];
const MGMT: StaffRole[] = ["ADMIN", "MANAGER"];
const STAFF: StaffRole[] = ["ADMIN", "MANAGER", "CASHIER", "RIDER"];

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
      { label: "Schedule", href: "/schedule", icon: "timer", roles: STAFF, description: "Rota and time clock" },
    ],
  },
  {
    title: "Money",
    items: [
      { label: "Payments", href: "/payments", icon: "card", roles: OPS },
      { label: "Receipts", href: "/receipts", icon: "note", roles: OPS },
      { label: "Financial", href: "/financial", icon: "chart", roles: OPS },
      { label: "Cash Shifts", href: "/shifts", icon: "clock", roles: OPS },
      { label: "Reports", href: "/reports", icon: "trend", roles: MGMT },
    ],
  },
  {
    title: "Menu",
    items: [
      { label: "Menu Builder", href: "/menu", icon: "book", roles: MGMT },
      { label: "Offers", href: "/offers", icon: "tag", roles: MGMT },
      { label: "Inventory", href: "/inventory", icon: "box", roles: OPS },
      { label: "Guest Feedback", href: "/feedback", icon: "star", roles: MGMT },
    ],
  },
  {
    title: "Human Resources",
    items: [
      { label: "Employees", href: "/hr/employees", icon: "users", roles: STAFF, hr: ["hr.employee.view"], description: "Employee directory" },
      { label: "Branch Workforce", href: "/hr/branches", icon: "store", roles: STAFF, hr: ["hr.branch.view", "hr.employee.view"], description: "Headcount by branch" },
      { label: "Departments & Positions", href: "/hr/organisation", icon: "clipboard", roles: STAFF, hr: ["hr.employee.view", "hr.department.manage", "hr.position.manage"] },
      { label: "HR Access", href: "/hr/access", icon: "shield", roles: ["ADMIN"], description: "Grant HR permissions" },
      { label: "My HR Profile", href: "/hr/me", icon: "user", roles: STAFF, hr: ["self"], description: "Your employment record" },
    ],
  },
  {
    title: "Admin",
    items: [
      { label: "Staff & Users", href: "/users", icon: "users", roles: ["ADMIN"] },
      { label: "Branches", href: "/branches", icon: "store", roles: MGMT },
      { label: "Billing & Tax", href: "/billing", icon: "cash", roles: MGMT },
      { label: "Restaurant Profile", href: "/restaurant", icon: "store", roles: MGMT },
      { label: "Printers", href: "/printers", icon: "print", roles: MGMT },
      { label: "Audit Log", href: "/audit", icon: "shield", roles: MGMT },
      { label: "Admin Panel", href: "/admin", icon: "settings", roles: MGMT },
    ],
  },
  {
    title: "You",
    items: [{ label: "My Account", href: "/account", icon: "user", roles: STAFF, description: "Password and profile" }],
  },
];

const allItems = navGroups.flatMap((group) => group.items);

/**
 * Navigation for a role. HR items additionally require a matching HR permission; pass the checker from
 * useHrAccess(). Without a checker (access not loaded yet) HR items stay hidden.
 */
export function navForRole(role: StaffRole | undefined, canHr?: (...anyOf: string[]) => boolean): NavGroup[] {
  if (!role) return [];
  return navGroups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => item.roles.includes(role) && (!item.hr || (canHr ? canHr(...item.hr) : false))),
    }))
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
  RIDER: ["/rider", "/schedule", "/account"],
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

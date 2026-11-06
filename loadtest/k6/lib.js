import http from 'k6/http';
import { check, fail } from 'k6';

export const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');

export function jsonHeaders(token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  return { headers };
}

/** Logs in the staff account given by STAFF_PHONE / STAFF_PASSWORD (a cashier, manager or admin). */
export function staffLogin() {
  const phone = __ENV.STAFF_PHONE;
  const password = __ENV.STAFF_PASSWORD;
  if (!phone || !password) {
    fail('Set STAFF_PHONE and STAFF_PASSWORD to a cashier/manager/admin account');
  }
  const res = http.post(`${BASE_URL}/auth/login`, JSON.stringify({ phone, password }), jsonHeaders());
  if (!check(res, { 'login 200': (r) => r.status === 200 })) {
    fail(`Login failed with status ${res.status}: ${res.body}`);
  }
  return res.json('accessToken');
}

/** Returns available menu item IDs; fails fast when the menu is empty. */
export function availableMenuItems() {
  const res = http.get(`${BASE_URL}/menu/items`);
  check(res, { 'menu 200': (r) => r.status === 200 });
  const items = (res.json() || []).filter((item) => item.available && item.active !== false);
  if (items.length === 0) {
    fail('No available menu items. Create at least one menu item before running the load test');
  }
  return items.map((item) => item.id);
}

export function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

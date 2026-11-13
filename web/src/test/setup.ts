import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Newer Node versions ship an experimental global localStorage that is undefined unless started with
// --localstorage-file, and it hides jsdom's. Give the tests a working in-memory Storage when that happens.
function memoryStorage(): Storage {
  const data = new Map<string, string>();
  return {
    get length() {
      return data.size;
    },
    clear: () => data.clear(),
    getItem: (key) => (data.has(key) ? data.get(key)! : null),
    key: (index) => Array.from(data.keys())[index] ?? null,
    removeItem: (key) => void data.delete(key),
    setItem: (key, value) => void data.set(key, String(value)),
  };
}

for (const name of ["localStorage", "sessionStorage"] as const) {
  let works = false;
  try {
    works = typeof window[name]?.getItem === "function";
  } catch {
    works = false;
  }
  if (!works) {
    const storage = memoryStorage();
    Object.defineProperty(window, name, { configurable: true, value: storage });
    Object.defineProperty(globalThis, name, { configurable: true, value: storage });
  }
}

afterEach(() => {
  cleanup();
});

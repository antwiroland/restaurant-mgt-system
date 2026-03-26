import type { Config } from "tailwindcss";

/**
 * Restaurant Manager — Tailwind CSS v4 Configuration
 *
 * Design tokens (colors, spacing, radius, shadows, fonts) live in
 * src/app/globals.css inside the @theme { } block. Tailwind v4 reads
 * those CSS custom properties and generates utility classes from them.
 *
 * This file handles: content paths, plugin registration, and any
 * build-time options that cannot be expressed in CSS.
 */
const config: Config = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/features/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/lib/**/*.{js,ts,jsx,tsx,mdx}",
  ],
};

export default config;

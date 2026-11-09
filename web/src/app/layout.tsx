import type { Metadata, Viewport } from "next";
import { Manrope, Fraunces, IBM_Plex_Mono } from "next/font/google";
import { BrandProvider } from "@/components/BrandProvider";
import { SessionProvider } from "@/components/SessionProvider";
import { I18nProvider } from "@/components/I18nProvider";
import { getBrand } from "@/lib/publicServer";
import "./globals.css";

const manrope = Manrope({
  variable: "--font-manrope",
  subsets: ["latin"],
  display: "swap",
});

const fraunces = Fraunces({
  variable: "--font-fraunces",
  subsets: ["latin"],
  display: "swap",
  style: ["normal", "italic"],
});

const plexMono = IBM_Plex_Mono({
  variable: "--font-plex-mono",
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  display: "swap",
});

export async function generateMetadata(): Promise<Metadata> {
  const brand = await getBrand();
  return {
    title: {
      default: `${brand.name} — ${brand.tagline}`,
      template: `%s · ${brand.name}`,
    },
    description: `${brand.name}: browse the menu, order for pickup or delivery, reserve a table, or order from your table.`,
  };
}

export const viewport: Viewport = {
  themeColor: "#231a15",
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  // Restaurant profile from the API (env values fill gaps); fetched per request so edits show immediately.
  const brand = await getBrand();
  return (
    <html lang="en" className={`${manrope.variable} ${fraunces.variable} ${plexMono.variable}`}>
      <body>
        <I18nProvider>
          <BrandProvider brand={brand}>
            <SessionProvider>{children}</SessionProvider>
          </BrandProvider>
        </I18nProvider>
      </body>
    </html>
  );
}

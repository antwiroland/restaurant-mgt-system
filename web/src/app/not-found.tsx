import Link from "next/link";
import { BrandMark } from "@/components/NavSidebar";

export default function NotFound() {
  return (
    <main className="grid min-h-dvh place-items-center bg-ivory px-4 py-12 text-center">
      <div className="max-w-md">
        <div className="mx-auto w-fit"><BrandMark size={48} /></div>
        <p className="eyebrow mt-8">404</p>
        <h1 className="heading-serif mt-3 text-4xl">This page isn&apos;t on the menu</h1>
        <p className="mt-3 text-ink-soft">The link may be old, or the page has moved.</p>
        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <Link href="/" className="btn btn-primary btn-md">Back to home</Link>
          <Link href="/explore" className="btn btn-secondary btn-md">Browse the menu</Link>
        </div>
      </div>
    </main>
  );
}

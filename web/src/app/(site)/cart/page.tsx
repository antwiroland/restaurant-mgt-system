import type { Metadata } from "next";
import { CartClient } from "./CartClient";

export const metadata: Metadata = { title: "Your order", robots: { index: false } };

export default function CartPage() {
  return (
    <div className="site py-10 md:py-14">
      <CartClient />
    </div>
  );
}

import type { Metadata } from "next";
import { Suspense } from "react";
import { TrackOrderClient } from "./TrackOrderClient";

export const metadata: Metadata = { title: "Track your order", robots: { index: false } };

type Props = { params: Promise<{ id: string }> };

export default async function TrackOrderPage({ params }: Props) {
  const { id } = await params;
  return (
    <div className="site py-10 md:py-14">
      <Suspense>
        <TrackOrderClient orderId={id} />
      </Suspense>
    </div>
  );
}

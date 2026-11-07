import type { Metadata } from "next";
import { PrintSlip } from "@/components/PrintSlip";

export const metadata: Metadata = { title: "Print kitchen", robots: { index: false } };

type Props = { params: Promise<{ orderId: string }> };

export default async function PrintKitchenPage({ params }: Props) {
  const { orderId } = await params;
  return <PrintSlip orderId={orderId} kind="kitchen" />;
}

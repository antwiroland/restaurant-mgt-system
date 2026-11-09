import type { Metadata } from "next";
import { PrintSlip } from "@/components/PrintSlip";

export const metadata: Metadata = { title: "Print receipt", robots: { index: false } };

type Props = { params: Promise<{ orderId: string }> };

export default async function PrintReceiptPage({ params }: Props) {
  const { orderId } = await params;
  return <PrintSlip orderId={orderId} kind="receipt" />;
}

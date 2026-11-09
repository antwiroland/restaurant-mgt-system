import type { Metadata } from "next";
import { AuthCard } from "@/components/AuthCard";
import { VerifyEmail } from "../account-forms";

export const metadata: Metadata = { title: "Verify email" };

type Props = { searchParams: Promise<{ token?: string | string[] }> };

export default async function VerifyEmailPage({ searchParams }: Props) {
  const { token } = await searchParams;
  return (
    <AuthCard eyebrow="Account" title="Verify your email">
      <VerifyEmail token={(Array.isArray(token) ? token[0] : token) ?? ""} />
    </AuthCard>
  );
}

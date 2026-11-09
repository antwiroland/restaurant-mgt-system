import type { Metadata } from "next";
import { AuthCard } from "@/components/AuthCard";
import { ResetPasswordForm } from "../account-forms";

export const metadata: Metadata = { title: "Choose a new password" };

type Props = { searchParams: Promise<{ token?: string | string[] }> };

export default async function ResetPasswordPage({ searchParams }: Props) {
  const { token } = await searchParams;
  return (
    <AuthCard eyebrow="Account" title="Choose a new password" description="Reset links expire 30 minutes after they're sent.">
      <ResetPasswordForm token={(Array.isArray(token) ? token[0] : token) ?? ""} />
    </AuthCard>
  );
}

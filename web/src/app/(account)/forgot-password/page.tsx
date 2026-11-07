import type { Metadata } from "next";
import { AuthCard } from "@/components/AuthCard";
import { ForgotPasswordForm } from "../account-forms";

export const metadata: Metadata = { title: "Forgot password" };

export default function ForgotPasswordPage() {
  return (
    <AuthCard eyebrow="Account" title="Reset your password" description="We'll send a link to the email on your account, or by SMS if you don't have one.">
      <ForgotPasswordForm />
    </AuthCard>
  );
}

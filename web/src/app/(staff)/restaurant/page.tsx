"use client";

import { useEffect, useState } from "react";
import { Icon } from "@/components/Icon";
import { Skeleton } from "@/components/Skeleton";
import { Spinner } from "@/components/Spinner";
import { ErrorState, PageHeader } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { getRestaurantProfile, updateRestaurantProfile, type RestaurantProfileRecord } from "@/lib/apiClient";

type Form = {
  name: string;
  tagline: string;
  about: string;
  address: string;
  phone: string;
  email: string;
  websiteUrl: string;
  heroImageUrl: string;
  currency: string;
  hours: string;
  latitude: string;
  longitude: string;
  receiptFooter: string;
};

function toForm(p: RestaurantProfileRecord): Form {
  return {
    name: p.name ?? "",
    tagline: p.tagline ?? "",
    about: p.about ?? "",
    address: p.address ?? "",
    phone: p.phone ?? "",
    email: p.email ?? "",
    websiteUrl: p.websiteUrl ?? "",
    heroImageUrl: p.heroImageUrl ?? "",
    currency: p.currency ?? "GHS",
    hours: (p.openingHours ?? []).join("\n"),
    latitude: p.latitude == null ? "" : String(p.latitude),
    longitude: p.longitude == null ? "" : String(p.longitude),
    receiptFooter: p.receiptFooter ?? "",
  };
}

export default function RestaurantProfilePage() {
  const { session, authenticatedFetch } = useStaffSession();
  const [form, setForm] = useState<Form | null>(null);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const canEdit = session?.user.role === "ADMIN";

  async function load() {
    setError("");
    try {
      setForm(toForm(await getRestaurantProfile()));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load the restaurant profile");
    }
  }

  useEffect(() => {
    void load();
  }, []);

  function field<K extends keyof Form>(key: K) {
    return {
      value: form?.[key] ?? "",
      onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
        setSaved(false);
        setForm((current) => (current ? { ...current, [key]: event.target.value } : current));
      },
      disabled: !canEdit,
    };
  }

  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (!form) return;
    setSaving(true);
    setError("");
    try {
      const num = (value: string) => (value.trim() === "" ? null : Number(value));
      const next = await authenticatedFetch((s) =>
        updateRestaurantProfile(s, {
          name: form.name,
          tagline: form.tagline,
          about: form.about,
          address: form.address,
          phone: form.phone,
          email: form.email,
          websiteUrl: form.websiteUrl,
          heroImageUrl: form.heroImageUrl,
          currency: form.currency,
          openingHours: form.hours.split("\n").map((line) => line.trim()).filter(Boolean),
          latitude: num(form.latitude),
          longitude: num(form.longitude),
          receiptFooter: form.receiptFooter,
        }),
      );
      setForm(toForm(next));
      setSaved(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save");
    } finally {
      setSaving(false);
    }
  }

  return (
    <main className="shell max-w-4xl">
      <PageHeader
        eyebrow="Admin"
        title="Restaurant profile"
        icon="store"
        description="Name, contact details, opening hours and location shown on the website, in the app, and on printed receipts."
      />
      {error ? <ErrorState message={error} onRetry={() => void load()} /> : null}
      {!canEdit ? <p className="alert alert-info"><Icon name="info" size={18} className="mt-0.5 shrink-0" />Only admins can change these details.</p> : null}

      {!form ? (
        <div className="grid gap-3"><Skeleton className="h-40" /><Skeleton className="h-60" /></div>
      ) : (
        <form onSubmit={save} className="grid gap-5">
          <section className="panel grid gap-4">
            <h2 className="heading-md">Identity</h2>
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="field"><span className="field-label">Restaurant name</span><input className="input" required maxLength={120} {...field("name")} /></label>
              <label className="field"><span className="field-label">Tagline</span><input className="input" maxLength={160} placeholder="Seasonal kitchen & bar" {...field("tagline")} /></label>
            </div>
            <label className="field"><span className="field-label">About</span><textarea className="textarea" maxLength={4000} placeholder="A short paragraph for the homepage" {...field("about")} /></label>
            <div className="grid gap-4 sm:grid-cols-[1fr_120px]">
              <label className="field"><span className="field-label">Homepage photo URL</span><input className="input" type="url" placeholder="https://…" {...field("heroImageUrl")} /><span className="field-hint">Leave empty to use the first dish photo.</span></label>
              <label className="field"><span className="field-label">Currency</span><input className="input uppercase" maxLength={8} {...field("currency")} /></label>
            </div>
          </section>

          <section className="panel grid gap-4">
            <h2 className="heading-md">Contact & location</h2>
            <label className="field"><span className="field-label">Address</span><input className="input" maxLength={255} {...field("address")} /></label>
            <div className="grid gap-4 sm:grid-cols-3">
              <label className="field"><span className="field-label">Phone</span><input className="input" type="tel" maxLength={40} {...field("phone")} /></label>
              <label className="field"><span className="field-label">Email</span><input className="input" type="email" maxLength={160} {...field("email")} /></label>
              <label className="field"><span className="field-label">Website</span><input className="input" type="url" placeholder="https://…" {...field("websiteUrl")} /></label>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="field"><span className="field-label">Latitude</span><input className="input" inputMode="decimal" placeholder="5.5560" {...field("latitude")} /></label>
              <label className="field"><span className="field-label">Longitude</span><input className="input" inputMode="decimal" placeholder="-0.1969" {...field("longitude")} /></label>
            </div>
            <p className="field-hint -mt-2">Used for map links and the delivery map in the app.</p>
          </section>

          <section className="panel grid gap-4">
            <h2 className="heading-md">Opening hours & receipts</h2>
            <label className="field">
              <span className="field-label">Opening hours</span>
              <textarea className="textarea font-mono text-sm" rows={4} placeholder={"Mon–Thu 11:30–22:00\nFri–Sat 11:30–23:30\nSun 12:00–21:00"} {...field("hours")} />
              <span className="field-hint">One line each, shown exactly as typed.</span>
            </label>
            <label className="field"><span className="field-label">Receipt footer</span><input className="input" maxLength={500} placeholder="Thank you! Follow us @…" {...field("receiptFooter")} /></label>
          </section>

          {canEdit ? (
            <div className="flex items-center justify-end gap-3">
              {saved ? <span className="flex items-center gap-1.5 text-sm font-semibold text-success-on" role="status"><Icon name="checkCircle" size={18} /> Saved — the website updates right away</span> : null}
              <button className="btn btn-primary btn-md" disabled={saving}>{saving ? <Spinner /> : null} Save profile</button>
            </div>
          ) : null}
        </form>
      )}
    </main>
  );
}

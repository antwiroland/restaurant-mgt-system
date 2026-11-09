"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Icon } from "@/components/Icon";
import { useT } from "@/components/I18nProvider";
import { useStaffSession } from "@/components/SessionProvider";
import { Spinner } from "@/components/Spinner";
import { FoodImage } from "@/components/site/FoodImage";
import {
  addMyAddress,
  getFavourites,
  getMe,
  getMyAddresses,
  getMyReservations,
  getOrders,
  loginWithOtp,
  registerCustomer,
  requestOtp,
  setFavourite,
  updateMe,
  verifyOtp,
  type CustomerAddressRecord,
  type MenuItemRecord,
  type OrderRecord,
  type ReservationRecord,
} from "@/lib/apiClient";
import { formatMoney } from "@/lib/brand";
import { orderStatusLabel } from "@/lib/orderStatus";

function message(err: unknown, fallback: string) {
  return err instanceof Error ? err.message : fallback;
}

export function AccountClient() {
  const { session, loading } = useStaffSession();
  if (loading) {
    return <div className="grid min-h-[40vh] place-items-center"><Spinner size={28} /></div>;
  }
  if (!session) return <SignIn />;
  if (session.user.role !== "CUSTOMER") {
    return (
      <section className="mx-auto max-w-md rounded-card border border-line bg-surface p-8 text-center">
        <p className="text-ink-soft">This is a staff account. Use the staff console instead.</p>
        <Link href="/login" className="btn btn-primary mt-5">Open staff console</Link>
      </section>
    );
  }
  return <Dashboard />;
}

/* ------------------------------------------------------------------ */
/* Sign in / register                                                  */
/* ------------------------------------------------------------------ */

type Mode = "password" | "otp" | "register";

function SignIn() {
  const t = useT();
  const router = useRouter();
  const next = useSearchParams().get("next");
  const { login, adopt } = useStaffSession();
  const [mode, setMode] = useState<Mode>("password");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [verificationToken, setVerificationToken] = useState("");
  const [codeSent, setCodeSent] = useState(false);
  const [resendIn, setResendIn] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (resendIn <= 0) return;
    const timer = window.setTimeout(() => setResendIn((s) => s - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [resendIn]);

  function switchMode(nextMode: Mode) {
    setMode(nextMode);
    setError("");
    setCode("");
    setCodeSent(false);
    setVerificationToken("");
  }

  function done() {
    router.replace(next && next.startsWith("/") ? next : "/my-account");
  }

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError("");
    try {
      await action();
    } catch (err) {
      setError(message(err, "Something went wrong"));
    } finally {
      setBusy(false);
    }
  }

  const sendCode = () =>
    run(async () => {
      const result = await requestOtp(phone.trim(), mode === "register" ? "REGISTER" : "LOGIN");
      setCodeSent(true);
      setResendIn(result.resendAfterSeconds);
    });

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (mode === "password") {
      await run(async () => {
        const s = await login(phone.trim(), password);
        if (s.user.role !== "CUSTOMER") {
          router.replace("/login");
          return;
        }
        done();
      });
    } else if (mode === "otp") {
      if (!codeSent) return sendCode();
      await run(async () => {
        adopt(await loginWithOtp(phone.trim(), code.trim()));
        done();
      });
    } else if (!verificationToken) {
      if (!codeSent) return sendCode();
      await run(async () => {
        const verified = await verifyOtp(phone.trim(), code.trim());
        setVerificationToken(verified.verificationToken);
      });
    } else {
      await run(async () => {
        adopt(await registerCustomer({ name: name.trim(), phone: phone.trim(), email: email.trim() || undefined, password, verificationToken }));
        done();
      });
    }
  }

  const title = mode === "register" ? t("account.registerTitle") : t("account.signInTitle");
  const submitLabel =
    mode === "password" ? t("common.signIn")
      : mode === "otp" ? (codeSent ? t("common.signIn") : t("account.sendCode"))
        : !codeSent ? t("account.sendCode") : !verificationToken ? t("common.continue") : t("common.createAccount");

  return (
    <section className="mx-auto w-full max-w-md rounded-[20px] border border-line bg-surface p-7 shadow-card sm:p-8">
      <h1 className="heading-serif text-3xl">{title}</h1>
      <p className="mt-2 text-sm text-ink-soft">
        {mode === "register" ? "Order for pickup or delivery, save addresses and favourites, and track your orders." : "Sign in to order, track orders and see your history."}
      </p>

      <form onSubmit={submit} className="mt-6 grid gap-4">
        <label className="field">
          <span className="field-label">{t("account.phone")}</span>
          <input className="input" type="tel" inputMode="tel" autoComplete="tel" required value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="+233 24 000 0000" disabled={codeSent && mode !== "password"} />
        </label>

        {mode === "password" ? (
          <label className="field">
            <span className="field-label">{t("account.password")}</span>
            <input className="input" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
          </label>
        ) : null}

        {mode !== "password" && codeSent && !verificationToken ? (
          <label className="field">
            <span className="field-label">{t("account.code")}</span>
            <input className="input tracking-[0.3em]" inputMode="numeric" autoComplete="one-time-code" required maxLength={8} value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))} />
            <span className="field-hint">
              Sent by SMS to {phone}.{" "}
              {resendIn > 0 ? t("account.resendIn", { seconds: resendIn }) : <button type="button" className="font-semibold text-brand underline" onClick={() => void sendCode()}>Resend</button>}
            </span>
          </label>
        ) : null}

        {mode === "register" && verificationToken ? (
          <>
            <p className="alert alert-success"><Icon name="checkCircle" size={18} className="mt-0.5 shrink-0" />Phone verified.</p>
            <label className="field"><span className="field-label">{t("account.name")}</span><input className="input" autoComplete="name" required maxLength={120} value={name} onChange={(e) => setName(e.target.value)} /></label>
            <label className="field"><span className="field-label">{t("account.email")} <span className="font-normal text-ink-muted">({t("common.optional")})</span></span><input className="input" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} /></label>
            <label className="field"><span className="field-label">{t("account.password")}</span><input className="input" type="password" autoComplete="new-password" required minLength={6} value={password} onChange={(e) => setPassword(e.target.value)} /><span className="field-hint">At least 6 characters.</span></label>
          </>
        ) : null}

        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}

        <button className="btn btn-primary btn-lg btn-block" disabled={busy}>{busy ? <Spinner /> : null} {submitLabel}</button>
      </form>

      <div className="mt-5 grid gap-2 text-center text-sm">
        {mode === "password" ? (
          <>
            <button type="button" className="font-semibold text-brand hover:underline" onClick={() => switchMode("otp")}>{t("account.useCode")}</button>
            <Link href="/forgot-password" className="text-ink-soft hover:underline">{t("account.forgot")}</Link>
          </>
        ) : mode === "otp" ? (
          <button type="button" className="font-semibold text-brand hover:underline" onClick={() => switchMode("password")}>{t("account.usePassword")}</button>
        ) : null}
        {mode === "register" ? (
          <button type="button" className="text-ink-soft hover:underline" onClick={() => switchMode("password")}>{t("account.haveAccount")}</button>
        ) : (
          <button type="button" className="text-ink-soft hover:underline" onClick={() => switchMode("register")}>{t("account.noAccount")}</button>
        )}
      </div>
    </section>
  );
}

/* ------------------------------------------------------------------ */
/* Dashboard                                                           */
/* ------------------------------------------------------------------ */

type Tab = "orders" | "favourites" | "addresses" | "bookings" | "profile";

function Dashboard() {
  const t = useT();
  const { session, logout } = useStaffSession();
  const [tab, setTab] = useState<Tab>("orders");
  const tabs: { id: Tab; label: string; icon: "receipt" | "heart" | "pin" | "calendar" | "user" }[] = [
    { id: "orders", label: t("account.orders"), icon: "receipt" },
    { id: "favourites", label: t("account.favourites"), icon: "heart" },
    { id: "addresses", label: t("account.addresses"), icon: "pin" },
    { id: "bookings", label: t("account.bookings"), icon: "calendar" },
    { id: "profile", label: t("account.profile"), icon: "user" },
  ];

  return (
    <div className="grid gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="eyebrow">{t("account.title")}</p>
          <h1 className="heading-serif mt-2 text-4xl">Hello, {session?.user.name.split(" ")[0]}</h1>
        </div>
        <div className="flex gap-2">
          <Link href="/explore" className="btn btn-primary"><Icon name="bag" size={18} /> Order now</Link>
          <button type="button" className="btn btn-secondary" onClick={() => void logout()}><Icon name="logout" size={18} /> {t("common.signOut")}</button>
        </div>
      </div>

      <div className="flex gap-2 overflow-x-auto pb-1" role="tablist" aria-label={t("account.title")}>
        {tabs.map((item) => (
          <button
            key={item.id}
            type="button"
            role="tab"
            aria-selected={tab === item.id}
            className={`chip min-h-[42px] shrink-0 gap-2 ${tab === item.id ? "chip-active" : ""}`}
            onClick={() => setTab(item.id)}
          >
            <Icon name={item.icon} size={16} /> {item.label}
          </button>
        ))}
      </div>

      <div role="tabpanel">
        {tab === "orders" ? <OrdersTab /> : null}
        {tab === "favourites" ? <FavouritesTab /> : null}
        {tab === "addresses" ? <AddressesTab /> : null}
        {tab === "bookings" ? <BookingsTab /> : null}
        {tab === "profile" ? <ProfileTab /> : null}
      </div>
    </div>
  );
}

function useLoad<T>(load: () => Promise<T>) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState("");
  const reload = useCallback(() => {
    setError("");
    load().then(setData).catch((err) => setError(message(err, "Could not load")));
  }, [load]);
  useEffect(() => {
    const timer = window.setTimeout(reload, 0);
    return () => window.clearTimeout(timer);
  }, [reload]);
  return { data, error, reload, setData };
}

function Empty({ text, action }: { text: string; action?: React.ReactNode }) {
  return (
    <div className="rounded-card border border-dashed border-line bg-surface p-8 text-center text-ink-soft">
      <p>{text}</p>
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  );
}

function OrdersTab() {
  const t = useT();
  const { authenticatedFetch } = useStaffSession();
  const load = useCallback(() => authenticatedFetch((s) => getOrders(s)), [authenticatedFetch]);
  const { data, error, reload } = useLoad<OrderRecord[]>(load);
  if (error) return <p className="alert alert-danger">{error} <button className="underline" onClick={reload}>{t("common.retry")}</button></p>;
  if (!data) return <Spinner />;
  if (data.length === 0) return <Empty text={t("account.noOrders")} action={<Link href="/explore" className="btn btn-primary">{t("cart.browseMenu")}</Link>} />;
  return (
    <ul className="grid gap-3">
      {data.map((order) => (
        <li key={order.id}>
          <Link href={`/my-orders/${order.id}`} className="flex flex-wrap items-center gap-4 rounded-card border border-line bg-surface p-4 transition-shadow hover:shadow-card">
            <span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-cream text-brand">
              <Icon name={order.type === "DELIVERY" ? "scooter" : order.type === "PICKUP" ? "bag" : "table"} size={20} />
            </span>
            <div className="min-w-0 flex-1">
              <p className="font-semibold text-ink">
                #{order.id.slice(0, 8).toUpperCase()} · {order.type === "DINE_IN" ? `Table ${order.tableNumber ?? ""}` : order.type === "PICKUP" ? "Pickup" : "Delivery"}
              </p>
              <p className="truncate text-sm text-ink-soft">
                {new Date(order.createdAt).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })} · {order.items.map((i) => `${i.quantity}× ${i.name}`).join(", ")}
              </p>
            </div>
            <span className="badge badge-neutral">{orderStatusLabel(order)}</span>
            <span className="money font-semibold">{formatMoney(order.total)}</span>
          </Link>
        </li>
      ))}
    </ul>
  );
}

function FavouritesTab() {
  const t = useT();
  const { authenticatedFetch } = useStaffSession();
  const load = useCallback(() => authenticatedFetch((s) => getFavourites(s)), [authenticatedFetch]);
  const { data, error, reload, setData } = useLoad<MenuItemRecord[]>(load);
  if (error) return <p className="alert alert-danger">{error} <button className="underline" onClick={reload}>{t("common.retry")}</button></p>;
  if (!data) return <Spinner />;
  if (data.length === 0) return <Empty text={t("account.noFavourites")} action={<Link href="/explore" className="btn btn-primary">{t("cart.browseMenu")}</Link>} />;
  return (
    <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {data.map((item) => (
        <li key={item.id} className="overflow-hidden rounded-card border border-line bg-surface">
          <Link href={`/explore/${item.id}`}><FoodImage src={item.imageUrl} alt={item.name} ratio="aspect-[4/3]" /></Link>
          <div className="flex items-start justify-between gap-3 p-4">
            <div>
              <Link href={`/explore/${item.id}`} className="font-semibold hover:text-brand">{item.name}</Link>
              <p className="money text-sm text-ink-soft">{formatMoney(item.price)}{!item.available ? ` · ${t("menu.soldOut")}` : ""}</p>
            </div>
            <button
              type="button"
              className="btn btn-ghost btn-icon btn-sm text-brand"
              aria-label={t("menu.unfavourite")}
              onClick={async () => {
                await authenticatedFetch((s) => setFavourite(s, item.id, false));
                setData((current) => (current ?? []).filter((i) => i.id !== item.id));
              }}
            >
              <Icon name="heart" filled />
            </button>
          </div>
        </li>
      ))}
    </ul>
  );
}

function AddressesTab() {
  const t = useT();
  const { authenticatedFetch } = useStaffSession();
  const load = useCallback(() => authenticatedFetch((s) => getMyAddresses(s)), [authenticatedFetch]);
  const { data, error, reload } = useLoad<CustomerAddressRecord[]>(load);
  const [adding, setAdding] = useState(false);
  const [form, setForm] = useState({ label: "Home", addressLine: "", city: "", landmark: "", isDefault: false });
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");

  async function save(event: React.FormEvent) {
    event.preventDefault();
    setSaving(true);
    setSaveError("");
    try {
      await authenticatedFetch((s) => addMyAddress(s, { ...form, city: form.city || null, landmark: form.landmark || null }));
      setAdding(false);
      setForm({ label: "Home", addressLine: "", city: "", landmark: "", isDefault: false });
      reload();
    } catch (err) {
      setSaveError(message(err, "Could not save address"));
    } finally {
      setSaving(false);
    }
  }

  if (error) return <p className="alert alert-danger">{error} <button className="underline" onClick={reload}>{t("common.retry")}</button></p>;
  if (!data) return <Spinner />;
  return (
    <div className="grid gap-4">
      {data.length === 0 && !adding ? <Empty text={t("account.noAddresses")} /> : null}
      <ul className="grid gap-3 sm:grid-cols-2">
        {data.map((address) => (
          <li key={address.id} className="rounded-card border border-line bg-surface p-4">
            <p className="flex items-center gap-2 font-semibold">
              <Icon name="pin" size={16} className="text-brand" /> {address.label}
              {address.isDefault ? <span className="badge badge-success">{t("account.defaultAddress")}</span> : null}
            </p>
            <p className="mt-1 text-sm text-ink-soft">{[address.addressLine, address.city].filter(Boolean).join(", ")}</p>
            {address.landmark ? <p className="text-sm text-ink-muted">Near {address.landmark}</p> : null}
          </li>
        ))}
      </ul>
      {adding ? (
        <form onSubmit={save} className="grid gap-3 rounded-card border border-line bg-surface p-5">
          <div className="grid gap-3 sm:grid-cols-[160px_1fr]">
            <label className="field"><span className="field-label">{t("account.label")}</span><input className="input" required maxLength={40} value={form.label} onChange={(e) => setForm({ ...form, label: e.target.value })} /></label>
            <label className="field"><span className="field-label">{t("account.addressLine")}</span><input className="input" required maxLength={255} value={form.addressLine} onChange={(e) => setForm({ ...form, addressLine: e.target.value })} /></label>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="field"><span className="field-label">{t("account.city")}</span><input className="input" maxLength={80} value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} /></label>
            <label className="field"><span className="field-label">{t("account.landmark")}</span><input className="input" maxLength={120} value={form.landmark} onChange={(e) => setForm({ ...form, landmark: e.target.value })} /></label>
          </div>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={form.isDefault} onChange={(e) => setForm({ ...form, isDefault: e.target.checked })} /> {t("account.makeDefault")}</label>
          {saveError ? <p className="alert alert-danger" role="alert">{saveError}</p> : null}
          <div className="flex justify-end gap-2">
            <button type="button" className="btn btn-secondary" onClick={() => setAdding(false)}>{t("common.cancel")}</button>
            <button className="btn btn-primary" disabled={saving}>{saving ? <Spinner /> : null} {t("common.save")}</button>
          </div>
        </form>
      ) : (
        <button type="button" className="btn btn-secondary w-fit" onClick={() => setAdding(true)}><Icon name="plus" size={16} /> {t("account.addAddress")}</button>
      )}
    </div>
  );
}

function BookingsTab() {
  const t = useT();
  const { authenticatedFetch } = useStaffSession();
  const load = useCallback(() => authenticatedFetch((s) => getMyReservations(s)), [authenticatedFetch]);
  const { data, error, reload } = useLoad<ReservationRecord[]>(load);
  if (error) return <p className="alert alert-danger">{error} <button className="underline" onClick={reload}>{t("common.retry")}</button></p>;
  if (!data) return <Spinner />;
  if (data.length === 0) return <Empty text={t("account.noBookings")} action={<Link href="/reserve" className="btn btn-primary">Book a table</Link>} />;
  return (
    <ul className="grid gap-3">
      {data.map((r) => (
        <li key={r.id} className="flex flex-wrap items-center gap-4 rounded-card border border-line bg-surface p-4">
          <span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-cream text-brand"><Icon name="calendar" size={20} /></span>
          <div className="min-w-0 flex-1">
            <p className="font-semibold">{new Date(r.reservedAt).toLocaleString(undefined, { dateStyle: "full", timeStyle: "short" })}</p>
            <p className="text-sm text-ink-soft">Party of {r.partySize}{r.tableNumber ? ` · Table ${r.tableNumber}` : ""}</p>
          </div>
          <span className="badge badge-neutral">{r.status.charAt(0) + r.status.slice(1).toLowerCase()}</span>
        </li>
      ))}
      <li><Link href="/reserve" className="text-sm font-semibold text-brand hover:underline">Manage or cancel a booking →</Link></li>
    </ul>
  );
}

function ProfileTab() {
  const t = useT();
  const { authenticatedFetch } = useStaffSession();
  const [form, setForm] = useState<{ name: string; email: string; phone: string } | null>(null);
  const [state, setState] = useState<{ saving: boolean; saved: boolean; error: string }>({ saving: false, saved: false, error: "" });

  useEffect(() => {
    authenticatedFetch((s) => getMe(s))
      .then((me) => setForm({ name: me.name, email: me.email ?? "", phone: me.phone }))
      .catch((err) => setState((s) => ({ ...s, error: message(err, "Could not load profile") })));
  }, [authenticatedFetch]);

  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (!form) return;
    setState({ saving: true, saved: false, error: "" });
    try {
      await authenticatedFetch((s) => updateMe(s, { name: form.name.trim(), email: form.email.trim() || undefined, phone: form.phone.trim() }));
      setState({ saving: false, saved: true, error: "" });
    } catch (err) {
      setState({ saving: false, saved: false, error: message(err, "Could not save") });
    }
  }

  if (!form) return state.error ? <p className="alert alert-danger">{state.error}</p> : <Spinner />;
  return (
    <form onSubmit={save} className="grid max-w-lg gap-4 rounded-card border border-line bg-surface p-6">
      <label className="field"><span className="field-label">{t("account.name")}</span><input className="input" required maxLength={120} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
      <label className="field"><span className="field-label">{t("account.phone")}</span><input className="input" type="tel" required maxLength={20} value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} /></label>
      <label className="field"><span className="field-label">{t("account.email")}</span><input className="input" type="email" maxLength={255} value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></label>
      {state.error ? <p className="alert alert-danger" role="alert">{state.error}</p> : null}
      <div className="flex items-center justify-end gap-3">
        {state.saved ? <span className="text-sm font-semibold text-success-on" role="status">Saved</span> : null}
        <button className="btn btn-primary" disabled={state.saving}>{state.saving ? <Spinner /> : null} {t("common.save")}</button>
      </div>
    </form>
  );
}

"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ConfirmModal } from "@/components/ConfirmModal";
import { Skeleton } from "@/components/Skeleton";
import { useStaffSession } from "@/components/SessionProvider";
import {
  createTaxRate,
  deleteTaxRate,
  getBillingConfig,
  updateBillingSettings,
  updateTaxRate,
  type BillingConfigRecord,
  type TaxRateRecord,
} from "@/lib/apiClient";

type SettingsDraft = {
  serviceChargePercent: string;
  serviceChargeDineInOnly: boolean;
  pricesIncludeTax: boolean;
  taxDeliveryFee: boolean;
  tipsEnabled: boolean;
};

type TaxDraft = { id?: string; code: string; name: string; rate: string; compound: boolean; displayOrder: string; active: boolean };

const EMPTY_TAX: TaxDraft = { code: "", name: "", rate: "", compound: false, displayOrder: "0", active: true };

function toSettingsDraft(config: BillingConfigRecord): SettingsDraft {
  return {
    serviceChargePercent: String(Number(config.serviceChargePercent)),
    serviceChargeDineInOnly: config.serviceChargeDineInOnly,
    pricesIncludeTax: config.pricesIncludeTax,
    taxDeliveryFee: config.taxDeliveryFee,
    tipsEnabled: config.tipsEnabled,
  };
}

function Toggle({ label, hint, checked, onChange }: { label: string; hint: string; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <label className="flex min-h-[var(--touch-md)] items-start gap-3 text-sm">
      <input type="checkbox" className="mt-1 h-5 w-5" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      <span>
        <span className="font-medium">{label}</span>
        <span className="block text-ink-soft">{hint}</span>
      </span>
    </label>
  );
}

export default function BillingPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [config, setConfig] = useState<BillingConfigRecord | null>(null);
  const [settings, setSettings] = useState<SettingsDraft | null>(null);
  const [tax, setTax] = useState<TaxDraft>(EMPTY_TAX);
  const [pendingDelete, setPendingDelete] = useState<TaxRateRecord | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  const isAdmin = session?.user.role === "ADMIN";

  const load = useCallback(async () => {
    try {
      const next = await getBillingConfig();
      setConfig(next);
      setSettings(toSettingsDraft(next));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load billing settings");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function saveSettings(event: React.FormEvent) {
    event.preventDefault();
    if (!settings) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const next = await authenticatedFetch((s) =>
        updateBillingSettings(s, { ...settings, serviceChargePercent: Number(settings.serviceChargePercent || 0) }),
      );
      setConfig(next);
      setSettings(toSettingsDraft(next));
      setMessage("Billing settings saved. New orders use them straight away.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save settings");
    } finally {
      setBusy(false);
    }
  }

  async function saveTax(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    const payload = {
      code: tax.code.trim().toUpperCase(),
      name: tax.name.trim(),
      rate: Number(tax.rate),
      compound: tax.compound,
      displayOrder: Number(tax.displayOrder || 0),
      active: tax.active,
    };
    try {
      if (tax.id) {
        await authenticatedFetch((s) => updateTaxRate(s, tax.id as string, payload));
        setMessage(`${payload.name} updated`);
      } else {
        await authenticatedFetch((s) => createTaxRate(s, payload));
        setMessage(`${payload.name} added`);
      }
      setTax(EMPTY_TAX);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save tax");
    } finally {
      setBusy(false);
    }
  }

  async function confirmDelete() {
    if (!pendingDelete) return;
    const target = pendingDelete;
    setPendingDelete(null);
    try {
      await authenticatedFetch((s) => deleteTaxRate(s, target.id));
      if (tax.id === target.id) setTax(EMPTY_TAX);
      setMessage(`${target.name} removed`);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not remove tax");
    }
  }

  if (loading) return <main className="shell"><section className="panel grid gap-3"><Skeleton className="h-6 w-40" /><Skeleton className="h-40 w-full" /></section></main>;
  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">Billing</p>
          <h1 className="text-2xl font-semibold">Staff access required</h1>
          <Link href="/login" className="mt-4 inline-flex btn btn-primary">Open Login</Link>
        </section>
      </main>
    );
  }

  const taxes = [...(config?.taxes ?? [])].sort((a, b) => a.displayOrder - b.displayOrder);

  return (
    <main className="shell grid gap-4 lg:grid-cols-2">
      <section className="panel grid content-start gap-3">
        <div>
          <p className="kicker">Billing</p>
          <h1 className="text-2xl font-semibold">Service charge, tax and tips</h1>
          <p className="text-sm text-ink-soft">
            These rules are applied when an order is created and printed on every bill and receipt.
            {!isAdmin ? " Only admins can change them." : ""}
          </p>
        </div>

        {settings ? (
          <form className="grid gap-2" onSubmit={(e) => void saveSettings(e)}>
            <fieldset disabled={!isAdmin || busy} className="grid gap-2">
              <label className="grid gap-1 text-sm">
                <span className="font-medium">Service charge (%)</span>
                <input
                  className="input max-w-40"
                  type="number"
                  min={0}
                  max={100}
                  step="0.01"
                  required
                  value={settings.serviceChargePercent}
                  onChange={(e) => setSettings({ ...settings, serviceChargePercent: e.target.value })}
                />
              </label>
              <Toggle
                label="Service charge on dine-in only"
                hint="Pickup and delivery orders are not charged."
                checked={settings.serviceChargeDineInOnly}
                onChange={(v) => setSettings({ ...settings, serviceChargeDineInOnly: v })}
              />
              <Toggle
                label="Menu prices include tax"
                hint="Tax is worked out of the price instead of added on top."
                checked={settings.pricesIncludeTax}
                onChange={(v) => setSettings({ ...settings, pricesIncludeTax: v })}
              />
              <Toggle
                label="Tax the delivery fee"
                hint="Include the delivery fee in the taxable amount."
                checked={settings.taxDeliveryFee}
                onChange={(v) => setSettings({ ...settings, taxDeliveryFee: v })}
              />
              <Toggle
                label="Allow tips"
                hint="Show a tip field on POS and customer checkout. Tips are never taxed."
                checked={settings.tipsEnabled}
                onChange={(v) => setSettings({ ...settings, tipsEnabled: v })}
              />
              {isAdmin ? (
                <button type="submit" className="btn btn-primary justify-self-start">Save settings</button>
              ) : null}
            </fieldset>
          </form>
        ) : (
          <Skeleton className="h-40 w-full" />
        )}

        <div aria-live="polite">
          {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
          {message ? <p className="alert alert-success">{message}</p> : null}
        </div>
      </section>

      <section className="panel grid content-start gap-3">
        <div>
          <h2 className="text-xl font-semibold">Taxes</h2>
          <p className="text-sm text-ink-soft">
            Ghana example: NHIL 2.5%, GETFund 2.5% and COVID levy 1% on the bill, then VAT 15% as a compound tax on top of those levies.
          </p>
        </div>

        {taxes.length === 0 ? (
          <p className="text-sm text-ink-soft">No taxes configured. Bills are tax-free until you add one.</p>
        ) : (
          <ul className="grid gap-2">
            {taxes.map((rate) => (
              <li key={rate.id} className="card flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p className="font-semibold">{rate.name} <span className="text-ink-soft">({rate.code})</span></p>
                  <p className="text-sm text-ink-soft">
                    {Number(rate.rate)}% {rate.compound ? "· compound" : ""} · order {rate.displayOrder}
                  </p>
                  <span className={`badge ${rate.active ? "badge-success" : "badge-neutral"}`}>{rate.active ? "Active" : "Inactive"}</span>
                </div>
                {isAdmin ? (
                  <div className="flex gap-2">
                    <button
                      type="button"
                      className="btn btn-secondary btn-sm"
                      onClick={() =>
                        setTax({
                          id: rate.id,
                          code: rate.code,
                          name: rate.name,
                          rate: String(Number(rate.rate)),
                          compound: rate.compound,
                          displayOrder: String(rate.displayOrder),
                          active: rate.active,
                        })
                      }
                    >
                      Edit
                    </button>
                    <button type="button" className="btn btn-danger btn-sm" onClick={() => setPendingDelete(rate)}>Remove</button>
                  </div>
                ) : null}
              </li>
            ))}
          </ul>
        )}

        {isAdmin ? (
          <form className="grid gap-2 border-t border-line pt-3" onSubmit={(e) => void saveTax(e)}>
            <h3 className="font-semibold">{tax.id ? `Edit ${tax.name}` : "Add a tax"}</h3>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
              <label className="grid gap-1 text-sm">
                <span>Code</span>
                <input className="input" required maxLength={20} placeholder="VAT" value={tax.code} onChange={(e) => setTax({ ...tax, code: e.target.value })} />
              </label>
              <label className="grid gap-1 text-sm sm:col-span-2">
                <span>Name</span>
                <input className="input" required maxLength={60} placeholder="Value Added Tax" value={tax.name} onChange={(e) => setTax({ ...tax, name: e.target.value })} />
              </label>
              <label className="grid gap-1 text-sm">
                <span>Rate (%)</span>
                <input className="input" type="number" min={0} max={100} step="0.001" required value={tax.rate} onChange={(e) => setTax({ ...tax, rate: e.target.value })} />
              </label>
              <label className="grid gap-1 text-sm">
                <span>Order</span>
                <input className="input" type="number" min={0} value={tax.displayOrder} onChange={(e) => setTax({ ...tax, displayOrder: e.target.value })} />
              </label>
            </div>
            <Toggle label="Compound" hint="Charged on the bill plus all non-compound levies (how VAT works in Ghana)." checked={tax.compound} onChange={(v) => setTax({ ...tax, compound: v })} />
            <Toggle label="Active" hint="Inactive taxes are kept but not charged." checked={tax.active} onChange={(v) => setTax({ ...tax, active: v })} />
            <div className="flex gap-2">
              <button type="submit" className="btn btn-primary" disabled={busy}>{tax.id ? "Save tax" : "Add tax"}</button>
              {tax.id ? <button type="button" className="btn btn-secondary" onClick={() => setTax(EMPTY_TAX)}>Cancel</button> : null}
            </div>
          </form>
        ) : null}
      </section>

      <ConfirmModal
        open={pendingDelete !== null}
        title="Remove tax?"
        message={pendingDelete ? `${pendingDelete.name} will no longer be charged on new orders. Existing bills keep their tax lines.` : ""}
        confirmLabel="Remove"
        danger
        onConfirm={() => void confirmDelete()}
        onClose={() => setPendingDelete(null)}
      />
    </main>
  );
}

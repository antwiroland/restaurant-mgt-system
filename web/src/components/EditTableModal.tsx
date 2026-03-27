"use client";

import { useEffect, useState } from "react";
import { Modal } from "@/components/Modal";
import type { TableRecord } from "@/lib/apiClient";

type EditTableModalProps = {
  open: boolean;
  table: TableRecord | null;
  busy?: boolean;
  onClose: () => void;
  onSave: (payload: { number: string; capacity: number; zone?: string }) => void;
};

export function EditTableModal({ open, table, busy = false, onClose, onSave }: EditTableModalProps) {
  const [number, setNumber] = useState("");
  const [capacity, setCapacity] = useState("4");
  const [zone, setZone] = useState("");

  useEffect(() => {
    if (!table) return;
    setNumber(table.number);
    setCapacity(String(table.capacity));
    setZone(table.zone ?? "");
  }, [table]);

  return (
    <Modal
      open={open}
      title={table ? `Edit Table ${table.number}` : "Edit Table"}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-secondary btn-sm" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button
            className="btn btn-primary btn-sm"
            disabled={busy || !number.trim() || Number.isNaN(Number.parseInt(capacity, 10))}
            onClick={() => onSave({ number: number.trim(), capacity: Number.parseInt(capacity, 10), zone: zone.trim() || undefined })}
          >
            {busy ? "Saving..." : "Save"}
          </button>
        </>
      }
    >
      <div className="grid gap-3">
        <label className="field">
          <span className="field-label">Table Number</span>
          <input className="input" value={number} onChange={(event) => setNumber(event.target.value)} />
        </label>
        <label className="field">
          <span className="field-label">Capacity</span>
          <input className="input" type="number" min={1} value={capacity} onChange={(event) => setCapacity(event.target.value)} />
        </label>
        <label className="field">
          <span className="field-label">Zone</span>
          <input className="input" value={zone} onChange={(event) => setZone(event.target.value)} />
        </label>
      </div>
    </Modal>
  );
}


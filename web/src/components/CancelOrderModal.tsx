"use client";

import { useEffect, useState } from "react";
import { Modal } from "@/components/Modal";

type CancelOrderModalProps = {
  open: boolean;
  orderLabel?: string;
  busy?: boolean;
  onClose: () => void;
  onConfirm: (reason?: string) => void;
};

export function CancelOrderModal({ open, orderLabel, busy = false, onClose, onConfirm }: CancelOrderModalProps) {
  const [reason, setReason] = useState("");

  useEffect(() => {
    if (!open) setReason("");
  }, [open]);

  return (
    <Modal
      open={open}
      title={`Cancel ${orderLabel ?? "Order"}`}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-secondary btn-sm" onClick={onClose} disabled={busy}>
            Keep Order
          </button>
          <button className="btn btn-danger btn-sm" onClick={() => onConfirm(reason.trim() || undefined)} disabled={busy}>
            {busy ? "Cancelling..." : "Cancel Order"}
          </button>
        </>
      }
    >
      <label className="field">
        <span className="field-label">Reason (optional)</span>
        <textarea
          className="textarea"
          placeholder="Why is this order being cancelled?"
          value={reason}
          onChange={(event) => setReason(event.target.value)}
        />
      </label>
    </Modal>
  );
}


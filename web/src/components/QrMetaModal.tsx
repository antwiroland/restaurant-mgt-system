"use client";

import { useState } from "react";
import { Modal } from "@/components/Modal";
import type { TableQrRecord } from "@/lib/apiClient";

type QrMetaModalProps = {
  open: boolean;
  qr: TableQrRecord | null;
  loading?: boolean;
  onClose: () => void;
};

export function QrMetaModal({ open, qr, loading = false, onClose }: QrMetaModalProps) {
  const [copied, setCopied] = useState<"" | "token" | "url">("");

  async function copyValue(kind: "token" | "url", value: string) {
    await navigator.clipboard.writeText(value);
    setCopied(kind);
    window.setTimeout(() => setCopied(""), 1400);
  }

  return (
    <Modal
      open={open}
      title={qr ? `QR Metadata - Table ${qr.tableNumber}` : "QR Metadata"}
      onClose={onClose}
      footer={<button className="btn btn-secondary btn-sm" onClick={onClose}>Close</button>}
    >
      {loading ? (
        <p className="text-sm text-ink-soft">Loading QR metadata...</p>
      ) : qr ? (
        <div className="grid gap-3 text-sm">
          <div className="grid gap-1">
            <p className="field-label">Token</p>
            <div className="flex items-center gap-2">
              <code className="input grow overflow-x-auto">{qr.qrToken}</code>
              <button className="btn btn-secondary btn-sm" onClick={() => void copyValue("token", qr.qrToken)}>
                {copied === "token" ? "Copied" : "Copy"}
              </button>
            </div>
          </div>
          <div className="grid gap-1">
            <p className="field-label">Scan URL</p>
            <div className="flex items-center gap-2">
              <code className="input grow overflow-x-auto">{qr.qrUrl}</code>
              <button className="btn btn-secondary btn-sm" onClick={() => void copyValue("url", qr.qrUrl)}>
                {copied === "url" ? "Copied" : "Copy"}
              </button>
            </div>
          </div>
        </div>
      ) : (
        <p className="text-sm text-ink-soft">No QR metadata available.</p>
      )}
    </Modal>
  );
}


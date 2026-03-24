"use client";

import { useState } from "react";
import { verifyPin } from "@/features/financial/pin";

type Props = {
  onApply: () => void;
  expectedPin?: string;
  locked?: boolean;
};

export function PinModal({ onApply, expectedPin = "1234", locked = false }: Props) {
  const [open, setOpen] = useState(false);
  const [pin, setPin] = useState("");
  const [message, setMessage] = useState("");

  function submit() {
    const result = verifyPin(pin, expectedPin, locked);
    if (result === "locked") {
      setMessage("PIN locked");
      return;
    }
    if (result === "error") {
      setMessage("Invalid PIN");
      return;
    }
    setMessage("");
    onApply();
    setOpen(false);
  }

  return (
    <div>
      <button onClick={() => setOpen(true)}>Open PIN Modal</button>
      {open ? (
        <div role="dialog" aria-label="pin-modal">
          <input aria-label="pin-input" value={pin} onChange={(e) => setPin(e.target.value)} />
          <button onClick={submit}>Submit PIN</button>
          {message ? <p>{message}</p> : null}
        </div>
      ) : null}
    </div>
  );
}

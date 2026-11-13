"use client";

import { useEffect, useState } from "react";
import { Icon } from "@/components/Icon";
import { installSoundUnlock, isSoundMuted, onSoundMutedChange, playAlertSound, setSoundMuted } from "@/lib/alertSound";

/** Header button: turns alert sounds on or off on this device. Turning them on plays a sample chime. */
export function SoundToggle({ dark = false }: { dark?: boolean }) {
  const [muted, setMuted] = useState(false);

  useEffect(() => {
    installSoundUnlock();
    const sync = window.setTimeout(() => setMuted(isSoundMuted()), 0);
    const off = onSoundMutedChange(setMuted);
    return () => {
      window.clearTimeout(sync);
      off();
    };
  }, []);

  function toggle() {
    const next = !muted;
    setSoundMuted(next);
    if (!next) playAlertSound("request", { force: true });
  }

  return (
    <button
      type="button"
      className={`btn btn-icon btn-sm ${dark ? "btn-ghost text-night-ink hover:bg-night-card" : "btn-ghost"}`}
      aria-label={muted ? "Alert sounds off. Turn on" : "Alert sounds on. Turn off"}
      aria-pressed={!muted}
      title={muted ? "Alert sounds off" : "Alert sounds on"}
      onClick={toggle}
    >
      <Icon name={muted ? "volumeOff" : "volume"} />
    </button>
  );
}

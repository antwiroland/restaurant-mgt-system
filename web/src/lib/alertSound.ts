"use client";

/**
 * Short alert chimes for staff screens, generated with the Web Audio API (no sound files to load).
 *
 *  - "request": a guest called a waiter or asked for the bill (two-note ding)
 *  - "warning": a table served and unpaid, low stock, a low rating (three quick beeps)
 *  - "order":   a new ticket on the kitchen display (bright single ding)
 *
 * Browsers only allow sound after the person has interacted with the page, so the audio context is unlocked on the
 * first click, tap or key press. Sound can be muted per device (kept in localStorage).
 */

export type AlertSound = "request" | "warning" | "order";

const MUTE_KEY = "rm-alert-sound-muted";
const MIN_GAP_MS = 1200; // never stack chimes on top of each other
const listeners = new Set<(muted: boolean) => void>();

let context: AudioContext | null = null;
let lastPlayed = 0;
let unlockInstalled = false;

function audioContext(): AudioContext | null {
  if (typeof window === "undefined") return null;
  if (!context) {
    const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctor) return null;
    try {
      context = new Ctor();
    } catch {
      return null;
    }
  }
  return context;
}

/** Call once on staff screens: resumes the audio context on the first user gesture (browser autoplay rules). */
export function installSoundUnlock() {
  if (unlockInstalled || typeof window === "undefined") return;
  unlockInstalled = true;
  const unlock = () => {
    const ctx = audioContext();
    if (ctx && ctx.state === "suspended") void ctx.resume();
    if (ctx && ctx.state === "running") {
      window.removeEventListener("pointerdown", unlock);
      window.removeEventListener("keydown", unlock);
    }
  };
  window.addEventListener("pointerdown", unlock);
  window.addEventListener("keydown", unlock);
}

export function isSoundMuted(): boolean {
  try {
    return window.localStorage.getItem(MUTE_KEY) === "1";
  } catch {
    return false;
  }
}

export function setSoundMuted(muted: boolean) {
  try {
    if (muted) window.localStorage.setItem(MUTE_KEY, "1");
    else window.localStorage.removeItem(MUTE_KEY);
  } catch {
    // Private mode: the choice lasts for this page only.
  }
  listeners.forEach((listener) => listener(muted));
}

export function onSoundMutedChange(listener: (muted: boolean) => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

// [frequency Hz, start offset s, duration s]
const PATTERNS: Record<AlertSound, { wave: OscillatorType; volume: number; notes: [number, number, number][] }> = {
  request: { wave: "sine", volume: 0.35, notes: [[880, 0, 0.35], [1318.5, 0.18, 0.5]] },
  warning: { wave: "triangle", volume: 0.4, notes: [[988, 0, 0.14], [988, 0.2, 0.14], [1244.5, 0.4, 0.28]] },
  order: { wave: "sine", volume: 0.35, notes: [[1567.98, 0, 0.12], [2093, 0.1, 0.45]] },
};

/** Plays a chime unless sound is muted or one just played. Returns whether it played. */
export function playAlertSound(kind: AlertSound, { force = false }: { force?: boolean } = {}): boolean {
  if (!force && isSoundMuted()) return false;
  const now = Date.now();
  if (!force && now - lastPlayed < MIN_GAP_MS) return false;
  const ctx = audioContext();
  if (!ctx) return false;
  if (ctx.state === "suspended") void ctx.resume();
  lastPlayed = now;

  const pattern = PATTERNS[kind];
  const start = ctx.currentTime + 0.02;
  for (const [frequency, offset, duration] of pattern.notes) {
    const oscillator = ctx.createOscillator();
    const gain = ctx.createGain();
    oscillator.type = pattern.wave;
    oscillator.frequency.value = frequency;
    // quick attack, smooth decay: a bell-like ding rather than a click
    gain.gain.setValueAtTime(0.0001, start + offset);
    gain.gain.exponentialRampToValueAtTime(pattern.volume, start + offset + 0.015);
    gain.gain.exponentialRampToValueAtTime(0.0001, start + offset + duration);
    oscillator.connect(gain).connect(ctx.destination);
    oscillator.start(start + offset);
    oscillator.stop(start + offset + duration + 0.05);
  }
  return true;
}

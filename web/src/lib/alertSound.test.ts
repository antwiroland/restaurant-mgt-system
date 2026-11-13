import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// A minimal fake of the Web Audio API: counts the notes started.
let started = 0;
class FakeContext {
  state = "running";
  currentTime = 0;
  destination = {};
  resume = vi.fn();
  createOscillator() {
    return { type: "sine", frequency: { value: 0 }, connect: (node: unknown) => node, start: () => { started++; }, stop: () => {} };
  }
  createGain() {
    return {
      gain: { setValueAtTime: () => {}, exponentialRampToValueAtTime: () => {} },
      connect: (node: unknown) => node,
    };
  }
}

describe("alert sounds", () => {
  beforeEach(() => {
    vi.resetModules();
    started = 0;
    window.localStorage.clear();
    (window as unknown as { AudioContext: unknown }).AudioContext = FakeContext;
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-11-12T12:00:00Z"));
  });
  afterEach(() => vi.useRealTimers());

  it("plays a chime, but not twice in a row and not when muted", async () => {
    const sound = await import("./alertSound");
    expect(sound.playAlertSound("request")).toBe(true);
    expect(started).toBe(2); // two-note ding
    expect(sound.playAlertSound("warning")).toBe(false); // too soon after the last one

    vi.setSystemTime(new Date("2026-11-12T12:00:05Z"));
    sound.setSoundMuted(true);
    expect(sound.isSoundMuted()).toBe(true);
    expect(sound.playAlertSound("warning")).toBe(false);

    sound.setSoundMuted(false);
    expect(sound.playAlertSound("warning")).toBe(true);
    expect(started).toBe(5); // + three beeps
  });

  it("tells listeners when sound is muted or unmuted", async () => {
    const sound = await import("./alertSound");
    const seen: boolean[] = [];
    const off = sound.onSoundMutedChange((muted) => seen.push(muted));
    sound.setSoundMuted(true);
    sound.setSoundMuted(false);
    off();
    sound.setSoundMuted(true);
    expect(seen).toEqual([true, false]);
  });
});

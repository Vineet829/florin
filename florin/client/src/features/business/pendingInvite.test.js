import { afterEach, describe, expect, it, vi } from "vitest";
import {
  forgetInvite,
  pendingInvitePath,
  rememberInvite,
} from "./pendingInvite";

describe("pendingInvite", () => {
  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
  });

  it("returns to the pending invitation", () => {
    expect(pendingInvitePath()).toBeNull();
    rememberInvite("abc_DEF-123");
    expect(pendingInvitePath()).toBe("/invite/abc_DEF-123");
    forgetInvite();
    expect(pendingInvitePath()).toBeNull();
  });

  it("forgets expired invitations", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-09-01T10:00:00Z"));
    rememberInvite("token");
    vi.setSystemTime(new Date("2026-09-08T09:59:00Z"));
    expect(pendingInvitePath()).toBe("/invite/token");
    vi.setSystemTime(new Date("2026-09-08T10:00:00Z"));
    expect(pendingInvitePath()).toBeNull();
  });

  it("ignores unreadable entries", () => {
    localStorage.setItem("pendingInvite", "{not json");
    expect(pendingInvitePath()).toBeNull();
    localStorage.setItem("pendingInvite", JSON.stringify({ token: 42 }));
    expect(pendingInvitePath()).toBeNull();
  });
});

import { afterEach, beforeEach, expect, it, vi } from "vitest";

beforeEach(() => {
  vi.resetModules();
  vi.stubEnv("DEV", false);
  vi.stubEnv("PROD", true);
});
afterEach(() => {
  vi.unstubAllEnvs();
});

const apiBase = async () => (await import("./apiBase")).API_BASE;

it("defaults to same origin", async () => {
  vi.stubEnv("VITE_API_URL", undefined);

  expect(await apiBase()).toBe("/api/v1");
});

it("uses the configured API URL", async () => {
  vi.stubEnv("VITE_API_URL", "https://api.example.com/api/v1");

  expect(await apiBase()).toBe("https://api.example.com/api/v1");
});

import { beforeEach, expect, it, vi } from "vitest";
import reducer, { logIn, restoreSession } from "./authSlice";

beforeEach(() => localStorage.clear());

const encode = (value) =>
  btoa(JSON.stringify(value))
    .replace(/=+$/, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
const savedToken = `${encode({ alg: "HS256" })}.${encode({
  sub: "u1",
  roles: ["User"],
  exp: 4102444800,
})}.signature`;

const storeWith = (auth) => ({
  getState: () => ({ auth }),
  dispatch: vi.fn((action) => {
    auth = reducer(auth, action);
  }),
});

it("keeps token only in memory", () => {
  const state = reducer(
    undefined,
    logIn({ username: "asha", firstName: "Asha", accessToken: "token" }),
  );
  expect(state.user.accessToken).toBe("token");
  expect(JSON.parse(localStorage.getItem("user"))).toEqual({
    username: "asha",
    firstName: "Asha",
  });
});

it("reload starts in restoring state", async () => {
  localStorage.setItem(
    "user",
    JSON.stringify({ username: "asha", accessToken: savedToken }),
  );
  vi.resetModules();
  const { default: fresh } = await import("./authSlice");
  expect(fresh(undefined, { type: "@@init" })).toEqual({
    user: null,
    restoring: true,
  });
  expect(localStorage.getItem("user")).not.toContain(savedToken);
});

it("restores token via cookie", async () => {
  localStorage.setItem("user", JSON.stringify({ username: "asha" }));
  const store = storeWith({ user: null, restoring: true });
  await restoreSession(store, async () => ({
    data: { username: "asha", accessToken: "renewed" },
  }));
  expect(store.getState().auth).toEqual({
    user: { username: "asha", accessToken: "renewed" },
    restoring: false,
  });
  expect(localStorage.getItem("user")).not.toContain("renewed");
});

it.each([
  { status: 401, data: null },
  { status: 403, data: null },
  { status: "PARSING_ERROR", originalStatus: 403, data: "" },
])("signs out on refused cookie: %j", async (error) => {
  localStorage.setItem("user", JSON.stringify({ username: "asha" }));
  const store = storeWith({ user: null, restoring: true });
  await restoreSession(store, async () => ({ error }));
  expect(store.getState().auth).toEqual({ user: null, restoring: false });
  expect(localStorage.getItem("user")).toBeNull();
});

it.each([
  { status: "FETCH_ERROR", error: "TypeError: Failed to fetch" },
  { status: "PARSING_ERROR", originalStatus: 502, data: "<html>" },
  { status: 503, data: null },
])("keeps user on server error: %j", async (error) => {
  localStorage.setItem("user", JSON.stringify({ username: "asha" }));
  const store = storeWith({ user: null, restoring: true });
  await restoreSession(store, async () => ({ error }));
  expect(store.getState().auth).toEqual({ user: null, restoring: false });
  expect(JSON.parse(localStorage.getItem("user"))).toEqual({
    username: "asha",
  });
});

it("finishes restoring on thrown request", async () => {
  const store = storeWith({ user: null, restoring: true });
  await restoreSession(store, async () => {
    throw new Error("boom");
  });
  expect(store.getState().auth.restoring).toBe(false);
});

it("skips restore when signed out", async () => {
  const refresh = vi.fn();
  await restoreSession(storeWith({ user: null, restoring: false }), refresh);
  expect(refresh).not.toHaveBeenCalled();
});

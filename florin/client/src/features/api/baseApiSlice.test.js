import { beforeEach, expect, it, vi } from "vitest";
import { fetchBaseQuery } from "@reduxjs/toolkit/query/react";
import { baseApiSlice } from "./baseApiSlice";
import { logIn, logOut } from "../auth/authSlice";
import { businessChanged } from "../business/businessSlice";

vi.mock("@reduxjs/toolkit/query/react", () => ({
  fetchBaseQuery: vi.fn(() => vi.fn()),
  createApi: (options) => ({
    ...options,
    util: { resetApiState: () => ({ type: "api/resetApiState" }) },
  }),
}));

const request = fetchBaseQuery.mock.results[0].value;
const pathOf = (args) => (typeof args === "string" ? args : args.url);
let api;

beforeEach(() => {
  request.mockReset();
  let user = { accessToken: "expired" };
  api = {
    getState: () => ({ auth: { user } }),
    dispatch: vi.fn((action) => {
      if (action.type === logIn.type) user = action.payload;
      if (action.type === logOut.type) user = null;
    }),
  };
});

it.each([
  { status: 403, data: null },
  { status: "PARSING_ERROR", originalStatus: 403 },
  { status: "PARSING_ERROR", originalStatus: 403, data: "" },
])("refreshes and retries: %j", async (error) => {
  request
    .mockResolvedValueOnce({ error })
    .mockResolvedValueOnce({ data: { accessToken: "renewed" } })
    .mockResolvedValueOnce({ data: { customers: [] } });

  const result = await baseApiSlice.baseQuery("/customer/all", api, {});

  expect(result).toEqual({ data: { customers: [] } });
  expect(api.getState().auth.user.accessToken).toBe("renewed");
  expect(request.mock.calls.map(([args]) => pathOf(args))).toEqual([
    "/customer/all",
    "/auth/new_access_token",
    "/customer/all",
  ]);
});

it("refresh uses keepalive", async () => {
  request
    .mockResolvedValueOnce({ error: { status: 403 } })
    .mockResolvedValueOnce({ data: { accessToken: "renewed" } })
    .mockResolvedValueOnce({ data: { customers: [] } });

  await baseApiSlice.baseQuery("/customer/all", api, {});

  expect(request.mock.calls[1][0]).toEqual({
    url: "/auth/new_access_token",
    keepalive: true,
  });
});

it("shares one refresh between requests", async () => {
  request.mockImplementation(async (args) => {
    const path = pathOf(args);
    if (path === "/auth/new_access_token") {
      return { data: { accessToken: "renewed" } };
    }
    return api.getState().auth.user.accessToken === "expired"
      ? { error: { status: 403 } }
      : { data: { resource: path } };
  });

  const results = await Promise.all([
    baseApiSlice.baseQuery("/customer/all", api, {}),
    baseApiSlice.baseQuery("/document/all", api, {}),
  ]);

  expect(results).toEqual([
    { data: { resource: "/customer/all" } },
    { data: { resource: "/document/all" } },
  ]);
  expect(
    request.mock.calls.filter(
      ([args]) => pathOf(args) === "/auth/new_access_token",
    ),
  ).toHaveLength(1);
  expect(api.dispatch).toHaveBeenCalledTimes(1);
});

it("clears session on rejected refresh", async () => {
  request
    .mockResolvedValueOnce({ error: { status: 403 } })
    .mockResolvedValueOnce({ error: { status: 401 } });

  const result = await baseApiSlice.baseQuery("/customer/all", api, {});

  expect(result.error.status).toBe(403);
  expect(api.getState().auth.user).toBeNull();
  expect(api.dispatch).toHaveBeenCalledWith({ type: "api/resetApiState" });
  expect(request).toHaveBeenCalledTimes(2);
});

it.each([
  { status: "FETCH_ERROR", error: "TypeError: Failed to fetch" },
  { status: 502, data: "" },
  { status: 503, data: { message: "Try again" } },
])("keeps session on server error: %j", async (error) => {
  request
    .mockResolvedValueOnce({ error: { status: 403 } })
    .mockResolvedValueOnce({ error });

  const result = await baseApiSlice.baseQuery("/customer/all", api, {});

  expect(result.error.status).toBe(403);
  expect(api.getState().auth.user).toEqual({ accessToken: "expired" });
  expect(api.dispatch).not.toHaveBeenCalled();
});

it("returns auth error without retry", async () => {
  request.mockResolvedValue({
    error: { status: 403, data: { message: "Login failed" } },
  });

  await baseApiSlice.baseQuery({ url: "/auth/login", method: "POST" }, api, {});

  expect(request).toHaveBeenCalledTimes(1);
  expect(api.dispatch).not.toHaveBeenCalled();
});

it("passes refusals through unchanged", async () => {
  const refusal = {
    status: 403,
    data: {
      success: false,
      message: "You are not authorized to view this document. It's not yours",
    },
  };
  request.mockResolvedValue({ error: refusal });

  const result = await baseApiSlice.baseQuery(
    "/document/someone-elses",
    api,
    {},
  );

  expect(result).toEqual({ error: refusal });
  expect(request).toHaveBeenCalledTimes(1);
  expect(api.dispatch).not.toHaveBeenCalled();
  expect(api.getState().auth.user.accessToken).toBe("expired");
});

it.each([
  [
    "6f1c2d3e-0000-4000-8000-000000000001",
    "6f1c2d3e-0000-4000-8000-000000000001",
  ],
  ["none", null],
])("switches to server's business: %s", async (header, activeId) => {
  request.mockResolvedValue({
    error: { status: 409, data: { message: "Switched" } },
    meta: {
      response: { headers: new Headers({ "X-Active-Business": header }) },
    },
  });

  await baseApiSlice.baseQuery("/customer/all", api, {});

  expect(api.dispatch).toHaveBeenCalledWith(businessChanged(activeId));
});

it("ignores other conflicts", async () => {
  request.mockResolvedValue({
    error: { status: 409, data: { message: "That conflicts" } },
    meta: { response: { headers: new Headers() } },
  });

  await baseApiSlice.baseQuery("/customer/create", api, {});

  expect(api.dispatch).not.toHaveBeenCalled();
});

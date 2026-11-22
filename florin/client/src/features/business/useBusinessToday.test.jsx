import { afterAll, afterEach, beforeAll, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import useBusinessToday, { todayIn } from "./useBusinessToday";
import { parseDate } from "../../utils/dates";

const active = vi.hoisted(() => ({ business: undefined }));
vi.mock("./permissions", () => ({
  useBusiness: () => ({ business: active.business }),
}));

window.IS_REACT_ACT_ENVIRONMENT = true;

const zone = process.env.TZ;
beforeAll(() => {
  process.env.TZ = "Asia/Kolkata";
});
afterAll(() => {
  if (zone === undefined) delete process.env.TZ;
  else process.env.TZ = zone;
});
afterEach(() => {
  vi.useRealTimers();
  active.business = undefined;
});

const at = (moment) => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date(moment));
};
const day = (date) => [date.getFullYear(), date.getMonth() + 1, date.getDate()];

it("gives today in the business zone", () => {
  at("2026-10-08T05:00:00+05:30");
  const today = todayIn("America/Los_Angeles");
  expect(day(today)).toEqual([2026, 10, 7]);
  expect(today.getTime()).toBe(parseDate("2026-10-07").getTime());
  expect(day(todayIn("Asia/Kolkata"))).toEqual([2026, 10, 8]);
});

it("falls back for unknown zones", () => {
  at("2026-10-08T05:00:00+05:30");
  expect(day(todayIn(undefined))).toEqual([2026, 10, 8]);
  expect(day(todayIn(""))).toEqual([2026, 10, 8]);
  expect(day(todayIn("Nowhere/Town"))).toEqual([2026, 10, 8]);
});

const render = () => {
  const seen = [];
  const Probe = () => {
    seen.push(day(useBusinessToday()));
    return null;
  };
  const root = createRoot(document.createElement("div"));
  act(() => root.render(<Probe />));
  return {
    seen,
    again: () => act(() => root.render(<Probe />)),
    unmount: () => act(() => root.unmount()),
  };
};

it("turns over at business midnight", () => {
  active.business = { _id: "b-1", timeZone: "America/Los_Angeles" };
  at("2026-10-08T06:59:00Z");
  const page = render();
  expect(page.seen.at(-1)).toEqual([2026, 10, 7]);
  vi.setSystemTime(new Date("2026-10-08T07:00:00Z"));
  page.again();
  expect(page.seen.at(-1)).toEqual([2026, 10, 8]);
  page.unmount();
});

it("uses browser day without a zone", () => {
  active.business = { _id: "b-1" };
  at("2026-10-08T05:00:00+05:30");
  const page = render();
  expect(page.seen.at(-1)).toEqual([2026, 10, 8]);
  page.unmount();
});

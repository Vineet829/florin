import { afterEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import ScheduleDialog from "./ScheduleDialog";

const idle = () => [vi.fn(), { isLoading: false }];
vi.mock("../recurringApiSlice", () => ({
  useCreateScheduleMutation: () => idle(),
  useUpdateScheduleMutation: () => idle(),
}));
vi.mock("../../../components/workspace/Dialog", () => ({
  Dialog: ({ open, children }) => (open ? children : null),
}));
const active = vi.hoisted(() => ({ business: undefined }));
vi.mock("../../business/permissions", () => ({
  useBusiness: () => ({ business: active.business }),
}));

window.IS_REACT_ACT_ENVIRONMENT = true;

afterEach(() => {
  vi.useRealTimers();
  active.business = undefined;
});

const nextInvoice = (schedule) => {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(
      <ScheduleDialog
        open
        onClose={() => {}}
        documentId="d1"
        documentNumber="INV-1014"
        schedule={schedule}
      />,
    );
  });
  const { value, min } = container.querySelector('input[type="date"]');
  act(() => root.unmount());
  return { value, min };
};

it("starts a month from today", () => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date(2026, 9, 8, 12));
  expect(nextInvoice()).toEqual({ value: "2026-11-08", min: "2026-10-08" });
});

it("uses the business calendar", () => {
  const zone = process.env.TZ;
  process.env.TZ = "Asia/Kolkata";
  try {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-10-08T05:00:00+05:30"));
    active.business = { _id: "b-1", timeZone: "America/Los_Angeles" };
    expect(nextInvoice()).toEqual({ value: "2026-11-07", min: "2026-10-07" });
    expect(
      nextInvoice({
        _id: "s1",
        frequency: "MONTHLY",
        nextRun: "2026-10-07",
        issueAutomatically: true,
        emailAutomatically: false,
      }),
    ).toEqual({ value: "2026-10-07", min: "2026-10-07" });
  } finally {
    if (zone === undefined) delete process.env.TZ;
    else process.env.TZ = zone;
  }
});

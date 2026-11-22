import { afterEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import PaymentForm from "./PaymentForm";
import { dateInput } from "../../../components/workspace/Workspace";

vi.mock("../documentsApiSlice", () => ({
  useCreatePaymentMutation: () => [vi.fn(), { isLoading: false }],
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

const datePaid = () => {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(
      <PaymentForm
        document={{ _id: "1", currency: "USD", ledger: { balanceDue: 100 } }}
      />,
    );
  });
  const { value, max } = container.querySelector('input[type="date"]');
  act(() => root.unmount());
  return { value, max };
};

it("caps payment date at today", () => {
  expect(datePaid()).toEqual({ value: dateInput(), max: dateInput() });
});

it("uses the business calendar", () => {
  const zone = process.env.TZ;
  process.env.TZ = "Asia/Kolkata";
  try {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-10-08T05:00:00+05:30"));
    active.business = { _id: "b-1", timeZone: "America/Los_Angeles" };
    expect(datePaid()).toEqual({ value: "2026-10-07", max: "2026-10-07" });
  } finally {
    if (zone === undefined) delete process.env.TZ;
    else process.env.TZ = zone;
  }
});

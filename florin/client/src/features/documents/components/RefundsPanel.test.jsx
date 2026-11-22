import { afterEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import RefundsPanel from "./RefundsPanel";
import { dateInput } from "../../../components/workspace/Workspace";

const idle = () => [vi.fn(), { isLoading: false }];
vi.mock("../documentsApiSlice", () => ({
  useCreateRefundMutation: () => idle(),
  useDeleteRefundMutation: () => idle(),
  useUpdateRefundMutation: () => idle(),
}));
vi.mock("../../../components/workspace/Dialog", () => ({
  Dialog: ({ children }) => children,
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

const dateFields = () => {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(
      <RefundsPanel
        document={{
          _id: "1",
          currency: "USD",
          ledger: { refundDue: 30 },
          refunds: [
            {
              _id: "r1",
              dateRefunded: "2026-09-01",
              amount: 10,
              paymentMethod: "Cash",
            },
          ],
        }}
      />,
    );
  });
  act(() => {
    container
      .querySelector('button[aria-label^="Edit the refund"]')
      .dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
  const fields = [...container.querySelectorAll('input[type="date"]')].map(
    ({ value, max }) => ({ value, max }),
  );
  act(() => root.unmount());
  return fields;
};

it("refund date defaults to today", () => {
  expect(dateFields()).toEqual([
    { value: dateInput(), max: dateInput() },
    { value: "2026-09-01", max: dateInput() },
  ]);
});

it("uses the business calendar", () => {
  const zone = process.env.TZ;
  process.env.TZ = "Asia/Kolkata";
  try {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-10-08T05:00:00+05:30"));
    active.business = { _id: "b-1", timeZone: "America/Los_Angeles" };
    expect(dateFields()).toEqual([
      { value: "2026-10-07", max: "2026-10-07" },
      { value: "2026-09-01", max: "2026-10-07" },
    ]);
  } finally {
    if (zone === undefined) delete process.env.TZ;
    else process.env.TZ = zone;
  }
});

import { afterEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import ExpenseDialog from "./ExpenseDialog";
import { dateInput } from "../../../components/workspace/Workspace";

const idle = () => [vi.fn(), { isLoading: false }];
vi.mock("../expensesApiSlice", () => ({
  useCreateExpenseMutation: () => idle(),
  useGetExpenseCategoriesQuery: () => ({}),
  useUpdateExpenseMutation: () => idle(),
}));
vi.mock("../../settings/settingsApiSlice", () => ({
  useGetSettingsQuery: () => ({
    data: { settings: { defaultCurrency: "USD", baseCurrency: "USD" } },
  }),
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

const datePaid = () => {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(<ExpenseDialog open onClose={() => {}} />);
  });
  const { value, max } = container.querySelector('input[type="date"]');
  act(() => root.unmount());
  return { value, max };
};

it("expense date defaults to today", () => {
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

it("accepts up to 10 decimals", () => {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(
      <ExpenseDialog
        open
        expense={{
          _id: "e1",
          date: "2026-01-15",
          category: "Travel",
          total: 10,
          currency: "EUR",
        }}
        onClose={() => {}}
      />,
    );
  });
  const label = [...container.querySelectorAll("label")].find((element) =>
    element.textContent.startsWith("Exchange rate"),
  );
  const rate = container.querySelector(`[id="${label.htmlFor}"]`);
  rate.value = "0.012048192771084338";
  expect(rate.validity.stepMismatch).toBe(true);
  rate.value = "0.0120481928";
  expect(rate.validity.valid).toBe(true);
  act(() => root.unmount());
});

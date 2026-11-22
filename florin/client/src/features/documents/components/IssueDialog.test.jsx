import { afterEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import IssueDialog from "./IssueDialog";
import { useGetSettingsQuery } from "../../settings/settingsApiSlice";

vi.mock("../documentsApiSlice", () => ({
  useIssueDocMutation: () => [vi.fn(), { isLoading: false }],
}));
vi.mock("../../settings/settingsApiSlice", () => ({
  useGetSettingsQuery: vi.fn(),
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

const inRegime = (taxRegime) =>
  useGetSettingsQuery.mockReturnValue({
    data: {
      settings: { paymentTermsDays: 15, baseCurrency: "INR", taxRegime },
    },
  });

const render = (draft) => {
  const container = document.createElement("div");
  act(() => {
    createRoot(container).render(
      <IssueDialog
        document={{ _id: "d1", currency: "USD", ...draft }}
        open
        onClose={() => {}}
        onIssued={() => {}}
        canEmail
      />,
    );
  });
  return container;
};

const rateField = (container) => {
  const label = [...container.querySelectorAll("label")].find((element) =>
    element.textContent.startsWith("Exchange rate"),
  );
  if (!label) return null;
  const input = [...container.querySelectorAll("input")].find(
    (element) => element.id === label.htmlFor,
  );
  const hint = [...container.querySelectorAll("p")].find(
    (element) => element.id === `${input.id}-hint`,
  );
  return { input, hint: hint?.textContent };
};

it("requires rate for foreign GST", () => {
  inRegime("GST_INDIA");
  for (const documentType of ["Invoice", "Receipt"]) {
    const { input, hint } = rateField(render({ documentType }));
    expect(input.required).toBe(true);
    expect(hint).toMatch(/^Needed/);
  }
});

it("rate optional for quotations", () => {
  inRegime("GST_INDIA");
  const quotation = rateField(render({ documentType: "Quotation" }));
  expect(quotation.input.required).toBe(false);
  expect(quotation.hint).toMatch(/^Optional/);
  inRegime("STANDARD");
  const invoice = rateField(render({ documentType: "Invoice" }));
  expect(invoice.input.required).toBe(false);
  expect(invoice.hint).toMatch(/^Optional/);
});

it("credit note keeps invoice rate", () => {
  inRegime("GST_INDIA");
  expect(
    rateField(
      render({
        documentType: "Credit Note",
        exchangeRate: "83.25",
        exchangeBase: "INR",
      }),
    ),
  ).toBeNull();
});

it("dates draft with payment terms", () => {
  inRegime("STANDARD");
  const zone = process.env.TZ;
  process.env.TZ = "Asia/Kolkata";
  try {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-10-08T05:00:00+05:30"));
    active.business = { _id: "b-1", timeZone: "America/Los_Angeles" };
    const [issued, due] = render({ documentType: "Invoice" }).querySelectorAll(
      'input[type="date"]',
    );
    expect([issued.value, issued.max]).toEqual(["2026-10-07", "2026-10-07"]);
    expect([due.value, due.min]).toEqual(["2026-10-22", "2026-10-07"]);
  } finally {
    if (zone === undefined) delete process.env.TZ;
    else process.env.TZ = zone;
  }
});

it("accepts up to 10 decimals", () => {
  inRegime("STANDARD");
  const { input } = rateField(render({ documentType: "Invoice" }));
  input.value = "0.012048192771084338";
  expect(input.validity.stepMismatch).toBe(true);
  input.value = "0.0120481928";
  expect(input.validity.valid).toBe(true);
  input.value = "83.2500000000000";
  expect(input.validity.valid).toBe(true);
});

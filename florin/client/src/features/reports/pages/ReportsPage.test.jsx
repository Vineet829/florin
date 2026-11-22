import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { MemoryRouter } from "react-router-dom";
import ReportsPage from "./ReportsPage";
import {
  useGetProfitReportQuery,
  useGetReportCsvMutation,
  useGetReportQuery,
  useGetTaxReportQuery,
  useGetTdsReportQuery,
} from "../reportsApiSlice";

vi.mock("../reportsApiSlice", () => ({
  useGetReportQuery: vi.fn(),
  useGetTaxReportQuery: vi.fn(),
  useGetTdsReportQuery: vi.fn(),
  useGetProfitReportQuery: vi.fn(),
  useGetReportCsvMutation: vi.fn(),
}));
vi.mock("../../settings/settingsApiSlice", () => ({
  useGetSettingsQuery: () => ({}),
}));
vi.mock("../../dashboard/components/DashboardShell", () => ({
  default: ({ children }) => children,
}));

window.IS_REACT_ACT_ENVIRONMENT = true;

const loaded = (data) => ({
  data,
  isLoading: false,
  isFetching: false,
  isError: false,
});

beforeEach(() => {
  useGetReportQuery.mockReturnValue(
    loaded({
      period: "12m",
      from: "2025-11-01",
      to: "2026-10-02",
      today: "2026-10-02",
      years: [],
      view: "currency",
      baseCurrency: "INR",
      currencies: [],
      notConverted: [],
    }),
  );
  useGetTaxReportQuery.mockReturnValue(loaded({ currencies: [] }));
  useGetTdsReportQuery.mockReturnValue(loaded({ currencies: [] }));
});

let root;
let container;
afterEach(() => {
  act(() => root.unmount());
  container.remove();
  delete Element.prototype.scrollIntoView;
});

const render = (path = "/reports") => {
  container = document.body.appendChild(document.createElement("div"));
  root = createRoot(container);
  act(() => {
    root.render(
      <MemoryRouter initialEntries={[path]}>
        <ReportsPage />
      </MemoryRouter>,
    );
  });
  return container;
};

const headings = (container) =>
  [...container.querySelectorAll("h2")].map((heading) => heading.textContent);

it("shows loss without invoices", () => {
  useGetProfitReportQuery.mockReturnValue(
    loaded({
      period: "12m",
      currencies: [
        {
          currency: "INR",
          income: 0,
          expenses: 1500,
          profit: -1500,
          months: [
            { month: "2026-09", income: 0, expenses: 1500, profit: -1500 },
          ],
          categories: [{ category: "Rent", expenses: 1500 }],
        },
      ],
    }),
  );
  const page = render();
  expect(headings(page)).toEqual([
    "Nothing to report for this period.",
    "Profit and loss",
  ]);
  expect(page.textContent).toContain("a loss of INR 1,500.00");
});

it("shows margins and expense shares", () => {
  withFigures();
  useGetProfitReportQuery.mockReturnValue(
    loaded({
      period: "12m",
      currencies: [
        {
          currency: "USD",
          income: 1000,
          expenses: 1600,
          profit: -600,
          months: [
            { month: "2026-08", income: 1000, expenses: 400, profit: 600 },
            { month: "2026-09", income: 0, expenses: 1200, profit: -1200 },
          ],
          categories: [
            { category: "Rent", expenses: 1200 },
            { category: "Software", expenses: 396 },
            { category: "Postage", expenses: 4 },
          ],
        },
      ],
    }),
  );
  const profit = render().querySelector("#profit");
  const [months, categories] = profit.querySelectorAll("table");
  const text = (table) =>
    [...table.querySelectorAll("tbody tr")].map((row) =>
      [...row.children].map((cell) => cell.textContent),
    );
  expect(
    [...months.querySelectorAll("th")].map((heading) => heading.textContent),
  ).toEqual([
    "Month",
    "Income",
    "Expenses",
    "Profit",
    "Margin",
    "Profit so far",
  ]);
  expect(text(months)).toEqual([
    [
      "August 2026",
      "USD1,000.00",
      "USD400.00",
      "USD600.00",
      "60%",
      "USD600.00",
    ],
    [
      "September 2026",
      "USD0.00",
      "USD1,200.00",
      "USD-1,200.00",
      "-",
      "USD-600.00",
    ],
  ]);
  expect(text(categories)).toEqual([
    ["Rent", "USD1,200.00", "75%"],
    ["Software", "USD396.00", "25%"],
    ["Postage", "USD4.00", "<1%"],
  ]);
  expect(
    [...categories.querySelectorAll('[aria-hidden="true"] > span')].map(
      (bar) => bar.style.width,
    ),
  ).toEqual(["100%", "33%", `${(4 / 1200) * 100}%`]);
  expect(months.parentElement.nextElementSibling).toBe(
    categories.parentElement,
  );
});

it("shows empty report", () => {
  useGetProfitReportQuery.mockReturnValue(
    loaded({ period: "12m", currencies: [] }),
  );
  expect(headings(render())).toEqual(["Nothing to report for this period."]);
});

const withFigures = () => {
  useGetReportCsvMutation.mockReturnValue([vi.fn(), { isLoading: false }]);
  useGetReportQuery.mockReturnValue(
    loaded({
      period: "12m",
      from: "2025-11-01",
      to: "2026-10-02",
      today: "2026-10-02",
      years: [],
      view: "currency",
      baseCurrency: "USD",
      notConverted: [],
      currencies: [
        {
          currency: "USD",
          billed: 1000,
          received: 600,
          outstanding: 400,
          overdue: 0,
          months: [{ month: "2026-09", billed: 1000, received: 600 }],
          ageing: [
            {
              key: "notDue",
              label: "Not due yet",
              invoices: 1,
              outstanding: 400,
            },
          ],
          methods: [{ method: "Bank Transfer", payments: 1, amount: 600 }],
          clients: [
            {
              customerId: "c1",
              name: "Northwind",
              documents: 1,
              billed: 1000,
              paid: 600,
              outstanding: 400,
            },
          ],
          otherClients: null,
        },
      ],
    }),
  );
  useGetProfitReportQuery.mockReturnValue(
    loaded({
      period: "12m",
      currencies: [
        {
          currency: "USD",
          income: 900,
          expenses: 0,
          profit: 900,
          months: [{ month: "2026-09", income: 900, expenses: 0, profit: 900 }],
          categories: [],
        },
      ],
    }),
  );
};

it("labels partial months by days", () => {
  const days = { period: "30d", from: "2026-09-08", to: "2026-10-07" };
  const halfTaxed = { taxable: 500, tax: 35, cgst: 0, sgst: 0, igst: 0 };
  withFigures();
  const { data: report } = useGetReportQuery();
  useGetReportQuery.mockReturnValue(
    loaded({
      ...report,
      ...days,
      currencies: report.currencies.map((entry) => ({
        ...entry,
        months: [
          { month: "2026-09", billed: 600, received: 300 },
          { month: "2026-10", billed: 400, received: 300 },
        ],
      })),
    }),
  );
  useGetProfitReportQuery.mockReturnValue(
    loaded({
      ...days,
      currencies: [
        {
          currency: "USD",
          income: 900,
          expenses: 0,
          profit: 900,
          months: [
            { month: "2026-09", income: 500, expenses: 0, profit: 500 },
            { month: "2026-10", income: 400, expenses: 0, profit: 400 },
          ],
          categories: [],
        },
      ],
    }),
  );
  useGetTaxReportQuery.mockReturnValue(
    loaded({
      ...days,
      currencies: [
        {
          currency: "USD",
          tax: 70,
          taxable: 1000,
          documents: 2,
          gst: false,
          reverseChargeTaxable: 0,
          rates: [
            { rate: 7, documents: 2, ...halfTaxed, taxable: 1000, tax: 70 },
          ],
          months: [
            { month: "2026-09", rate: 7, documents: 1, ...halfTaxed },
            { month: "2026-10", rate: 7, documents: 1, ...halfTaxed },
          ],
        },
      ],
    }),
  );
  useGetTdsReportQuery.mockReturnValue(
    loaded({
      ...days,
      currencies: [
        {
          currency: "USD",
          withheld: 10,
          clients: [
            {
              customerId: "c1",
              name: "Northwind",
              payments: 1,
              received: 90,
              withheld: 10,
            },
          ],
          months: [{ month: "2026-10", payments: 1, withheld: 10 }],
          payments: [
            {
              documentId: "d1",
              documentNumber: "INV-1001",
              datePaid: "2026-10-02",
              client: "Northwind",
              received: 90,
              withheld: 10,
            },
          ],
        },
      ],
    }),
  );
  const page = render("/reports?period=30d");
  const months = (caption) =>
    [
      ...[...page.querySelectorAll("table")]
        .find((table) =>
          table.querySelector("caption").textContent.startsWith(caption),
        )
        .querySelectorAll('tbody td[data-label="Month"]'),
    ].map((cell) => cell.textContent);
  const both = ["Sep 8-30, 2026", "Oct 1-7, 2026"];
  expect(months("Billed and received by month")).toEqual(both);
  expect(months("Profit and loss by month")).toEqual(both);
  expect(months("Tax charged by month and rate")).toEqual(both);
  expect(months("Tax withheld by month")).toEqual(["Oct 1-7, 2026"]);
  expect(
    [...page.querySelectorAll('#income [role="img"]')].map((month) =>
      month.getAttribute("aria-label"),
    ),
  ).toEqual([
    "Sep 8-30, 2026: billed 600.00, received 300.00",
    "Oct 1-7, 2026: billed 400.00, received 300.00",
  ]);
});

it("opens at the linked section", () => {
  withFigures();
  const scrolled = [];
  Element.prototype.scrollIntoView = vi.fn(function () {
    scrolled.push(this.id);
  });
  const page = render("/reports#ageing");
  expect(
    ["income", "profit", "ageing", "clients"].map(
      (id) => page.querySelector(`#${id}`)?.querySelector("h2").textContent,
    ),
  ).toEqual([
    "Billed and received by month",
    "Profit and loss",
    "Unpaid invoices by age",
    "Top clients",
  ]);
  expect(scrolled).toEqual(["ageing"]);
  expect(document.activeElement.id).toBe("ageing");
});

it.each(["/reports#%", "/reports#nowhere", "/reports"])(
  "stays at top for %s",
  (path) => {
    withFigures();
    Element.prototype.scrollIntoView = vi.fn();
    const page = render(path);
    expect(page.querySelector("#income")).not.toBeNull();
    expect(Element.prototype.scrollIntoView).not.toHaveBeenCalled();
  },
);

it("charts billed and received", () => {
  withFigures();
  const income = render().querySelector("#income");
  expect(
    [...income.querySelectorAll("li")].map((item) => item.textContent),
  ).toEqual(["Billed", "Received"]);
  const chart = income.querySelector('[role="group"]');
  expect(chart.getAttribute("aria-label")).toBe("Billed and received by month");
  expect(
    [...chart.querySelectorAll('[role="img"]')].map((month) =>
      month.getAttribute("aria-label"),
    ),
  ).toEqual(["September 2026: billed 1,000.00, received 600.00"]);
  expect(income.querySelector("caption").closest("details").open).toBe(false);
});

it("names chart and download", async () => {
  withFigures();
  const fetchFile = vi.fn(() => ({
    unwrap: () => Promise.resolve("blob:report"),
  }));
  useGetReportCsvMutation.mockReturnValue([fetchFile, { isLoading: false }]);
  const { revokeObjectURL } = URL;
  URL.revokeObjectURL = vi.fn();
  const saved = [];
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, "click")
    .mockImplementation(function save() {
      saved.push(this.download);
    });
  try {
    const income = render().querySelector("#income");
    expect(income.querySelector("h2").textContent).toBe(
      "Billed and received by month",
    );
    await act(async () =>
      income
        .querySelector(
          'button[aria-label="Download billed and received by month as CSV"]',
        )
        .click(),
    );
    expect(fetchFile).toHaveBeenCalledWith({
      section: "income",
      period: "12m",
    });
    expect(saved).toEqual([
      "florin-billed-and-received-2025-11-01-to-2026-10-02.csv",
    ]);
  } finally {
    click.mockRestore();
    URL.revokeObjectURL = revokeObjectURL;
  }
});

it("spaces table columns evenly", () => {
  withFigures();
  const page = render();
  const tables = [...page.querySelectorAll("table")];
  expect(tables.length).toBeGreaterThan(0);
  for (const table of tables)
    expect(table.className).toContain("tabled:justify-between");
  const clients = page.querySelector("#clients table");
  expect(clients.style.gridTemplateColumns).toBe(
    "minmax(min(240px,40%),auto) auto auto auto auto",
  );
  const documents = clients.querySelector('[data-label="Documents"]');
  expect(documents.className).toContain("text-right");
  expect(documents.className).toContain("tabular-nums");
  for (const table of tables)
    expect(table.querySelector("tbody td").className).toMatch(
      /min-\[1536px\]:text-\[\d+px\]/,
    );
});

const rowsFrom = (first) => {
  const name = (panel) => panel.querySelector("h2").textContent;
  const rows = [];
  for (let item = first; item; item = item.nextElementSibling)
    rows.push(
      item.tagName === "SECTION" ? name(item) : [...item.children].map(name),
    );
  return rows;
};
const owed = ["Unpaid invoices by age", "Payment methods"];

it.each([
  [
    "a short period",
    1,
    false,
    ["Profit and loss", owed, "Top clients", ["Tax charged", "Tax withheld"]],
  ],
  [
    "a long period",
    11,
    false,
    ["Profit and loss", owed, "Top clients", ["Tax charged", "Tax withheld"]],
  ],
  [
    "tax split for GST",
    1,
    true,
    ["Profit and loss", owed, "Top clients", "Tax charged", "Tax withheld"],
  ],
])("same layout for %s", (_case, months, gst, layout) => {
  withFigures();
  useGetProfitReportQuery.mockReturnValue(
    loaded({
      period: "12m",
      currencies: [
        {
          currency: "USD",
          income: 900,
          expenses: 0,
          profit: 900,
          months: Array.from({ length: months }, (_, index) => ({
            month: `2025-${String(index + 1).padStart(2, "0")}`,
            income: 900,
            expenses: 0,
            profit: 900,
          })),
          categories: [],
        },
      ],
    }),
  );
  const tax = { taxable: 1000, tax: 70, cgst: 0, sgst: 0, igst: 0 };
  useGetTaxReportQuery.mockReturnValue(
    loaded({
      currencies: [
        {
          currency: "USD",
          tax: 70,
          taxable: 1000,
          documents: 1,
          gst,
          reverseChargeTaxable: 0,
          rates: [{ rate: 7, documents: 1, ...tax }],
          months: [{ month: "2026-09", rate: 7, ...tax }],
        },
      ],
    }),
  );
  const page = render();
  expect(rowsFrom(page.querySelector("#profit"))).toEqual(layout);
  const rates = [...page.querySelectorAll("table")].find((table) =>
    table
      .querySelector("caption")
      .textContent.startsWith("Tax charged by rate"),
  );
  expect(rates.parentElement.className).toContain(
    gst ? "[container-name:table]" : "[container-name:narrow-table]",
  );
});

it("opens on last 90 days", async () => {
  withFigures();
  const scrolled = [];
  Element.prototype.scrollIntoView = vi.fn(function () {
    scrolled.push(this.id);
  });
  const page = render("/reports?period=90d#profit");
  expect(useGetReportQuery).toHaveBeenLastCalledWith({
    period: "90d",
    view: undefined,
  });
  expect(useGetProfitReportQuery).toHaveBeenLastCalledWith("90d");
  expect(scrolled).toEqual(["profit"]);
  const period = [...page.querySelectorAll("label")]
    .find((label) => label.textContent.startsWith("Period"))
    .querySelector('[role="combobox"]');
  expect(period.value).toBe("90d");
  await act(async () => period.click());
  expect(
    [...document.querySelectorAll('[role="option"]')]
      .slice(0, 3)
      .map((option) => option.textContent),
  ).toEqual(["Last 30 days", "Last 90 days", "Last 3 months"]);
  await act(async () => period.click());
});

const taxedAt = (rates) => {
  const sum = (key) => rates.reduce((total, row) => total + row[key], 0);
  useGetTaxReportQuery.mockReturnValue(
    loaded({
      period: "12m",
      from: "2025-11-01",
      to: "2026-10-02",
      currencies: [
        {
          currency: "USD",
          tax: sum("tax"),
          taxable: sum("taxable"),
          documents: sum("documents"),
          gst: false,
          reverseChargeTaxable: 0,
          rates: rates.map((row) => ({ cgst: 0, sgst: 0, igst: 0, ...row })),
          months: [],
        },
      ],
    }),
  );
};

it("splits taxed and untaxed sales", () => {
  withFigures();
  taxedAt([
    { rate: 0, documents: 2, taxable: 500, tax: 0 },
    { rate: 5, documents: 1, taxable: 400, tax: 20 },
    { rate: 10, documents: 1, taxable: 600, tax: 60 },
  ]);
  expect(render().textContent).toContain(
    "USD 80.00 charged on USD 1,000.00 of taxed sales. USD 500.00 more had no tax.",
  );
});

it("no untaxed note when all taxed", () => {
  withFigures();
  taxedAt([{ rate: 10, documents: 1, taxable: 600, tax: 60 }]);
  const text = render().textContent;
  expect(text).toContain("USD 60.00 charged on USD 600.00 of taxed sales.");
  expect(text).not.toContain("had no tax");
});

it("no tax when nothing taxed", () => {
  withFigures();
  taxedAt([{ rate: 0, documents: 2, taxable: 500, tax: 0 }]);
  const text = render().textContent;
  expect(text).toContain(
    "No tax was charged on the USD 500.00 of sales, from 2 documents.",
  );
  expect(text).not.toContain("of taxed sales");
});

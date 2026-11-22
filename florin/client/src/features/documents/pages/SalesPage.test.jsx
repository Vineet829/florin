import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import fixture from "./SalesPage.fixture.json";

vi.mock("../../dashboard/components/DashboardShell", () => ({
  default: ({ children }) => children,
}));

window.IS_REACT_ACT_ENVIRONMENT = true;

let requests;
let address;
let root;
let container;
beforeEach(() => {
  vi.resetModules();
  vi.stubEnv("VITE_API_URL", "http://localhost:3001/api/v1");
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date(2026, 9, 4, 12));
  requests = [];
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });

const serve = (answers = {}) =>
  vi.stubGlobal(
    "fetch",
    vi.fn(async (request) => {
      const url = new URL(request.url);
      requests.push(`${url.pathname}${url.search}`);
      const answer = {
        "/document/sales": fixture.sales,
        "/document/all": fixture.documents,
        "/businesses": { businesses: [] },
        ...answers,
      }[url.pathname.replace("/api/v1", "")];
      const body = await (typeof answer === "function" ? answer(url) : answer);
      if (body instanceof Response) return body;
      return body ? json(body) : json({ message: "Not found" }, 404);
    }),
  );

const listed = (page) =>
  vi.waitFor(() =>
    expect(
      page.querySelectorAll("section")[1]?.querySelector("tbody"),
    ).not.toBeNull(),
  );

const open = async (path, answers, ready = listed) => {
  serve(answers);
  const { Provider } = await import("react-redux");
  const { MemoryRouter, useLocation } = await import("react-router-dom");
  const { store } = await import("../../../app/store");
  const { default: SalesPage } = await import("./SalesPage");
  const Address = () => {
    address = useLocation().search;
    return null;
  };
  container = document.body.appendChild(document.createElement("div"));
  root = createRoot(container);
  await act(async () => {
    root.render(
      <Provider store={store}>
        <MemoryRouter initialEntries={[path]}>
          <SalesPage />
          <Address />
        </MemoryRouter>
      </Provider>,
    );
  });
  await act(() => ready(container));
  return container;
};

const click = (element) =>
  act(async () => {
    element.dispatchEvent(
      new MouseEvent("click", { bubbles: true, cancelable: true }),
    );
  });
const documents = () =>
  requests.filter((request) => request.includes("/document/"));
const summary = (page) => page.querySelector("section");
const list = (page) => page.querySelectorAll("section")[1];
const table = (element) =>
  [...element.querySelectorAll("tbody tr")].map((row) =>
    [...row.children].map((cell) => cell.textContent),
  );
const headings = (element) =>
  [...element.querySelectorAll("thead th")].map((th) => th.textContent);
const part = (page, title) =>
  [...summary(page).querySelectorAll("h3")].find(
    (heading) => heading.textContent === title,
  ).parentElement;
const compared = (page) =>
  [...summary(page).querySelectorAll("p")].find((line) =>
    line.textContent.includes("vs "),
  );
const select = (page, label) =>
  [...page.querySelectorAll("label")]
    .find((element) => element.textContent.startsWith(label))
    ?.querySelector('[role="combobox"]');
const options = async (combobox) => {
  await act(async () => combobox.click());
  const words = [...document.querySelectorAll('[role="option"]')].map(
    (option) => option.firstChild.textContent,
  );
  await act(async () =>
    combobox.dispatchEvent(
      new KeyboardEvent("keydown", { key: "Escape", bubbles: true }),
    ),
  );
  return words;
};
const choose = async (combobox, value) => {
  await act(async () => combobox.click());
  await act(async () =>
    document.querySelector(`[role="option"][data-value="${value}"]`).click(),
  );
};

it("shows the period summary", async () => {
  const page = await open("/sales?period=90d");
  expect(document.title).toBe("Sales - Florin");
  expect(page.querySelector("h1").textContent).toBe("Documents.");
  expect(documents()).toEqual([
    "/api/v1/document/sales?period=90d",
    "/api/v1/document/all?page=1&size=10&sort=createdAt&dir=desc&billed=true&period=90d&currency=USD",
  ]);
  expect(summary(page).textContent).toContain(
    "Billed68,848.08on 21 documents, issued Jul 7 - Oct 4, 2026",
  );
  const change = compared(page);
  expect(change.textContent).toBe(
    "up 29.1%vs 53,344.02 in Apr 8 - Jul 6, 2026",
  );
  expect(change.querySelector(".text-change-up")).not.toBeNull();
  expect(select(page, "Issued").textContent).toContain("Last 90 days");
  expect(select(page, "Currency")).toBeUndefined();
  const types = part(page, "By type");
  expect(headings(types)).toEqual(["Type", "Documents", "Amount"]);
  expect(table(types)).toEqual([
    ["Invoices", "14", "52,349.20"],
    ["Receipts", "6", "17,598.88"],
    ["Credit notes", "1", "-1,100.00"],
  ]);
  expect(
    [...types.querySelectorAll("tbody [style]")].map((bar) =>
      Math.round(parseFloat(bar.style.width)),
    ),
  ).toEqual([100, 34, 0]);
  const tax = part(page, "Tax by rate");
  expect(headings(tax)).toEqual(["Rate", "Documents", "Taxable", "Tax"]);
  expect(table(tax)).toEqual([
    ["8%", "15", "48,146.00", "3,851.68"],
    ["5%", "6", "16,048.00", "802.40"],
  ]);
  expect(tax.textContent).toContain("The total includes 4,654.08 tax.");
  expect(list(page).querySelector("h2").textContent).toBe("Billed documents");
  expect(headings(list(page))).toEqual([
    "Document",
    "Type",
    "Client",
    "Issued",
    "Amount",
  ]);
  expect(table(list(page)).slice(0, 3)).toEqual([
    ["INV-1039", "Invoice", "Bhide Nursing Home", "Oct 3, 2026", "USD2,762.75"],
    [
      "INV-1038",
      "Invoice",
      "Kale & Deshpande Architects",
      "Oct 2, 2026",
      "USD750.76",
    ],
    [
      "REC-0304",
      "Receipt",
      "Deshmukh & Associates",
      "Oct 2, 2026",
      "USD9,598.88",
    ],
  ]);
  expect(list(page).querySelector("tbody a").getAttribute("href")).toBe(
    "/document/00000000-0000-0000-0000-000000001039",
  );
  expect(page.querySelector('[role="status"]').textContent).toBe("1-10 of 21");
});

const always = {
  ...fixture.sales,
  period: {
    key: "",
    from: null,
    to: null,
    previousFrom: null,
    previousTo: null,
  },
  previousTotal: null,
  total: 209842.18,
  documents: 64,
};

it("defaults to all dates", async () => {
  const page = await open("/sales", { "/document/sales": always });
  expect(documents()).toEqual([
    "/api/v1/document/sales",
    "/api/v1/document/all?page=1&size=10&sort=createdAt&dir=desc&billed=true&currency=USD",
  ]);
  expect(select(page, "Issued").textContent).toContain("All dates");
  expect(summary(page).textContent).toContain(
    "Billed209,842.18on 64 documents",
  );
  expect(summary(page).textContent).not.toContain("issued");
  expect(compared(page)).toBeUndefined();
});

it("keeps period and currency in URL", async () => {
  const page = await open("/sales?period=90d", {
    "/document/sales": (url) => ({
      ...fixture.sales,
      currencies: ["USD", "EUR"],
      currency: url.searchParams.get("currency") || "USD",
    }),
  });
  expect(await options(select(page, "Issued"))).toEqual([
    "All dates",
    "Last 30 days",
    "Last 90 days",
    "Last 12 months",
    "This month",
    "Last month",
    "This quarter",
    "This year",
    "Last year",
  ]);
  await choose(select(page, "Issued"), "this-year");
  expect(address).toBe("?period=this-year");
  await act(() =>
    vi.waitFor(() =>
      expect(documents()).toContain(
        "/api/v1/document/all?page=1&size=10&sort=createdAt&dir=desc&billed=true&from=2026-01-01&to=2026-10-04&currency=USD",
      ),
    ),
  );
  expect(documents()).toContain("/api/v1/document/sales?period=this-year");
  expect(await options(select(page, "Currency"))).toEqual(["USD", "EUR"]);
  await choose(select(page, "Currency"), "EUR");
  expect(address).toBe("?period=this-year&currency=EUR");
  await act(() =>
    vi.waitFor(() =>
      expect(documents()).toContain(
        "/api/v1/document/sales?period=this-year&currency=EUR",
      ),
    ),
  );
  expect(documents()).toContain(
    "/api/v1/document/all?page=1&size=10&sort=createdAt&dir=desc&billed=true&from=2026-01-01&to=2026-10-04&currency=EUR",
  );
  await choose(select(page, "Currency"), "USD");
  expect(address).toBe("?period=this-year");
});

it("this year ends at business today", async () => {
  const zone = process.env.TZ;
  process.env.TZ = "Asia/Kolkata";
  try {
    vi.setSystemTime(new Date("2027-01-01T05:00:00+05:30"));
    await open("/sales?period=this-year", {
      "/businesses": {
        businesses: [
          {
            _id: "b-1",
            name: "Studio Demo",
            active: true,
            timeZone: "America/Los_Angeles",
          },
        ],
      },
    });
    await act(() =>
      vi.waitFor(() =>
        expect(documents()).toContain(
          "/api/v1/document/all?page=1&size=10&sort=createdAt&dir=desc&billed=true&from=2026-01-01&to=2026-12-31&currency=USD",
        ),
      ),
    );
  } finally {
    if (zone === undefined) delete process.env.TZ;
    else process.env.TZ = zone;
  }
});

it("names currencies when several", async () => {
  const page = await open("/sales?period=90d", {
    "/document/sales": { ...fixture.sales, currencies: ["USD", "EUR"] },
  });
  expect(summary(page).textContent).toContain("BilledUSD68,848.08");
  expect(compared(page).textContent).toContain("vs USD 53,344.02 in");
  expect(table(part(page, "By type"))[0]).toEqual([
    "Invoices",
    "14",
    "USD52,349.20",
  ]);
  expect(part(page, "Tax by rate").textContent).toContain(
    "The total includes USD 4,654.08 tax.",
  );
});

it("marks a fall, skips zero base", async () => {
  const page = await open("/sales?period=90d", {
    "/document/sales": (url) =>
      url.searchParams.get("period") === "30d"
        ? { ...fixture.sales, previousTotal: 0 }
        : { ...fixture.sales, previousTotal: 80000 },
  });
  expect(compared(page).textContent).toBe(
    "down 13.9%vs 80,000.00 in Apr 8 - Jul 6, 2026",
  );
  expect(compared(page).querySelector(".text-change-down")).not.toBeNull();
  await choose(select(page, "Issued"), "30d");
  await act(() =>
    vi.waitFor(() =>
      expect(compared(page).textContent).toBe("vs 0.00 in Apr 8 - Jul 6, 2026"),
    ),
  );
});

it("lists unbilled types at zero", async () => {
  const page = await open("/sales?period=30d", {
    "/document/sales": {
      ...fixture.sales,
      byType: [
        { type: "Invoice", documents: 3, amount: 2520 },
        { type: "Receipt", documents: 0, amount: 0 },
        { type: "Credit Note", documents: 0, amount: 0 },
      ],
    },
  });
  expect(table(part(page, "By type"))).toEqual([
    ["Invoices", "3", "2,520.00"],
    ["Receipts", "0", "0.00"],
    ["Credit notes", "0", "0.00"],
  ]);
});

it("keeps page in URL", async () => {
  const page = await open("/sales?period=90d");
  await click(page.querySelector('button[aria-label="Next page"]'));
  expect(address).toBe("?period=90d&page=2");
  await act(() =>
    vi.waitFor(() =>
      expect(documents()).toContain(
        "/api/v1/document/all?page=2&size=10&sort=createdAt&dir=desc&billed=true&period=90d&currency=USD",
      ),
    ),
  );
  await choose(select(page, "Issued"), "12m");
  expect(address).toBe("?period=12m");
});

it("empty period message", async () => {
  const page = await open(
    "/sales?period=30d",
    {
      "/document/sales": {
        ...fixture.sales,
        total: 0,
        previousTotal: 0,
        documents: 0,
        byType: [
          { type: "Invoice", documents: 0, amount: 0 },
          { type: "Receipt", documents: 0, amount: 0 },
          { type: "Credit Note", documents: 0, amount: 0 },
        ],
        tax: { total: 0, rates: [] },
      },
      "/document/all": {
        ...fixture.documents,
        totalDocuments: 0,
        numberOfPages: 0,
        myDocuments: [],
        totals: [],
      },
    },
    (element) =>
      vi.waitFor(() =>
        expect(element.textContent).toContain(
          "Nothing was billed in this period.",
        ),
      ),
  );
  expect(summary(page).textContent).toContain("Billed0.00on 0 documents");
  expect(part(page, "By type").textContent).toBe("By typeNothing was billed.");
  expect(part(page, "Tax by rate").textContent).toBe(
    "Tax by rateNo tax was charged.",
  );
  expect(list(page).textContent).toContain(
    "Invoices, receipts and credit notes show up here once they're issued, newest first.",
  );
  expect(list(page).querySelector("table")).toBeNull();
});

it("shows placeholder while loading", async () => {
  let answer;
  const page = await open(
    "/sales?period=90d",
    {
      "/document/sales": new Promise((resolve) => {
        answer = resolve;
      }),
    },
    () => Promise.resolve(),
  );
  expect(
    page.querySelector('[aria-hidden="true"] .animate-ov-pulse'),
  ).not.toBeNull();
  expect(page.querySelector('[role="status"]').textContent).toBe(
    "Loading workspace data...",
  );
  expect(documents()).toEqual(["/api/v1/document/sales?period=90d"]);
  await act(async () => answer(fixture.sales));
  await act(() => listed(page));
  expect(summary(page).textContent).toContain("68,848.08");
  expect(page.querySelector(".animate-ov-pulse")).toBeNull();
});

it("figures load error with retry", async () => {
  let fails = true;
  const page = await open(
    "/sales?period=90d",
    {
      "/document/sales": () =>
        fails ? json({ message: "Try later." }, 500) : fixture.sales,
    },
    (element) =>
      vi.waitFor(() =>
        expect(element.querySelector('[role="alert"]')).not.toBeNull(),
      ),
  );
  const alert = page.querySelector('[role="alert"]');
  expect(alert.textContent).toContain("We couldn't load this page.");
  expect(alert.textContent).toContain("Try later.");
  expect(documents().some((request) => request.includes("/document/all"))).toBe(
    false,
  );
  fails = false;
  await click(
    [...alert.querySelectorAll("button")].find(
      (button) => button.textContent === "Try again",
    ),
  );
  await act(() => listed(page));
  expect(summary(page).textContent).toContain("68,848.08");
});

it("documents load error keeps figures", async () => {
  const page = await open(
    "/sales?period=90d",
    { "/document/all": () => json({ message: "Try later." }, 500) },
    (element) =>
      vi.waitFor(() =>
        expect(element.querySelector('[role="alert"]')).not.toBeNull(),
      ),
  );
  expect(summary(page).textContent).toContain("68,848.08");
  expect(list(page).querySelector('[role="alert"]').textContent).toContain(
    "We couldn't load this page.",
  );
});

it("shows the documents tab row", async () => {
  const page = await open("/sales?period=90d");
  expect(
    [...page.querySelectorAll('nav[aria-label="Documents views"] a')].map(
      (tab) => [
        tab.textContent,
        tab.getAttribute("href"),
        tab.getAttribute("aria-current"),
      ],
    ),
  ).toEqual([
    ["All documents", "/documents", null],
    ["Receivables", "/receivables", null],
    ["Sales", "/sales", "page"],
    ["Payments", "/payments", null],
    ["Recurring", "/recurring", null],
  ]);
});

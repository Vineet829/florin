import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import fixture from "./ItemSalesPage.fixture.json";

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
  requests = [];
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
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
        "/item/sales": fixture.sales,
        "/item/sales/detail": fixture.detail,
        "/businesses": { businesses: [] },
        ...answers,
      }[url.pathname.replace("/api/v1", "")];
      const body = await (typeof answer === "function" ? answer(url) : answer);
      if (body instanceof Response) return body;
      return body ? json(body) : json({ message: "Not found" }, 404);
    }),
  );

const listed = (page) =>
  vi.waitFor(() => expect(page.querySelector("tbody")).not.toBeNull());

const PAGES = {
  list: () => import("./ItemSalesPage"),
  item: () => import("./ItemSalesDetailPage"),
};
const open = async (path, answers, ready = listed) => {
  serve(answers);
  const { Provider } = await import("react-redux");
  const { MemoryRouter, useLocation } = await import("react-router-dom");
  const { store } = await import("../../../app/store");
  const { default: Page } =
    await PAGES[path.startsWith("/items/sales/item") ? "item" : "list"]();
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
          <Page />
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
const asked = (path) =>
  requests.filter((request) => request.startsWith(`/api/v1${path}?`));
const table = (element) =>
  [...element.querySelectorAll("tbody tr")].map((row) =>
    [...row.children].map((cell) => cell.textContent),
  );
const headings = (element) =>
  [...element.querySelectorAll("thead th")].map((th) => th.textContent);
const links = (element) =>
  [...element.querySelectorAll("tbody a")].map((link) => [
    link.textContent,
    link.getAttribute("href"),
  ]);
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
const C = "00000000-0000-0000-0000-00000000";
const MOTION = "/items/sales/item?name=Motion+graphics+%28per+15+seconds%29";

it("lists item sales with shares", async () => {
  const page = await open("/items/sales?period=90d");
  expect(document.title).toBe("Sales by item - Florin");
  expect(page.querySelector("h1").textContent).toBe("Items.");
  expect(
    [...page.querySelectorAll('nav[aria-label="Items views"] a')].map((tab) => [
      tab.textContent,
      tab.getAttribute("aria-current"),
    ]),
  ).toEqual([
    ["All items", null],
    ["Sales by item", "page"],
  ]);
  expect(asked("/item/sales")).toEqual([
    "/api/v1/item/sales?period=90d&page=1&size=10",
  ]);
  expect(page.textContent).toContain(
    "What each item sold in Jul 7 - Oct 4, 2026, before tax, less what credit notes took back.",
  );
  expect(select(page, "Sold").textContent).toContain("Last 90 days");
  expect(headings(page)).toEqual(["Item", "Sold", "Amount", "Share of income"]);
  expect(table(page)).toEqual([
    ["Motion graphics (per 15 seconds)", "28", "18,200.00", "28%"],
    ["Logo and identity system", "3", "9,280.00", "15%"],
    ["Consulting (hourly)", "52", "7,140.00", "11%"],
    ["Front-end build (per page)", "17", "6,460.00", "10%"],
    ["Brand strategy workshop", "3", "5,400.00", "8%"],
    ["Website maintenance (monthly)", "6", "5,100.00", "8%"],
    ["Photography (half day)", "4", "4,800.00", "8%"],
    ["Copywriting (per page)", "22", "3,960.00", "6%"],
    ["Social media kit", "2", "2,400.00", "4%"],
    ["Other lines", "5", "1,454.00", "2%"],
    ["All 10 items", "", "64,194.00", "100%"],
  ]);
  expect(
    table(page)
      .slice(0, -1)
      .reduce((sum, row) => sum + parseInt(row[3], 10), 0),
  ).toBe(100);
  expect(links(page)).toHaveLength(10);
  expect(links(page)[9]).toEqual([
    "Other lines",
    "/items/sales/item?name=Other+lines&period=90d",
  ]);
  expect(links(page)[0]).toEqual([
    "Motion graphics (per 15 seconds)",
    `${MOTION}&period=90d`,
  ]);
  expect(links(page)[2]).toEqual([
    "Consulting (hourly)",
    "/items/sales/item?name=Consulting+%28hourly%29&period=90d",
  ]);
  expect(page.querySelector('[role="status"]').textContent).toBe("1-10 of 10");
});

it("keeps filters in the URL", async () => {
  const page = await open("/items/sales?period=90d", {
    "/item/sales": (url) => ({
      ...fixture.sales,
      currencies: ["USD", "EUR"],
      currency: url.searchParams.get("currency") || "USD",
      totalItems: 12,
      numberOfPages: 2,
    }),
  });
  expect(table(page)[0][2]).toBe("USD18,200.00");
  expect(await options(select(page, "Currency"))).toEqual(["USD", "EUR"]);
  await choose(select(page, "Currency"), "EUR");
  expect(address).toBe("?period=90d&currency=EUR");
  await act(() =>
    vi.waitFor(() =>
      expect(asked("/item/sales")).toContain(
        "/api/v1/item/sales?period=90d&currency=EUR&page=1&size=10",
      ),
    ),
  );
  expect(links(page)[0][1]).toBe(`${MOTION}&period=90d&currency=EUR`);
  await click(page.querySelector('button[aria-label="Next page"]'));
  expect(address).toBe("?period=90d&currency=EUR&page=2");
  await choose(select(page, "Sold"), "last-month");
  expect(address).toBe("?period=last-month&currency=EUR");
  await act(() =>
    vi.waitFor(() =>
      expect(asked("/item/sales")).toContain(
        "/api/v1/item/sales?period=last-month&currency=EUR&page=1&size=10",
      ),
    ),
  );
});

it("empty period message", async () => {
  const page = await open(
    "/items/sales?period=30d",
    {
      "/item/sales": {
        ...fixture.sales,
        total: 0,
        totalItems: 0,
        numberOfPages: 0,
        items: [],
      },
    },
    (element) =>
      vi.waitFor(() =>
        expect(element.textContent).toContain(
          "Nothing was sold in this period.",
        ),
      ),
  );
  expect(page.textContent).toContain(
    "Items show up here once they're on an invoice or receipt you issue, with what each brought in.",
  );
  expect(page.querySelector("table")).toBeNull();
  expect(select(page, "Sold")).toBeTruthy();
});

it("shows loading and error states", async () => {
  let answer;
  const page = await open(
    "/items/sales?period=90d",
    {
      "/item/sales": new Promise((resolve) => {
        answer = resolve;
      }),
    },
    () => Promise.resolve(),
  );
  expect(page.querySelector('[role="status"]').textContent).toBe(
    "Loading workspace data...",
  );
  await act(async () => answer(json({ message: "Try later." }, 500)));
  await act(() =>
    vi.waitFor(() =>
      expect(page.querySelector('[role="alert"]')).not.toBeNull(),
    ),
  );
  expect(page.querySelector('[role="alert"]').textContent).toContain(
    "We couldn't load this page.Try later.",
  );
  expect(page.querySelector("table")).toBeNull();
});

it("shows one item's sales", async () => {
  const page = await open(`${MOTION}&period=90d`);
  expect(document.title).toBe(
    "Motion graphics (per 15 seconds) sales - Florin",
  );
  expect(page.querySelector("h1").textContent).toBe("Item sales.");
  expect(asked("/item/sales/detail")).toEqual([
    "/api/v1/item/sales/detail?name=Motion+graphics+%28per+15+seconds%29&period=90d",
  ]);
  const [figures, linesPanel] = page.querySelectorAll("section");
  expect(figures.querySelector("h2").textContent).toBe(
    "Motion graphics (per 15 seconds)",
  );
  expect(figures.textContent).toContain(
    "Sold Jul 7 - Oct 4, 2026, before tax.",
  );
  expect(figures.textContent).toContain(
    "Brought in18,200.0028 sold, 28% of income, on 6 documents",
  );
  const buyers = figures.querySelector("table");
  expect(headings(buyers)).toEqual(["Client", "Sold", "Amount"]);
  expect(table(buyers)).toEqual([
    ["Deshmukh & Associates", "12", "7,800.00"],
    ["Casa Fernandes Homestay", "8", "5,200.00"],
    ["Mehta Bakers", "4", "2,600.00"],
    ["Pai Electricals", "4", "2,600.00"],
  ]);
  expect(links(buyers)[0]).toEqual([
    "Deshmukh & Associates",
    `/single-customer/${C}c107`,
  ]);
  expect(linesPanel.querySelector("h2").textContent).toBe("Lines");
  expect(linesPanel.textContent).toContain(
    "Each line it was sold on, newest first.",
  );
  expect(headings(linesPanel)).toEqual([
    "Document",
    "Type",
    "Issued",
    "Client",
    "Sold",
    "Amount",
  ]);
  expect(table(linesPanel)).toEqual([
    [
      "REC-0304",
      "Receipt",
      "Oct 2, 2026",
      "Deshmukh & Associates",
      "8",
      "5,200.00",
    ],
    ["INV-1035", "Invoice", "Oct 1, 2026", "Mehta Bakers", "4", "2,600.00"],
    ["INV-1034", "Invoice", "Sep 28, 2026", "Pai Electricals", "4", "2,600.00"],
    [
      "REC-0303",
      "Receipt",
      "Sep 15, 2026",
      "Casa Fernandes Homestay",
      "4",
      "2,600.00",
    ],
    [
      "INV-1022",
      "Invoice",
      "Aug 18, 2026",
      "Deshmukh & Associates",
      "4",
      "2,600.00",
    ],
    [
      "INV-1017",
      "Invoice",
      "Jul 20, 2026",
      "Casa Fernandes Homestay",
      "4",
      "2,600.00",
    ],
  ]);
  expect(links(linesPanel)[0]).toEqual([
    "REC-0304",
    "/document/00000000-0000-0000-0000-000000000304",
  ]);
});

it("counts hidden lines", async () => {
  const page = await open(`${MOTION}&period=12m`, {
    "/item/sales/detail": { ...fixture.detail, linesTotal: 63 },
  });
  expect(page.querySelectorAll("section")[1].textContent).toContain(
    "The latest 6 of 63 lines, newest first.",
  );
});

it("keeps item filters in URL", async () => {
  const page = await open(`${MOTION}&period=90d`, {
    "/item/sales/detail": (url) => ({
      ...fixture.detail,
      currencies: ["USD", "EUR"],
      currency: url.searchParams.get("currency") || "USD",
    }),
  });
  await choose(select(page, "Sold"), "this-year");
  expect(address).toBe(
    "?name=Motion+graphics+%28per+15+seconds%29&period=this-year",
  );
  await choose(select(page, "Currency"), "EUR");
  expect(address).toBe(
    "?name=Motion+graphics+%28per+15+seconds%29&period=this-year&currency=EUR",
  );
  await act(() =>
    vi.waitFor(() =>
      expect(asked("/item/sales/detail")).toContain(
        "/api/v1/item/sales/detail?name=Motion+graphics+%28per+15+seconds%29&period=this-year&currency=EUR",
      ),
    ),
  );
});

it("item empty period message", async () => {
  const page = await open(
    `${MOTION}&period=30d`,
    {
      "/item/sales/detail": {
        ...fixture.detail,
        quantity: 0,
        amount: 0,
        share: 0,
        documents: 0,
        clients: [],
        lines: [],
        linesTotal: 0,
      },
    },
    (element) =>
      vi.waitFor(() =>
        expect(element.textContent).toContain("Nothing sold in this period."),
      ),
  );
  expect(page.textContent).toContain("No one bought it in this period.");
  expect(page.textContent).toContain(
    "Brought in0.000 sold, 0% of income, on 0 documents",
  );
  expect(page.querySelector("table")).toBeNull();
});

it("item load error with retry", async () => {
  let fails = true;
  const page = await open(
    `${MOTION}&period=90d`,
    {
      "/item/sales/detail": () =>
        fails ? json({ message: "Try later." }, 500) : fixture.detail,
    },
    (element) =>
      vi.waitFor(() =>
        expect(element.querySelector('[role="alert"]')).not.toBeNull(),
      ),
  );
  const alert = page.querySelector('[role="alert"]');
  expect(alert.textContent).toContain("We couldn't load this page.");
  fails = false;
  await click(
    [...alert.querySelectorAll("button")].find(
      (button) => button.textContent === "Try again",
    ),
  );
  await act(() => listed(page));
  expect(page.textContent).toContain("Brought in18,200.00");
});

it("groups unnamed lines as other", async () => {
  const page = await open("/items/sales/item?name=Other+lines&period=30d", {
    "/item/sales/detail": {
      ...fixture.detail,
      item: "Other lines",
      quantity: 5,
      amount: 1454,
      share: 0.0227,
      documents: 3,
      clients: [
        {
          clientId: `${C}c107`,
          client: "Deshmukh & Associates",
          quantity: 5,
          amount: 1454,
        },
      ],
      lines: [fixture.detail.lines[0]],
      linesTotal: 1,
    },
  });
  expect(asked("/item/sales/detail")).toEqual([
    "/api/v1/item/sales/detail?name=Other+lines&period=30d",
  ]);
  const figures = page.querySelector("section");
  expect(figures.querySelector("h2").textContent).toBe("Other lines");
  expect(figures.textContent).toContain(
    "Sold Jul 7 - Oct 4, 2026, before tax, on lines saved without an item name.",
  );
  expect(figures.textContent).toContain(
    "Brought in1,454.005 sold, 2% of income, on 3 documents",
  );
});

it("shows item placeholder while loading", async () => {
  let answer;
  const page = await open(
    `${MOTION}&period=90d`,
    {
      "/item/sales/detail": new Promise((resolve) => {
        answer = resolve;
      }),
    },
    () => Promise.resolve(),
  );
  expect(page.querySelector('[role="status"]').textContent).toBe(
    "Loading workspace data...",
  );
  expect(page.querySelector("section")).toBeNull();
  expect(page.textContent).not.toContain("Brought in");
  await act(async () => answer(fixture.detail));
  await act(() => listed(page));
  expect(page.querySelector('[role="status"]')).toBeNull();
  expect(page.textContent).toContain("Brought in18,200.00");
});

it("points to Sales by item", async () => {
  const page = await open("/items/sales/item?period=90d", {}, (element) =>
    vi.waitFor(() =>
      expect(element.textContent).toContain("No item was chosen."),
    ),
  );
  expect(asked("/item/sales/detail")).toEqual([]);
  expect(page.querySelector('a[href="/items/sales"]').textContent).toBe(
    "Sales by item",
  );
});

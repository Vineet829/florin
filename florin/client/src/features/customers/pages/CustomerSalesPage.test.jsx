import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import fixture from "./CustomerSalesPage.fixture.json";

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
        "/customer/sales": fixture.sales,
        "/customer/all": {
          success: true,
          totalCustomers: 0,
          numberOfPages: 0,
          myCustomers: [],
        },
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

const open = async (
  path,
  answers,
  ready = listed,
  page = "CustomerSalesPage",
) => {
  serve(answers);
  const { Provider } = await import("react-redux");
  const { MemoryRouter, useLocation } = await import("react-router-dom");
  const { store } = await import("../../../app/store");
  const { default: Page } = await import(`./${page}.jsx`);
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
const sales = () =>
  requests.filter((request) => request.startsWith("/api/v1/customer/sales"));
const table = (page) =>
  [...page.querySelectorAll("tbody tr")].map((row) =>
    [...row.children].map((cell) => cell.textContent),
  );
const headings = (page) =>
  [...page.querySelectorAll("thead th")].map(
    (header) =>
      header.querySelector("button")?.textContent ?? header.textContent,
  );
const tabs = (page) =>
  [...page.querySelectorAll('nav[aria-label="Clients views"] a')].map((tab) => [
    tab.textContent,
    tab.getAttribute("href"),
    tab.getAttribute("aria-current"),
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

it("lists clients with totals", async () => {
  const page = await open("/customers/sales?period=90d");
  expect(document.title).toBe("Sales by customer - Florin");
  expect(page.querySelector("h1").textContent).toBe("Clients.");
  expect(tabs(page)).toEqual([
    ["All clients", "/customers", null],
    ["Sales by customer", "/customers/sales", "page"],
  ]);
  expect(sales()).toEqual([
    "/api/v1/customer/sales?period=90d&sort=billed&dir=desc&page=1&size=10",
  ]);
  expect(page.textContent).toContain(
    "What each client was billed on the documents issued Jul 7 - Oct 4, 2026, what they've paid on them, and what they still owe.",
  );
  expect(select(page, "Issued").textContent).toContain("Last 90 days");
  expect(select(page, "Sort clients").textContent).toContain("Most billed");
  expect(select(page, "Currency")).toBeUndefined();
  expect(headings(page)).toEqual([
    "Client",
    "Documents",
    "Billed",
    "Paid",
    "Owed",
  ]);
  expect(table(page)).toEqual([
    ["Deshmukh & Associates", "2", "13,459.88", "11,490.77", "1,969.11"],
    ["Casa Fernandes Homestay", "2", "10,317.45", "10,317.45", "0.00"],
    ["Nisarg Organic Farms", "2", "8,590.73", "3,278.96", "5,311.77"],
    ["Mehta Bakers", "3", "8,363.36", "5,145.86", "3,217.50"],
    ["Pai Electricals", "2", "5,298.15", "2,747.75", "2,550.40"],
    ["Shinde Constructions", "2", "5,164.09", "2,500.00", "2,664.09"],
    ["Kale & Deshpande Architects", "2", "4,953.00", "4,465.00", "488.00"],
    ["Iyer Dance Academy", "2", "4,861.50", "2,931.00", "1,930.50"],
    ["Bhide Nursing Home", "2", "4,262.75", "1,500.00", "2,762.75"],
    ["Gadre Mess", "2", "3,577.17", "185.50", "3,391.67"],
    ["All 10 clients", "21", "68,848.08", "44,562.29", "24,285.79"],
  ]);
  const links = [...page.querySelectorAll("tbody a")].map((link) => [
    link.textContent,
    link.getAttribute("href"),
  ]);
  expect(links).toHaveLength(9);
  expect(links[0]).toEqual([
    "Deshmukh & Associates",
    `/single-customer/${C}c107`,
  ]);
  expect(links.map(([name]) => name)).not.toContain("Nisarg Organic Farms");
  expect(page.querySelector('[role="status"]').textContent).toBe("1-10 of 10");
  expect(
    page.querySelector('th[aria-sort="descending"] button').textContent,
  ).toBe("Billed");
});

it("sorts by header and menu", async () => {
  const page = await open("/customers/sales?period=90d");
  const header = (name) =>
    [...page.querySelectorAll("thead button")].find(
      (button) => button.textContent === name,
    );
  await click(header("Owed"));
  expect(address).toBe("?period=90d&sort=owed&dir=desc");
  await act(() =>
    vi.waitFor(() =>
      expect(sales()).toContain(
        "/api/v1/customer/sales?period=90d&sort=owed&dir=desc&page=1&size=10",
      ),
    ),
  );
  await click(header("Owed"));
  expect(address).toBe("?period=90d&sort=owed&dir=asc");
  expect(await options(select(page, "Sort clients"))).toEqual([
    "Most billed",
    "Least billed",
    "Most paid",
    "Least paid",
    "Most owed",
    "Least owed",
    "Name A-Z",
    "Name Z-A",
  ]);
  await choose(select(page, "Sort clients"), "name:asc");
  expect(address).toBe("?period=90d&sort=name&dir=asc");
  await choose(select(page, "Sort clients"), "billed:desc");
  expect(address).toBe("?period=90d");
});

it("keeps period and currency in URL", async () => {
  const page = await open("/customers/sales?period=90d", {
    "/customer/sales": (url) => ({
      ...fixture.sales,
      currencies: ["USD", "EUR"],
      currency: url.searchParams.get("currency") || "USD",
    }),
  });
  expect(table(page)[0][2]).toBe("USD13,459.88");
  await choose(select(page, "Issued"), "this-year");
  expect(address).toBe("?period=this-year");
  await act(() =>
    vi.waitFor(() =>
      expect(sales()).toContain(
        "/api/v1/customer/sales?period=this-year&sort=billed&dir=desc&page=1&size=10",
      ),
    ),
  );
  expect(await options(select(page, "Currency"))).toEqual(["USD", "EUR"]);
  await choose(select(page, "Currency"), "EUR");
  expect(address).toBe("?period=this-year&currency=EUR");
  await act(() =>
    vi.waitFor(() =>
      expect(sales()).toContain(
        "/api/v1/customer/sales?period=this-year&currency=EUR&sort=billed&dir=desc&page=1&size=10",
      ),
    ),
  );
  await choose(select(page, "Currency"), "USD");
  expect(address).toBe("?period=this-year");
});

it("pages with overall totals", async () => {
  const page = await open("/customers/sales?period=12m", {
    "/customer/sales": (url) => ({
      ...fixture.sales,
      totalClients: 13,
      numberOfPages: 2,
      page: Number(url.searchParams.get("page")),
      clients:
        url.searchParams.get("page") === "2"
          ? fixture.sales.clients.slice(0, 3)
          : fixture.sales.clients,
    }),
  });
  expect(table(page).at(-1)[0]).toBe("All 13 clients");
  await click(page.querySelector('button[aria-label="Next page"]'));
  expect(address).toBe("?period=12m&page=2");
  await act(() =>
    vi.waitFor(() =>
      expect(page.querySelector('[role="status"]').textContent).toBe(
        "11-13 of 13",
      ),
    ),
  );
  expect(table(page)).toHaveLength(4);
  expect(table(page).at(-1)[0]).toBe("All 13 clients");
});

it("empty period message", async () => {
  const page = await open(
    "/customers/sales?period=30d",
    {
      "/customer/sales": {
        ...fixture.sales,
        totalClients: 0,
        numberOfPages: 0,
        totals: { documents: 0, billed: 0, paid: 0, owed: 0 },
        clients: [],
      },
    },
    (element) =>
      vi.waitFor(() =>
        expect(element.textContent).toContain(
          "No one was billed in this period.",
        ),
      ),
  );
  expect(page.textContent).toContain(
    "Clients show up here once you bill them, with what they paid and what they still owe.",
  );
  expect(page.querySelector("table")).toBeNull();
  expect(select(page, "Issued")).toBeTruthy();
});

it("defaults to all dates", async () => {
  const page = await open("/customers/sales", {
    "/customer/sales": {
      ...fixture.sales,
      period: {
        key: "",
        from: null,
        to: null,
        previousFrom: null,
        previousTo: null,
      },
    },
  });
  expect(sales()).toEqual([
    "/api/v1/customer/sales?sort=billed&dir=desc&page=1&size=10",
  ]);
  expect(select(page, "Issued").textContent).toContain("All dates");
  expect(page.textContent).toContain("on the documents issued at any time");
});

it("shows placeholder while loading", async () => {
  let answer;
  const page = await open(
    "/customers/sales?period=90d",
    {
      "/customer/sales": new Promise((resolve) => {
        answer = resolve;
      }),
    },
    () => Promise.resolve(),
  );
  expect(page.querySelector('[role="status"]').textContent).toBe(
    "Loading workspace data...",
  );
  expect(page.querySelector("table")).toBeNull();
  await act(async () => answer(fixture.sales));
  await act(() => listed(page));
  expect(table(page)).toHaveLength(11);
});

it("load error with retry", async () => {
  let fails = true;
  const page = await open(
    "/customers/sales?period=90d",
    {
      "/customer/sales": () =>
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
  fails = false;
  await click(
    [...alert.querySelectorAll("button")].find(
      (button) => button.textContent === "Try again",
    ),
  );
  await act(() => listed(page));
  expect(table(page)).toHaveLength(11);
});

it("shares the clients tab row", async () => {
  const page = await open(
    "/customers",
    {},
    (element) =>
      vi.waitFor(() =>
        expect(
          element.querySelector('nav[aria-label="Clients views"]'),
        ).not.toBeNull(),
      ),
    "CustomersPage",
  );
  expect(tabs(page)).toEqual([
    ["All clients", "/customers", "page"],
    ["Sales by customer", "/customers/sales", null],
  ]);
});

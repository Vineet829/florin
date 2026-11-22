import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";

vi.mock("../../dashboard/components/DashboardShell", () => ({
  default: ({ children }) => children,
}));

window.IS_REACT_ACT_ENVIRONMENT = true;

HTMLDialogElement.prototype.showModal = function showModal() {
  this.open = true;
};
HTMLDialogElement.prototype.close = function close() {
  this.open = false;
};

let requests;
let address;
let where;
let navigate;
let root;
let container;
let saves;
const { createObjectURL, revokeObjectURL } = URL;
beforeEach(() => {
  vi.resetModules();
  vi.stubEnv("VITE_API_URL", "http://localhost:3001/api/v1");
  requests = [];
  URL.createObjectURL = vi.fn(() => "blob:expenses");
  URL.revokeObjectURL = vi.fn();
  saves = vi
    .spyOn(HTMLAnchorElement.prototype, "click")
    .mockImplementation(() => {});
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
  saves.mockRestore();
  Object.assign(URL, { createObjectURL, revokeObjectURL });
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.useRealTimers();
});

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });

const expense = (id, fields) => ({
  _id: id,
  vendor: null,
  taxAmount: 0,
  taxReclaimable: false,
  currency: "USD",
  paymentMethod: "Credit Card",
  reference: null,
  notes: null,
  ...fields,
  total: fields.amount + (fields.taxAmount || 0),
});

const expenses = {
  success: true,
  totalExpenses: 3,
  numberOfPages: 1,
  from: null,
  to: null,
  expenses: [
    expense("e-3", {
      date: "2026-09-28",
      vendor: "Northwind Design Studio",
      category: "Contractors",
      amount: 1800,
    }),
    expense("e-2", {
      date: "2026-09-15",
      vendor: "Figma",
      category: "Software and subscriptions",
      amount: 45,
      taxAmount: 4.05,
      taxReclaimable: true,
    }),
    expense("e-1", {
      date: "2026-08-30",
      vendor: "Delta Air Lines",
      category: "Travel",
      amount: 412.6,
    }),
  ],
  totals: [{ currency: "USD", expenses: 3, spent: 2257.6 }],
};

const summary = {
  success: true,
  currency: "USD",
  currencies: ["USD"],
  outstanding: {
    total: 24285.79,
    overdue: 6326.03,
    notDue: 17959.76,
    invoices: 10,
    overdueInvoices: 3,
  },
  receivedLast30Days: { total: 22763.97, payments: 7 },
};

const business = (role, permissions) => ({
  businesses: [
    { _id: "b-1", name: "Studio Demo", role, active: true, permissions },
  ],
});
const owner = business("OWNER", [
  "VIEW_RECORDS",
  "EXPORT_LISTS",
  "EDIT_RECORDS",
  "DELETE_RECORDS",
  "EXPORT_ARCHIVE",
  "MANAGE_BUSINESS",
  "MANAGE_TEAM",
  "VIEW_TEAM_ACTIVITY",
  "OWN_BUSINESS",
]);
const viewer = business("VIEWER", ["VIEW_RECORDS", "EXPORT_LISTS"]);

const serve = (answers = {}) =>
  vi.stubGlobal(
    "fetch",
    vi.fn(async (request) => {
      const url = new URL(request.url);
      requests.push(`${url.pathname}${url.search}`);
      const answer = {
        "/expense": expenses,
        "/expense/categories": {
          categories: ["Contractors", "Software and subscriptions", "Travel"],
        },
        "/settings": {
          settings: { defaultCurrency: "USD", baseCurrency: "USD" },
        },
        "/expense/csv": () =>
          new Response("Date\n", { headers: { "content-type": "text/csv" } }),
        "/document/summary": summary,
        "/businesses": owner,
        ...answers,
      }[url.pathname.replace("/api/v1", "")];
      const body = await (typeof answer === "function" ? answer(url) : answer);
      if (body instanceof Response) return body;
      return body ? json(body) : json({ message: "Not found" }, 404);
    }),
  );

const listed = (page) =>
  vi.waitFor(() => expect(page.querySelector("table")).not.toBeNull());

const open = async (path, answers, before = []) => {
  serve(answers);
  const { Provider } = await import("react-redux");
  const { MemoryRouter, useLocation, useNavigate } =
    await import("react-router-dom");
  const { store } = await import("../../../app/store");
  const { default: ExpensesPage } = await import("./ExpensesPage");
  const Address = () => {
    const location = useLocation();
    address = location.search;
    where = `${location.pathname}${location.search}`;
    navigate = useNavigate();
    return null;
  };
  container = document.body.appendChild(document.createElement("div"));
  root = createRoot(container);
  await act(async () => {
    root.render(
      <Provider store={store}>
        <MemoryRouter
          initialEntries={[...before, path]}
          initialIndex={before.length}
        >
          <ExpensesPage />
          <Address />
        </MemoryRouter>
      </Provider>,
    );
  });
  await act(() => listed(container));
  return container;
};

const listRequests = () =>
  requests.filter((request) => /\/expense(\?|$)/.test(request));
const select = (page, label) =>
  [...page.querySelectorAll("label")]
    .find((element) => element.textContent.startsWith(label))
    ?.querySelector('[role="combobox"]');
const shown = (combobox) => combobox.firstChild.firstChild.textContent;
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
const download = async (page) => {
  const saved = saves.mock.calls.length;
  await act(async () =>
    page
      .querySelector('button[aria-label="Download these expenses as CSV"]')
      .click(),
  );
  await act(() =>
    vi.waitFor(() => expect(saves).toHaveBeenCalledTimes(saved + 1)),
  );
};
const csvRequests = () =>
  requests.filter((request) => request.includes("/expense/csv"));

it("sends periods by name or dates", async () => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date(2026, 9, 4, 12));
  const page = await open("/expenses?category=Travel&period=30d");
  expect(listRequests()).toEqual([
    "/api/v1/expense?page=1&size=10&category=Travel&period=30d",
  ]);
  expect(shown(select(page, "Paid"))).toBe("Last 30 days");
  await download(page);

  await choose(select(page, "Paid"), "last-month");
  await act(() => vi.waitFor(() => expect(listRequests()).toHaveLength(2)));
  expect(address).toBe("?category=Travel&period=last-month");
  expect(listRequests()[1]).toBe(
    "/api/v1/expense?page=1&size=10&category=Travel&from=2026-09-01&to=2026-09-30",
  );
  await download(page);
  expect(csvRequests()).toEqual([
    "/api/v1/expense/csv?category=Travel&period=30d",
    "/api/v1/expense/csv?category=Travel&from=2026-09-01&to=2026-09-30",
  ]);
});

it("this year ends at business today", async () => {
  const zone = process.env.TZ;
  process.env.TZ = "Asia/Kolkata";
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2027-01-01T05:00:00+05:30"));
  try {
    const page = await open("/expenses?period=this-year", {
      "/businesses": {
        businesses: owner.businesses.map((entry) => ({
          ...entry,
          timeZone: "America/Los_Angeles",
        })),
      },
    });
    await act(() =>
      vi.waitFor(() =>
        expect(listRequests().at(-1)).toBe(
          "/api/v1/expense?page=1&size=10&from=2026-01-01&to=2026-12-31",
        ),
      ),
    );
    await download(page);
    expect(csvRequests()).toEqual([
      "/api/v1/expense/csv?from=2026-01-01&to=2026-12-31",
    ]);
  } finally {
    vi.useRealTimers();
    if (zone === undefined) delete process.env.TZ;
    else process.env.TZ = zone;
  }
});

it("no currency filter for one currency", async () => {
  const page = await open("/expenses");
  expect(requests).toContain("/api/v1/document/summary");
  expect(select(page, "Currency")).toBeUndefined();
});

it("filters by a chosen currency", async () => {
  const page = await open("/expenses", {
    "/document/summary": { ...summary, currencies: ["USD", "EUR"] },
  });
  await act(() =>
    vi.waitFor(() => expect(select(page, "Currency")).toBeDefined()),
  );
  const currency = select(page, "Currency");
  expect(await options(currency)).toEqual(["All currencies", "USD", "EUR"]);
  await choose(currency, "EUR");
  await act(() => vi.waitFor(() => expect(listRequests()).toHaveLength(2)));
  expect(address).toBe("?currency=EUR");
  expect(listRequests()[1]).toBe("/api/v1/expense?page=1&size=10&currency=EUR");
  await download(page);
  expect(csvRequests()).toEqual(["/api/v1/expense/csv?currency=EUR"]);
});

const contractors = (url) =>
  url.searchParams.get("category") === "Contractors"
    ? {
        ...expenses,
        totalExpenses: 2,
        from: "2026-07-07",
        to: "2026-10-04",
        expenses: [
          expenses.expenses[0],
          expense("e-0", {
            date: "2026-07-21",
            vendor: "Northwind Design Studio",
            category: "Contractors",
            amount: 1800,
          }),
        ],
        totals: [{ currency: "USD", expenses: 2, spent: 3600 }],
      }
    : expenses;

const line = (page) =>
  [...page.querySelectorAll("button")]
    .find((button) => button.textContent === "Clear filters")
    ?.parentElement.querySelector("p").textContent;

it("category link sets filters and total", async () => {
  const page = await open(
    "/expenses?category=Contractors&period=90d&currency=USD",
    { "/expense": contractors },
  );
  expect(listRequests()).toEqual([
    "/api/v1/expense?page=1&size=10&category=Contractors&period=90d&currency=USD",
  ]);
  expect(line(page)).toBe(
    "2 expenses from Jul 7 - Oct 4, 2026, ·Spent 3,600.00",
  );
  expect(select(page, "Category").value).toBe("Contractors");
  expect(select(page, "Currency").value).toBe("USD");
  const clear = [...page.querySelectorAll("button")].find(
    (button) => button.textContent === "Clear filters",
  );
  await act(async () => clear.click());
  expect(address).toBe("");
  await act(() => vi.waitFor(() => expect(listRequests()).toHaveLength(2)));
  expect(listRequests()[1]).toBe("/api/v1/expense?page=1&size=10");
  expect(line(page)).toBeUndefined();
  expect(select(page, "Currency")).toBeUndefined();
  expect(document.activeElement).toBe(
    page.querySelector('input[type="search"]'),
  );
});

it("no summary without filters", async () => {
  const page = await open("/expenses");
  expect(page.querySelectorAll("tbody tr")).toHaveLength(3);
  expect(line(page)).toBeUndefined();
});

const dialog = () => document.querySelector("dialog");
const dayPaid = () => dialog().querySelector('button[aria-haspopup="dialog"]');
const button = (element, text) =>
  [...element.querySelectorAll("button")].find((candidate) =>
    candidate.textContent.includes(text),
  );

it("opens add dialog from link", async () => {
  const page = await open("/expenses?new=1&period=90d", {}, ["/dashboard"]);
  await act(() =>
    vi.waitFor(() => expect(dialog().querySelector("form")).not.toBeNull()),
  );
  expect(dialog().open).toBe(true);
  expect(dialog().querySelector("h2").textContent).toBe("New expense");
  expect(document.activeElement).toBe(dayPaid());
  expect(where).toBe("/expenses?period=90d");
  expect(listRequests()).toEqual(["/api/v1/expense?page=1&size=10&period=90d"]);
  await act(async () => button(dialog(), "Cancel").click());
  expect(dialog().open).toBe(false);
  expect(where).toBe("/expenses?period=90d");
  expect(document.activeElement).toBe(button(page, "New expense"));
  await act(async () => navigate(-1));
  expect(where).toBe("/dashboard");
  expect(dialog().open).toBe(false);
});

it("waits for settings before opening", async () => {
  let answer;
  const page = await open("/expenses?new=1", {
    "/settings": () => new Promise((resolve) => (answer = resolve)),
  });
  await act(() => vi.waitFor(() => expect(answer).toBeDefined()));
  expect(dialog().open).toBe(false);
  expect(where).toBe("/expenses?new=1");
  await act(async () =>
    answer({ settings: { defaultCurrency: "USD", baseCurrency: "USD" } }),
  );
  await act(() => vi.waitFor(() => expect(dialog().open).toBe(true)));
  expect(document.activeElement).toBe(dayPaid());
  expect(where).toBe("/expenses");
  expect(page.querySelector("h1").textContent).toBe("Expenses.");
});

it("ignores other values of new", async () => {
  await open("/expenses?new=0");
  await act(() =>
    vi.waitFor(() =>
      expect(requests).toEqual(
        expect.arrayContaining(["/api/v1/settings", "/api/v1/businesses"]),
      ),
    ),
  );
  await act(() => new Promise((resolve) => setTimeout(resolve, 20)));
  expect(dialog().open).toBe(false);
  expect(dialog().querySelector("form")).toBeNull();
});

it("no add dialog for viewers", async () => {
  const page = await open("/expenses?new=1", { "/businesses": viewer });
  await act(() => vi.waitFor(() => expect(address).toBe("")));
  expect(dialog().open).toBe(false);
  expect(dialog().querySelector("form")).toBeNull();
  const add = button(page, "New expense");
  expect(add.disabled).toBe(true);
  expect(add.title).toBe("Viewers can't change anything in this business");
});

import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import fixture from "./ReceivablesPage.fixture.json";

vi.mock("../../dashboard/components/DashboardShell", () => ({
  default: ({ children }) => children,
}));
const toast = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock("react-toastify", () => ({ toast }));

window.IS_REACT_ACT_ENVIRONMENT = true;

HTMLDialogElement.prototype.showModal = function showModal() {
  this.open = true;
};
HTMLDialogElement.prototype.close = function close() {
  this.open = false;
};

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
  toast.success.mockClear();
  toast.error.mockClear();
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
]);
const viewer = business("VIEWER", ["VIEW_RECORDS", "EXPORT_LISTS"]);

const GADRE = "00000000-0000-0000-0000-000000001026";
const draft = {
  success: true,
  to: "orders@gadremess.example",
  subject: "A reminder about invoice INV-1026",
  message: "Hello, invoice INV-1026 was due on Aug 11, 2026.",
  note: null,
};

const invoices = (url) =>
  url.searchParams.get("overdue") === "true"
    ? fixture.overdue
    : fixture.comingIn;

const serve = (answers = {}) =>
  vi.stubGlobal(
    "fetch",
    vi.fn(async (request) => {
      const url = new URL(request.url);
      requests.push(
        `${request.method === "GET" ? "" : `${request.method} `}${url.pathname}${url.search}`,
      );
      const path = url.pathname.replace("/api/v1", "");
      const answer = {
        "/document/receivables": fixture.receivables,
        "/document/all": invoices,
        "/businesses": owner,
        "/user/profile": { userProfile: { demo: false } },
        [`/document/${GADRE}/remind`]: (_url, sent) =>
          sent.method === "POST"
            ? { success: true, message: "Reminder sent successfully." }
            : draft,
        ...answers,
      }[path];
      const body = await (typeof answer === "function"
        ? answer(url, request)
        : answer);
      if (body instanceof Response) return body;
      return body ? json(body) : json({ message: "Not found" }, 404);
    }),
  );

const listed = (page) =>
  vi.waitFor(() => expect(page.querySelector("tbody")).not.toBeNull());

const open = async (path, answers, ready = listed) => {
  serve(answers);
  const { Provider } = await import("react-redux");
  const { MemoryRouter, useLocation } = await import("react-router-dom");
  const { store } = await import("../../../app/store");
  const { default: ReceivablesPage } = await import("./ReceivablesPage");
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
          <ReceivablesPage />
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
const links = (element) =>
  [...element.querySelectorAll("a")].map((link) => [
    link.textContent,
    link.getAttribute("href"),
  ]);
const pill = (page, label) =>
  [...page.querySelectorAll('input[name="receivables-mode"]')].find(
    (input) => input.parentElement.textContent === label,
  );
const bar = (page) =>
  [...summary(page).querySelector(".h-3\\.5").children].map((part) => [
    part.className.match(/bg-owed-[\w/-]+/)[0],
    parseFloat(part.style.flexGrow),
  ]);
const legend = (page) =>
  [...summary(page).querySelectorAll("ul > li .sr-only")].map(
    (words) => words.textContent,
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

it("shows incoming invoices", async () => {
  const page = await open("/receivables");
  expect(document.title).toBe("Receivables - Florin");
  expect(page.querySelector("h1").textContent).toBe("Documents.");
  expect(documents()).toEqual([
    "/api/v1/document/receivables",
    "/api/v1/document/all?page=1&size=10&sort=dueDate&dir=asc&outstanding=true&due=current&currency=USD",
  ]);
  expect(pill(page, "Coming in").checked).toBe(true);
  expect(summary(page).textContent).toContain(
    "Coming in17,959.76on 7 invoicesOf 24,285.79 owed on 10 invoices, as of today.",
  );
  expect(bar(page)).toEqual([
    ["bg-owed-due", 11194.74],
    ["bg-owed-due/70", 6277.02],
    ["bg-owed-due/45", 488],
  ]);
  expect(legend(page)).toEqual([
    "Due in 0-15 days: USD 11,194.74 on 4 invoices",
    "Due in 16-30 days: USD 6,277.02 on 2 invoices",
    "Due in 31-45 days: USD 488.00 on 1 invoice",
    "Due after 45 days: USD 0.00 on 0 invoices",
  ]);
  const owes = summary(page).querySelector("table");
  expect(headings(owes)).toEqual(["Client", "Invoices", "Owed"]);
  expect(table(owes)).toEqual([
    ["Nisarg Organic Farms", "1", "4,346.52"],
    ["Mehta Bakers", "1", "3,217.50"],
    ["Bhide Nursing Home", "1", "2,762.75"],
    ["Shinde Constructions", "1", "2,664.09"],
    ["Pai Electricals", "1", "2,550.40"],
  ]);
  expect(links(owes)).toEqual([
    ["Mehta Bakers", "/single-customer/00000000-0000-0000-0000-00000000c110"],
    [
      "Bhide Nursing Home",
      "/single-customer/00000000-0000-0000-0000-00000000c111",
    ],
    [
      "Shinde Constructions",
      "/single-customer/00000000-0000-0000-0000-00000000c103",
    ],
    [
      "Pai Electricals",
      "/single-customer/00000000-0000-0000-0000-00000000c101",
    ],
  ]);
  expect(list(page).querySelector("h2").textContent).toBe("Invoices coming in");
  expect(headings(list(page))).toEqual([
    "Invoice",
    "Client",
    "Due date",
    "Due",
    "Balance",
  ]);
  expect(table(list(page))).toEqual([
    [
      "INV-1032",
      "Shinde Constructions",
      "Oct 6, 2026",
      "Due in 2 days",
      "USD2,664.09",
    ],
    [
      "INV-1034",
      "Pai Electricals",
      "Oct 12, 2026",
      "Due in 8 days",
      "USD2,550.40",
    ],
    [
      "INV-1035",
      "Mehta Bakers",
      "Oct 15, 2026",
      "Due in 11 days",
      "USD3,217.50",
    ],
    [
      "INV-1036",
      "Nisarg Organic Farms",
      "Oct 24, 2026",
      "Due in 20 days",
      "USD4,346.52",
    ],
    [
      "INV-1037",
      "Iyer Dance Academy",
      "Oct 31, 2026",
      "Due in 27 days",
      "USD1,930.50",
    ],
    [
      "INV-1038",
      "Kale & Deshpande Architects",
      "Nov 16, 2026",
      "Due in 43 days",
      "USD488.00",
    ],
    ["INV-1039", "Bhide Nursing Home", "-", "No due date", "USD2,762.75"],
  ]);
  expect(links(list(page))[0]).toEqual([
    "INV-1032",
    "/document/00000000-0000-0000-0000-000000001032",
  ]);
  expect(list(page).querySelector("button[aria-label^='Send']")).toBeNull();
  expect(page.querySelector('[role="status"]').textContent).toBe("1-7 of 7");
});

it("shows overdue invoices", async () => {
  const page = await open("/receivables?mode=overdue");
  expect(documents()).toEqual([
    "/api/v1/document/receivables",
    "/api/v1/document/all?page=1&size=10&sort=dueDate&dir=asc&overdue=true&outstanding=true&currency=USD",
  ]);
  expect(pill(page, "Overdue").checked).toBe(true);
  expect(summary(page).textContent).toContain(
    "Overdue6,326.03on 3 invoicesOf 24,285.79 owed on 10 invoices, as of today.",
  );
  expect(bar(page)).toEqual([
    ["bg-owed-age-1", 965.25],
    ["bg-owed-age-2", 1969.11],
    ["bg-owed-age-4", 3391.67],
  ]);
  expect(legend(page)).toEqual([
    "1-15 days late: USD 965.25 on 1 invoice",
    "16-30 days late: USD 1,969.11 on 1 invoice",
    "31-45 days late: USD 0.00 on 0 invoices",
    "Over 45 days late: USD 3,391.67 on 1 invoice",
  ]);
  expect(
    [...summary(page).querySelectorAll("li .rounded-\\[3px\\]")].map(
      (swatch) => swatch.className.match(/bg-owed-[\w-]+/)[0],
    ),
  ).toEqual([
    "bg-owed-age-1",
    "bg-owed-age-2",
    "bg-owed-age-3",
    "bg-owed-age-4",
  ]);
  const owes = summary(page).querySelector("table");
  expect(table(owes)).toEqual([
    ["Gadre Mess", "1", "3,391.67"],
    ["Deshmukh & Associates", "1", "1,969.11"],
    ["Nisarg Organic Farms", "1", "965.25"],
  ]);
  expect(list(page).querySelector("h2").textContent).toBe("Overdue invoices");
  expect(headings(list(page))).toEqual([
    "Invoice",
    "Client",
    "Due date",
    "Late",
    "Balance",
    "Remind",
  ]);
  expect(table(list(page))).toEqual([
    [
      "INV-1026",
      "Gadre Mess",
      "Aug 11, 2026, overdue",
      "54 days late",
      "USD3,391.67",
      "Send reminder",
    ],
    [
      "INV-1030",
      "Deshmukh & Associates",
      "Sep 13, 2026, overdue",
      "21 days late",
      "USD1,969.11",
      "Send reminder",
    ],
    [
      "INV-1031",
      "Nisarg Organic Farms",
      "Sep 24, 2026, overdue",
      "10 days late",
      "USD965.25",
      "Send reminder",
    ],
  ]);
  expect(
    [...list(page).querySelectorAll("tbody button")].map((button) => [
      button.getAttribute("aria-label"),
      button.disabled,
    ]),
  ).toEqual([
    ["Send a reminder for INV-1026", false],
    ["Send a reminder for INV-1030", false],
    ["Send a reminder for INV-1031", false],
  ]);
});

it("switches mode with the pills", async () => {
  const page = await open("/receivables");
  await click(pill(page, "Overdue"));
  expect(address).toBe("?mode=overdue");
  await act(() =>
    vi.waitFor(() => expect(headings(list(page))).toContain("Late")),
  );
  expect(table(list(page))[0][0]).toBe("INV-1026");
  await click(pill(page, "Coming in"));
  expect(address).toBe("");
  await act(() =>
    vi.waitFor(() => expect(table(list(page))[0][0]).toBe("INV-1032")),
  );
  expect(
    documents().filter((request) => request.includes("/receivables")),
  ).toHaveLength(1);
});

it("counts days from business today", async () => {
  const zone = process.env.TZ;
  process.env.TZ = "Asia/Kolkata";
  try {
    vi.setSystemTime(new Date("2026-10-05T05:00:00+05:30"));
    const page = await open("/receivables", {
      "/businesses": {
        businesses: owner.businesses.map((entry) => ({
          ...entry,
          timeZone: "America/Los_Angeles",
        })),
      },
    });
    const days = () => table(list(page)).map((row) => row[3]);
    await act(() =>
      vi.waitFor(() =>
        expect(days()).toEqual([
          "Due in 2 days",
          "Due in 8 days",
          "Due in 11 days",
          "Due in 20 days",
          "Due in 27 days",
          "Due in 43 days",
          "No due date",
        ]),
      ),
    );
    await click(pill(page, "Overdue"));
    await act(() =>
      vi.waitFor(() =>
        expect(days()).toEqual([
          "54 days late",
          "21 days late",
          "10 days late",
        ]),
      ),
    );
  } finally {
    if (zone === undefined) delete process.env.TZ;
    else process.env.TZ = zone;
  }
});

const twoPages = (url) => ({
  ...fixture.overdue,
  totalDocuments: 12,
  numberOfPages: 2,
  page: Number(url.searchParams.get("page")),
  myDocuments:
    url.searchParams.get("page") === "2"
      ? fixture.overdue.myDocuments.slice(0, 2)
      : fixture.overdue.myDocuments,
});

it("new mode starts at page one", async () => {
  const page = await open("/receivables?mode=overdue&page=2", {
    "/document/all": twoPages,
  });
  expect(documents()[1]).toBe(
    "/api/v1/document/all?page=2&size=10&sort=dueDate&dir=asc&overdue=true&outstanding=true&currency=USD",
  );
  expect(page.querySelector('[role="status"]').textContent).toBe("11-12 of 12");
  await click(page.querySelector('button[aria-label="First page"]'));
  expect(address).toBe("?mode=overdue");
  await click(page.querySelector('button[aria-label="Next page"]'));
  expect(address).toBe("?mode=overdue&page=2");
  await click(pill(page, "Coming in"));
  expect(address).toBe("");
});

it("clamps page past the end", async () => {
  await open("/receivables?mode=overdue&page=5");
  await act(() => vi.waitFor(() => expect(address).toBe("?mode=overdue")));
});

const inEuros = {
  "/document/receivables": (url) => ({
    ...fixture.receivables,
    currencies: ["USD", "EUR"],
    currency: url.searchParams.get("currency") || "USD",
  }),
};

it("currency menu for several currencies", async () => {
  const page = await open("/receivables?mode=overdue", inEuros);
  const currency = select(page, "Currency");
  expect(currency.textContent).toContain("USD");
  expect(await options(currency)).toEqual(["USD", "EUR"]);
  expect(summary(page).textContent).toContain("USD6,326.03on 3 invoices");
  expect(summary(page).textContent).toContain("Of USD 24,285.79 owed");
  await choose(currency, "EUR");
  expect(address).toBe("?mode=overdue&currency=EUR");
  await act(() =>
    vi.waitFor(() =>
      expect(documents()).toContain(
        "/api/v1/document/all?page=1&size=10&sort=dueDate&dir=asc&overdue=true&outstanding=true&currency=EUR",
      ),
    ),
  );
  expect(documents()).toContain("/api/v1/document/receivables?currency=EUR");
  await choose(select(page, "Currency"), "USD");
  expect(address).toBe("?mode=overdue");
});

it("no currency menu for one", async () => {
  const page = await open("/receivables");
  expect(select(page, "Currency")).toBeUndefined();
});

const nothing = (group) => ({
  amount: 0,
  invoices: 0,
  bands: [],
  clients: [],
  ...group,
});
const empty = {
  success: true,
  totalDocuments: 0,
  numberOfPages: 0,
  page: 1,
  size: 10,
  myDocuments: [],
  totals: [],
};

it("nothing overdue message", async () => {
  const page = await open(
    "/receivables?mode=overdue",
    {
      "/document/receivables": {
        ...fixture.receivables,
        total: 17959.76,
        invoices: 7,
        overdue: nothing(),
      },
      "/document/all": empty,
    },
    (element) =>
      vi.waitFor(() =>
        expect(element.textContent).toContain("Nothing is overdue."),
      ),
  );
  expect(summary(page).textContent).toContain("Overdue0.00Nothing is overdue");
  expect(bar(page)).toEqual([]);
  expect(legend(page)).toEqual([
    "1-15 days late: USD 0.00 on 0 invoices",
    "16-30 days late: USD 0.00 on 0 invoices",
    "31-45 days late: USD 0.00 on 0 invoices",
    "Over 45 days late: USD 0.00 on 0 invoices",
  ]);
  expect(summary(page).textContent).toContain("No one is late paying.");
  expect(summary(page).querySelector("table")).toBeNull();
  expect(list(page).textContent).toContain(
    "Invoices past their due date show up here, most late first, with a way to remind the client.",
  );
  expect(list(page).querySelector("table")).toBeNull();
});

it("nothing incoming message", async () => {
  const page = await open(
    "/receivables",
    {
      "/document/receivables": {
        ...fixture.receivables,
        total: 6326.03,
        invoices: 3,
        current: nothing(),
      },
      "/document/all": empty,
    },
    (element) =>
      vi.waitFor(() =>
        expect(element.textContent).toContain("Nothing is coming in."),
      ),
  );
  expect(summary(page).textContent).toContain(
    "Coming in0.00Nothing is coming in",
  );
  expect(summary(page).textContent).toContain(
    "No one owes anything that isn't due yet.",
  );
  expect(list(page).textContent).toContain(
    "Unpaid invoices that aren't due yet show up here, soonest due first.",
  );
});

it("loads summary before invoices", async () => {
  let answer;
  const page = await open(
    "/receivables",
    {
      "/document/receivables": new Promise((resolve) => {
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
  expect(page.textContent).not.toContain("17,959.76");
  expect(documents()).toEqual(["/api/v1/document/receivables"]);
  await act(async () => answer(fixture.receivables));
  await act(() => listed(page));
  expect(summary(page).textContent).toContain("17,959.76");
  expect(page.querySelector(".animate-ov-pulse")).toBeNull();
});

it("figures load error with retry", async () => {
  let fails = true;
  const page = await open(
    "/receivables",
    {
      "/document/receivables": () =>
        fails
          ? json({ message: "Something went wrong. Please try again." }, 500)
          : fixture.receivables,
    },
    (element) =>
      vi.waitFor(() =>
        expect(element.querySelector('[role="alert"]')).not.toBeNull(),
      ),
  );
  const alert = page.querySelector('[role="alert"]');
  expect(alert.textContent).toContain("We couldn't load this page.");
  expect(documents().some((request) => request.includes("/document/all"))).toBe(
    false,
  );
  expect(page.querySelector("table")).toBeNull();
  fails = false;
  await click(
    [...alert.querySelectorAll("button")].find(
      (button) => button.textContent === "Try again",
    ),
  );
  await act(() => listed(page));
  expect(summary(page).textContent).toContain("17,959.76");
});

it("invoices load error keeps figures", async () => {
  const page = await open(
    "/receivables",
    {
      "/document/all": () =>
        json({ message: "The list is taking a break." }, 500),
    },
    (element) =>
      vi.waitFor(() =>
        expect(element.querySelector('[role="alert"]')).not.toBeNull(),
      ),
  );
  expect(summary(page).textContent).toContain("17,959.76");
  const alert = list(page).querySelector('[role="alert"]');
  expect(alert.textContent).toContain("We couldn't load this page.");
  expect(alert.textContent).toContain("The list is taking a break.");
});

const remindButton = (page) =>
  list(page).querySelector('button[aria-label="Send a reminder for INV-1026"]');
const sendReminder = async (page) => {
  await click(remindButton(page));
  const dialog = document.querySelector("dialog[open]");
  await act(() =>
    vi.waitFor(() => expect(dialog.querySelector("form")).not.toBeNull()),
  );
  expect(dialog.querySelector("h2").textContent).toBe("Send reminder");
  expect(dialog.querySelector('input[type="email"]').value).toBe(draft.to);
  await act(async () => {
    dialog.querySelector("form").requestSubmit();
  });
  return dialog;
};

it("sends a reminder", async () => {
  const page = await open("/receivables?mode=overdue");
  const dialog = await sendReminder(page);
  await act(() =>
    vi.waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith("Reminder sent successfully."),
    ),
  );
  expect(requests).toContain(`POST /api/v1/document/${GADRE}/remind`);
  expect(toast.error).not.toHaveBeenCalled();
  expect(dialog.open).toBe(false);
});

it("shows reminder failure reason", async () => {
  const page = await open("/receivables?mode=overdue", {
    [`/document/${GADRE}/remind`]: (_url, sent) =>
      sent.method === "POST"
        ? json({ message: "Gadre Mess has no email address." }, 400)
        : draft,
  });
  const dialog = await sendReminder(page);
  await act(() =>
    vi.waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith(
        "Gadre Mess has no email address.",
      ),
    ),
  );
  expect(toast.success).not.toHaveBeenCalled();
  expect(dialog.open).toBe(true);
});

it("default reminder failure message", async () => {
  const page = await open("/receivables?mode=overdue", {
    [`/document/${GADRE}/remind`]: (_url, sent) =>
      sent.method === "POST" ? new Response(null, { status: 502 }) : draft,
  });
  await sendReminder(page);
  await act(() =>
    vi.waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith(
        "The reminder couldn't be sent.",
      ),
    ),
  );
});

it("disables reminders for viewers", async () => {
  const page = await open("/receivables?mode=overdue", {
    "/businesses": viewer,
  });
  await act(() =>
    vi.waitFor(() => expect(remindButton(page).disabled).toBe(true)),
  );
  const reasons = [...list(page).querySelectorAll("tbody button")].map(
    (button) => [
      button.disabled,
      button.title,
      document.getElementById(button.getAttribute("aria-describedby"))
        .textContent,
    ],
  );
  expect(reasons).toEqual(
    Array(3).fill([
      true,
      "Viewers can't change anything in this business",
      "Viewers can't change anything in this business",
    ]),
  );
  await click(remindButton(page));
  expect(document.querySelector("dialog[open]")).toBeNull();
});

it("disables reminders for the demo", async () => {
  const page = await open("/receivables?mode=overdue", {
    "/user/profile": { userProfile: { demo: true } },
  });
  await act(() =>
    vi.waitFor(() => expect(remindButton(page).disabled).toBe(true)),
  );
  expect(remindButton(page).title).toBe(
    "Emailing is turned off for the shared demo account.",
  );
});

it("shows the documents tab row", async () => {
  const page = await open("/receivables");
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
    ["Receivables", "/receivables", "page"],
    ["Sales", "/sales", null],
    ["Payments", "/payments", null],
    ["Recurring", "/recurring", null],
  ]);
});

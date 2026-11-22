import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";

vi.mock("../../dashboard/components/DashboardShell", () => ({
  default: ({ children }) => children,
}));

window.IS_REACT_ACT_ENVIRONMENT = true;

let address;
let root;
let container;
beforeEach(() => {
  vi.resetModules();
  vi.stubEnv("VITE_API_URL", "http://localhost:3001/api/v1");
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

const item = (id, name, unitPrice, unit = null) => ({
  _id: `i-${id}`,
  name,
  description: null,
  unitPrice,
  unit,
  taxRate: null,
  hsnSac: null,
});
const catalog = [
  item(1, "Motion graphics (per 15 seconds)", 650),
  item(2, "Logo and identity system", 3200),
  item(3, "Consulting (hourly)", 140, "hour"),
  item(4, "Front-end build (per page)", 380, "page"),
  item(5, "Brand strategy workshop", 1800),
  item(6, "Copywriting", 95, "hour"),
  item(7, "Photography (half day)", 900),
  item(8, "Website hosting", 45, "month"),
  item(9, "Illustration", 480),
  item(10, "Social media kit", 750),
  item(11, "Packaging design", 2400),
  item(12, "Print production", 300),
];

const owner = {
  businesses: [
    {
      _id: "b-1",
      name: "Studio Demo",
      role: "OWNER",
      active: true,
      permissions: ["VIEW_RECORDS", "EDIT_RECORDS", "DELETE_RECORDS"],
    },
  ],
};

const serve = (items = catalog, totalItems) =>
  vi.stubGlobal(
    "fetch",
    vi.fn(async (request) => {
      const path = new URL(request.url).pathname.replace("/api/v1", "");
      const listed = typeof items === "function" ? items() : items;
      const answer = {
        "/item": {
          success: true,
          totalItems: totalItems ?? listed.length,
          items: listed,
        },
        "/settings": { settings: { taxRegime: "STANDARD" } },
        "/businesses": owner,
      }[path];
      return answer ? json(answer) : json({ message: "Not found" }, 404);
    }),
  );

const open = async (path, items, totalItems) => {
  serve(items, totalItems);
  const { Provider } = await import("react-redux");
  const { MemoryRouter, useLocation } = await import("react-router-dom");
  const { store } = await import("../../../app/store");
  const { default: ItemsPage } = await import("./ItemsPage");
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
          <ItemsPage />
          <Address />
        </MemoryRouter>
      </Provider>,
    );
  });
  await act(() =>
    vi.waitFor(() =>
      expect(container.querySelector("table, h2 + p")).not.toBeNull(),
    ),
  );
  return container;
};

const names = (page) =>
  [...page.querySelectorAll("tbody tr")].map(
    (row) => row.querySelector('[data-label="Name"]').textContent,
  );
const searchBox = (page) => page.querySelector('input[type="search"]');
const line = (page) =>
  [...page.querySelectorAll("button")]
    .find((button) => button.textContent === "Clear search")
    ?.parentElement.querySelector("p")?.textContent;
const button = (page, text) =>
  [...page.querySelectorAll("button")].find(
    (candidate) => candidate.textContent === text,
  );
const type = (input, text) =>
  act(async () => {
    Object.getOwnPropertyDescriptor(
      HTMLInputElement.prototype,
      "value",
    ).set.call(input, text);
    input.dispatchEvent(new Event("input", { bubbles: true }));
  });
const press = (input, key) =>
  act(async () => {
    input.dispatchEvent(new KeyboardEvent("keydown", { key, bubbles: true }));
  });

it("lists all items without search", async () => {
  const page = await open("/items");
  expect(names(page)).toHaveLength(10);
  expect(searchBox(page).value).toBe("");
  expect(searchBox(page).closest("label").textContent).toBe("Search items");
  expect(line(page)).toBeUndefined();
  expect(address).toBe("");
});

it("spaces table columns evenly", async () => {
  const page = await open("/items");
  const table = page.querySelector("table");
  expect(table.className).toContain("tabled:justify-between");
  expect(table.style.gridTemplateColumns).toBe(
    Array(table.querySelectorAll("th").length).fill("auto").join(" "),
  );
});

it("applies and clears URL search", async () => {
  const page = await open("/items?q=Motion+graphics+%28per+15+seconds%29");
  expect(names(page)).toEqual(["Motion graphics (per 15 seconds)"]);
  expect(searchBox(page).value).toBe("Motion graphics (per 15 seconds)");
  expect(line(page)).toBe("1 item");

  await act(async () => button(page, "Clear search").click());
  expect(address).toBe("");
  expect(names(page)).toHaveLength(10);
  expect(line(page)).toBeUndefined();
  expect(searchBox(page).value).toBe("");
  expect(document.activeElement).toBe(searchBox(page));
});

it("keeps search in URL", async () => {
  const page = await open("/items");
  await type(searchBox(page), " hour ");
  await press(searchBox(page), "Enter");
  expect(address).toBe("?q=hour");
  expect(names(page)).toEqual(["Consulting (hourly)", "Copywriting"]);
  expect(line(page)).toBe("2 items");

  await type(searchBox(page), "LOGO");
  expect(address).toBe("?q=hour");
  await act(() => new Promise((resolve) => setTimeout(resolve, 350)));
  expect(address).toBe("?q=LOGO");
  expect(names(page)).toEqual(["Logo and identity system"]);
});

it("search resets to page one", async () => {
  const page = await open("/items");
  await act(async () => page.querySelector('[aria-label="Next page"]').click());
  expect(names(page)).toEqual(["Packaging design", "Print production"]);
  await type(searchBox(page), "design");
  await press(searchBox(page), "Enter");
  expect(names(page)).toEqual(["Packaging design"]);
  await type(searchBox(page), "");
  await press(searchBox(page), "Enter");
  expect(address).toBe("");
  expect(names(page)).toHaveLength(10);
  expect(names(page)[0]).toBe("Motion graphics (per 15 seconds)");
});

it("no match message with reset", async () => {
  const page = await open("/items?q=Other+lines");
  expect(page.querySelector("table")).toBeNull();
  expect(page.textContent).toContain("No items match.");
  expect(line(page)).toBeUndefined();
  await act(async () => button(page, "Clear search").click());
  expect(address).toBe("");
  expect(names(page)).toHaveLength(10);
  expect(document.activeElement).toBe(searchBox(page));
});

it("no search without items", async () => {
  const page = await open("/items", []);
  expect(page.textContent).toContain("No items yet.");
  expect(searchBox(page)).toBeNull();
});

it("keeps focus when search hides", async () => {
  let answered = 0;
  const page = await open("/items?q=hosting", () =>
    answered++ ? catalog : [],
  );
  expect(page.textContent).toContain("No items match.");
  await act(async () => button(page, "Clear search").click());
  expect(address).toBe("");
  expect(searchBox(page)).toBeNull();
  expect(page.textContent).toContain("No items yet.");
  expect(document.activeElement).toBe(document.body);

  const { store } = await import("../../../app/store");
  const { itemsApiSlice } = await import("../itemsApiSlice");
  await act(async () => {
    store.dispatch(itemsApiSlice.util.invalidateTags(["Item"]));
  });
  await act(() =>
    vi.waitFor(() => expect(page.querySelector("table")).not.toBeNull()),
  );
  expect(searchBox(page)).not.toBeNull();
  expect(document.activeElement).toBe(document.body);
});

it("shows the items tab row", async () => {
  const page = await open("/items");
  expect(
    [...page.querySelectorAll('nav[aria-label="Items views"] a')].map((tab) => [
      tab.textContent,
      tab.getAttribute("href"),
      tab.getAttribute("aria-current"),
    ]),
  ).toEqual([
    ["All items", "/items", "page"],
    ["Sales by item", "/items/sales", null],
  ]);
});

const cutNote = (page) =>
  [...page.querySelectorAll("p")].find((note) =>
    note.textContent.startsWith("Showing the first"),
  )?.textContent;

it("shows total for partial lists", async () => {
  const page = await open("/items", catalog, 1234);
  expect(cutNote(page)).toBe("Showing the first 12 of 1,234 items.");
  await type(searchBox(page), "zzz");
  await press(searchBox(page), "Enter");
  expect(page.textContent).toContain("No items match.");
  expect(cutNote(page)).toBe("Showing the first 12 of 1,234 items.");
});

it("no total for full lists", async () => {
  const page = await open("/items");
  expect(cutNote(page)).toBeUndefined();
});

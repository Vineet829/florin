import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { useState } from "react";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { Dialog } from "./Dialog";
import { DatePicker } from "./DatePicker";

window.IS_REACT_ACT_ENVIRONMENT = true;

HTMLDialogElement.prototype.showModal = function showModal() {
  this.open = true;
};
HTMLDialogElement.prototype.close = function close() {
  this.open = false;
};

let root;
let container;
beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date(2026, 9, 7, 10, 30));
  container = document.body.appendChild(document.createElement("div"));
  root = createRoot(container);
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  window.innerWidth = 1024;
  window.innerHeight = 768;
});

const Harness = ({ initial = "", onChange, ...props }) => {
  const [value, setValue] = useState(initial);
  return (
    <div>
      <label id="due-label" htmlFor="due">
        Due date
      </label>
      <DatePicker
        id="due"
        labelId="due-label"
        value={value}
        onChange={(next) => {
          setValue(next);
          onChange?.(next);
        }}
        {...props}
      />
    </div>
  );
};
const render = (element) => act(async () => root.render(element));
let shown = 0;
const show = (props) => render(<Harness key={(shown += 1)} {...props} />);

const trigger = () => container.querySelector("#due");
const calendar = () => document.querySelector('[aria-label^="Choose a"]');
const title = () => calendar().querySelector("h2").textContent;
const day = (text) => document.querySelector(`[data-day="${text}"]`);
const click = (element) => act(async () => element.click());
const press = (element, key, init = {}) =>
  act(async () =>
    element.dispatchEvent(
      new KeyboardEvent("keydown", {
        key,
        bubbles: true,
        cancelable: true,
        ...init,
      }),
    ),
  );
const nameOf = (element) =>
  element
    .getAttribute("aria-labelledby")
    .split(" ")
    .map((id) => document.getElementById(id).textContent.trim())
    .join(" ");

it("shows the chosen day in words", async () => {
  await show({ initial: "2026-10-21" });
  const button = trigger();
  expect(button.getAttribute("role")).toBe("combobox");
  expect(button.getAttribute("aria-haspopup")).toBe("dialog");
  expect(button.getAttribute("aria-expanded")).toBe("false");
  expect(button.textContent).toBe("Wed, Oct 21, 2026");
  expect(nameOf(button)).toBe("Due date Wed, Oct 21, 2026");
  await show({ initial: "" });
  expect(trigger().textContent).toBe("Pick a date");
  await show({ initial: "", placeholder: "Not yet" });
  expect(trigger().textContent).toBe("Not yet");
});

it("opens on the chosen month", async () => {
  await show({ initial: "2026-11-03" });
  await click(trigger());
  expect(trigger().getAttribute("aria-expanded")).toBe("true");
  expect(calendar().getAttribute("role")).toBe("dialog");
  expect(document.getElementById(trigger().getAttribute("aria-controls"))).toBe(
    calendar(),
  );
  expect(title()).toBe("November 2026");
  expect(
    [...calendar().querySelectorAll("tbody tr")].map(
      (week) => week.firstElementChild.textContent,
    ),
  ).toEqual(["1", "8", "15", "22", "29"]);
  expect(
    [...calendar().querySelectorAll("th")].map((cell) => cell.abbr),
  ).toEqual([
    "Sunday",
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday",
    "Friday",
    "Saturday",
  ]);
  const chosen = day("2026-11-03");
  expect(chosen.getAttribute("aria-pressed")).toBe("true");
  expect(chosen.getAttribute("aria-label")).toBe("Tuesday, November 3, 2026");
  expect(chosen.className).toContain("bg-spot-night");
  expect(document.activeElement).toBe(chosen);
  expect(
    [...calendar().querySelectorAll("[data-day]")].filter(
      (cell) => cell.tabIndex === 0,
    ),
  ).toEqual([chosen]);
  await click(trigger());
  await show({ initial: "" });
  await click(trigger());
  expect(title()).toBe("October 2026");
  const today = day("2026-10-07");
  expect(today.getAttribute("aria-current")).toBe("date");
  expect(today.getAttribute("aria-pressed")).toBe("false");
  expect(document.activeElement).toBe(today);
});

it("reports clicked day and closes", async () => {
  const onChange = vi.fn();
  await show({ initial: "2026-10-21", onChange });
  await click(trigger());
  await click(day("2026-10-30"));
  expect(onChange).toHaveBeenCalledWith("2026-10-30");
  expect(calendar()).toBeNull();
  expect(trigger().getAttribute("aria-expanded")).toBe("false");
  expect(trigger().textContent).toBe("Fri, Oct 30, 2026");
  expect(document.activeElement).toBe(trigger());
});

it("disables days outside limits", async () => {
  const onChange = vi.fn();
  await show({ initial: "", min: "2026-10-07", max: "2026-10-20", onChange });
  await click(trigger());
  const before = day("2026-10-06");
  expect(before.getAttribute("aria-disabled")).toBe("true");
  expect(before.className).toContain("cursor-default");
  await click(before);
  await click(day("2026-10-21"));
  expect(onChange).not.toHaveBeenCalled();
  expect(calendar()).not.toBeNull();
  expect(day("2026-10-20").hasAttribute("aria-disabled")).toBe(false);
  await show({ initial: "", min: "2026-12-14" });
  await click(trigger());
  expect(title()).toBe("December 2026");
  expect(document.activeElement).toBe(day("2026-12-14"));
});

it("navigates days with keyboard", async () => {
  const onChange = vi.fn();
  await show({ initial: "2026-10-21", onChange });
  await click(trigger());
  const moves = [
    ["ArrowRight", {}, "2026-10-22"],
    ["ArrowDown", {}, "2026-10-29"],
    ["ArrowDown", {}, "2026-11-05"],
    ["Home", {}, "2026-11-01"],
    ["End", {}, "2026-11-07"],
    ["ArrowLeft", {}, "2026-11-06"],
    ["ArrowUp", {}, "2026-10-30"],
    ["PageDown", {}, "2026-11-30"],
    ["PageUp", {}, "2026-10-30"],
    ["PageDown", { shiftKey: true }, "2027-10-30"],
    ["PageUp", { shiftKey: true }, "2026-10-30"],
  ];
  for (const [key, init, expected] of moves) {
    await press(document.activeElement, key, init);
    expect(document.activeElement.dataset.day).toBe(expected);
  }
  await press(document.activeElement, "ArrowRight");
  await press(document.activeElement, "PageDown");
  expect(document.activeElement.dataset.day).toBe("2026-11-30");
  await click(document.activeElement);
  expect(onChange).toHaveBeenCalledWith("2026-11-30");
  await press(trigger(), "ArrowDown");
  expect(calendar()).not.toBeNull();
});

it("month arrows and today button", async () => {
  const onChange = vi.fn();
  await show({ initial: "2026-10-21", onChange });
  await click(trigger());
  const next = calendar().querySelector('[aria-label="Next month"]');
  next.focus();
  await click(next);
  expect(title()).toBe("November 2026");
  expect(document.activeElement).toBe(next);
  await click(calendar().querySelector('[aria-label="Previous month"]'));
  await click(calendar().querySelector('[aria-label="Previous month"]'));
  expect(title()).toBe("September 2026");
  expect(calendar().querySelector("h2").getAttribute("aria-live")).toBe(
    "polite",
  );
  const today = [...calendar().querySelectorAll("button")].find(
    (button) => button.textContent === "Today",
  );
  await click(today);
  expect(onChange).toHaveBeenCalledWith("2026-10-07");
  await show({ initial: "", max: "2026-10-01" });
  await click(trigger());
  expect(
    [...calendar().querySelectorAll("button")].find(
      (button) => button.textContent === "Today",
    ).disabled,
  ).toBe(true);
});

it("closes without changing the date", async () => {
  const onChange = vi.fn();
  await show({ initial: "2026-10-21", onChange });
  await click(trigger());
  await press(document.activeElement, "Escape");
  expect(calendar()).toBeNull();
  expect(document.activeElement).toBe(trigger());
  await click(trigger());
  await act(async () =>
    document.body.dispatchEvent(new MouseEvent("mousedown", { bubbles: true })),
  );
  expect(calendar()).toBeNull();
  await click(trigger());
  await click(trigger());
  expect(calendar()).toBeNull();
  expect(onChange).not.toHaveBeenCalled();
});

it("traps Tab in the calendar", async () => {
  await show({ initial: "2026-10-21" });
  await click(trigger());
  const stops = [
    ...calendar().querySelectorAll('button:not([tabindex="-1"])'),
  ].map((button) => button.textContent || button.getAttribute("aria-label"));
  expect(stops).toEqual(["Today", "Previous month", "Next month", "21"]);
  await press(document.activeElement, "Tab");
  expect(document.activeElement.textContent).toBe("Today");
  await press(document.activeElement, "Tab", { shiftKey: true });
  expect(document.activeElement).toBe(day("2026-10-21"));
});

it("clears only optional dates", async () => {
  const onChange = vi.fn();
  await show({ initial: "2026-10-21", onChange });
  const clear = container.querySelector('[aria-label="Clear the date"]');
  await click(clear);
  expect(onChange).toHaveBeenCalledWith("");
  expect(trigger().textContent).toBe("Pick a date");
  expect(document.activeElement).toBe(trigger());
  expect(container.querySelector('[aria-label="Clear the date"]')).toBeNull();
  await show({ initial: "2026-10-21", required: true });
  expect(container.querySelector('[aria-label="Clear the date"]')).toBeNull();
});

it("syncs the native input", async () => {
  const onChange = vi.fn();
  await show({
    initial: "",
    required: true,
    name: "dueDate",
    min: "2026-10-07",
    max: "2026-12-31",
    onChange,
  });
  const input = container.querySelector("input");
  expect(input.type).toBe("date");
  expect(input.name).toBe("dueDate");
  expect([input.min, input.max, input.required]).toEqual([
    "2026-10-07",
    "2026-12-31",
    true,
  ]);
  expect(input.tabIndex).toBe(-1);
  expect(input.getAttribute("aria-hidden")).toBe("true");
  expect(input.validity.valueMissing).toBe(true);
  await act(async () => input.dispatchEvent(new Event("invalid")));
  expect(trigger().getAttribute("aria-invalid")).toBe("true");
  await act(async () => {
    Object.getOwnPropertyDescriptor(
      HTMLInputElement.prototype,
      "value",
    ).set.call(input, "2026-11-02");
    input.dispatchEvent(new Event("input", { bubbles: true }));
  });
  expect(onChange).toHaveBeenCalledWith("2026-11-02");
  expect(input.value).toBe("2026-11-02");
  expect(trigger().textContent).toBe("Mon, Nov 2, 2026");
  expect(trigger().hasAttribute("aria-invalid")).toBe(false);
});

it("picks a month in month mode", async () => {
  const onChange = vi.fn();
  await show({ type: "month", initial: "2026-08", max: "2026-10", onChange });
  expect(trigger().textContent).toBe("August 2026");
  expect(container.querySelector("input").type).toBe("month");
  await click(trigger());
  expect(calendar().getAttribute("aria-label")).toBe("Choose a month");
  expect(title()).toBe("2026");
  const months = [...calendar().querySelectorAll("[data-day]")];
  expect(months.map((month) => month.textContent)).toEqual([
    "Jan",
    "Feb",
    "Mar",
    "Apr",
    "May",
    "Jun",
    "Jul",
    "Aug",
    "Sep",
    "Oct",
    "Nov",
    "Dec",
  ]);
  expect(document.activeElement).toBe(day("2026-08"));
  expect(day("2026-10").getAttribute("aria-current")).toBe("date");
  expect(day("2026-11").getAttribute("aria-disabled")).toBe("true");
  await press(document.activeElement, "ArrowDown");
  expect(document.activeElement).toBe(day("2026-11"));
  await press(document.activeElement, "PageUp");
  expect(title()).toBe("2025");
  expect(document.activeElement).toBe(day("2025-11"));
  await click(day("2025-11"));
  expect(onChange).toHaveBeenCalledWith("2025-11");
  expect(trigger().textContent).toBe("November 2025");
  await click(trigger());
  await click(
    [...calendar().querySelectorAll("button")].find(
      (button) => button.textContent === "This month",
    ),
  );
  expect(onChange).toHaveBeenLastCalledWith("2026-10");
});

it("renders inside its dialog", async () => {
  await render(
    <Dialog open title="Record a payment" onClose={() => {}}>
      <Harness initial="2026-10-21" />
    </Dialog>,
  );
  await click(trigger());
  expect(calendar().closest("dialog")).toBe(document.querySelector("dialog"));
});

it("opens as a sheet on phones", async () => {
  vi.stubGlobal(
    "matchMedia",
    vi.fn((query) => ({
      matches: query === "(pointer: coarse)",
      addEventListener() {},
      removeEventListener() {},
    })),
  );
  const onChange = vi.fn();
  await show({ initial: "2026-10-21", onChange });
  await click(trigger());
  const sheet = document.querySelector("dialog");
  expect(sheet.open).toBe(true);
  expect(sheet.getAttribute("aria-label")).toBe("Choose a date");
  expect(day("2026-10-21").className).toContain("size-11");
  expect(document.activeElement).toBe(day("2026-10-21"));
  await click(day("2026-10-23"));
  expect(onChange).toHaveBeenCalledWith("2026-10-23");
  expect(document.querySelector("dialog")).toBeNull();
  expect(document.activeElement).toBe(trigger());
});

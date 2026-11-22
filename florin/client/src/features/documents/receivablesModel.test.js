import { describe, expect, it } from "vitest";
import {
  AGE_BANDS,
  DUE_BANDS,
  MODES,
  bandsOf,
  daysUntil,
  dueLabel,
  lateLabel,
  readReceivables,
  receivablesQuery,
  receivablesSearch,
} from "./receivablesModel";

const read = (search) => readReceivables(new URLSearchParams(search));
const OCT_4 = new Date(2026, 9, 4, 12);

describe("receivables URL state", () => {
  it("starts with the defaults", () => {
    expect(read("")).toEqual({
      mode: "coming-in",
      currency: "",
      page: 1,
      size: 10,
    });
    expect(receivablesSearch(read("")).toString()).toBe("");
  });

  it("round-trips URL parameters", () => {
    const list = read("page=2&currency=EUR&size=25&mode=overdue");
    expect(list).toEqual({
      mode: "overdue",
      currency: "EUR",
      page: 2,
      size: 25,
    });
    expect(receivablesSearch(list).toString()).toBe(
      "mode=overdue&currency=EUR&page=2&size=25",
    );
  });

  it("defaults for invalid values", () => {
    expect(read("mode=late&currency=euro&page=-1&size=7")).toEqual({
      mode: "coming-in",
      currency: "",
      page: 1,
      size: 10,
    });
  });

  it("lists the pill modes", () => {
    expect(MODES.map((mode) => [mode.value, mode.label])).toEqual([
      ["coming-in", "Coming in"],
      ["overdue", "Overdue"],
    ]);
  });
});

describe("invoice queries", () => {
  it("queries invoices not yet due", () => {
    expect(
      receivablesQuery({ mode: "coming-in", page: 1, size: 10 }, "USD"),
    ).toEqual({
      page: 1,
      size: 10,
      sort: "dueDate",
      dir: "asc",
      outstanding: "true",
      due: "current",
      currency: "USD",
    });
  });

  it("queries overdue invoices", () => {
    expect(
      receivablesQuery({ mode: "overdue", page: 3, size: 25 }, "EUR"),
    ).toEqual({
      page: 3,
      size: 25,
      sort: "dueDate",
      dir: "asc",
      overdue: "true",
      outstanding: "true",
      currency: "EUR",
    });
  });

  it("omits a missing currency", () => {
    expect(
      receivablesQuery({ mode: "overdue", page: 1, size: 10 }),
    ).not.toHaveProperty("currency");
  });
});

describe("days late or due", () => {
  it("counts whole days", () => {
    expect(daysUntil("2026-10-06", OCT_4)).toBe(2);
    expect(daysUntil("2026-10-04", OCT_4)).toBe(0);
    expect(daysUntil("2026-08-11", OCT_4)).toBe(-54);
    expect(daysUntil("2026-10-06", new Date(2026, 9, 4))).toBe(2);
  });

  it("labels days late", () => {
    expect(lateLabel("2026-08-11", OCT_4)).toBe("54 days late");
    expect(lateLabel("2026-10-03", OCT_4)).toBe("1 day late");
    expect(lateLabel("2026-10-04", OCT_4)).toBe("1 day late");
  });

  it("labels days until due", () => {
    expect(dueLabel("2026-10-04", OCT_4)).toBe("Due today");
    expect(dueLabel("2026-10-05", OCT_4)).toBe("Due tomorrow");
    expect(dueLabel("2026-11-16", OCT_4)).toBe("Due in 43 days");
    expect(dueLabel("2026-10-03", OCT_4)).toBe("Due today");
    expect(dueLabel(null, OCT_4)).toBe("No due date");
  });
});

describe("age bands", () => {
  it("orders overdue bands", () => {
    expect(
      AGE_BANDS.map(({ key, label, color }) => [key, label, color]),
    ).toEqual([
      ["days1to15", "1-15 days", "bg-owed-age-1"],
      ["days16to30", "16-30 days", "bg-owed-age-2"],
      ["days31to45", "31-45 days", "bg-owed-age-3"],
      ["over45", "Over 45 days", "bg-owed-age-4"],
    ]);
  });

  it("orders bands not yet due", () => {
    expect(
      DUE_BANDS.map(({ key, label, color }) => [key, label, color]),
    ).toEqual([
      ["dueIn0to15", "Due in 0-15 days", "bg-owed-due"],
      ["dueIn16to30", "Due in 16-30 days", "bg-owed-due/70"],
      ["dueIn31to45", "Due in 31-45 days", "bg-owed-due/45"],
      ["dueAfter45", "Due after 45 days", "bg-owed-due/25"],
    ]);
  });

  it("finds bands by key", () => {
    const late = { key: "over45", amount: 5, invoices: 2 };
    expect(bandsOf({ bands: [late] })).toEqual({ over45: late });
    expect(bandsOf({})).toEqual({});
    expect(bandsOf()).toEqual({});
  });
});

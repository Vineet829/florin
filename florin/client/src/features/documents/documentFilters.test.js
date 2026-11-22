import { describe, expect, it } from "vitest";
import {
  PERIODS,
  SORT_OPTIONS,
  TYPE_FILTERS,
  documentQuery,
  documentFilterQuery,
  documentResults,
  isFiltered,
  listSearch,
  periodRange,
  readListState,
} from "./documentFilters";

const read = (search) => readListState(new URLSearchParams(search));

describe("documents URL state", () => {
  it("starts with the defaults", () => {
    expect(read("")).toEqual({
      q: "",
      status: "",
      due: "",
      type: "",
      period: "",
      currency: "",
      sort: "createdAt",
      dir: "desc",
      page: 1,
      size: 10,
    });
  });

  it("reads URL parameters", () => {
    expect(
      read(
        "q=north&status=overdue&type=invoice&period=this-year&sort=total&dir=asc&page=3&size=25",
      ),
    ).toEqual({
      q: "north",
      status: "overdue",
      due: "",
      type: "invoice",
      period: "this-year",
      currency: "",
      sort: "total",
      dir: "asc",
      page: 3,
      size: 25,
    });
  });

  it("defaults for invalid values", () => {
    expect(
      read(
        "status=Maybe&type=memo&period=forever&due=someday&currency=usd&sort=password&dir=up&page=-2&size=7&extra=1",
      ),
    ).toEqual(read(""));
    expect(read("sort=status").sort).toBe("createdAt");
  });

  it("writes only changed values", () => {
    expect(listSearch(read("")).toString()).toBe("");
    const search = "q=a+b&status=paid&sort=createdAt&dir=asc&page=2&size=50";
    expect(listSearch(read(search)).toString()).toBe(search);
    expect(isFiltered(read("sort=total&page=2"))).toBe(false);
    expect(isFiltered(read("type=receipt"))).toBe(true);
    expect(isFiltered(read("period=last-month"))).toBe(true);
  });

  it("round-trips overview links", () => {
    const search =
      "status=outstanding&due=next-week&type=billed&period=90d&currency=EUR&sort=dueDate&dir=asc";
    expect(read(search)).toMatchObject({
      status: "outstanding",
      due: "next-week",
      type: "billed",
      period: "90d",
      currency: "EUR",
      sort: "dueDate",
      dir: "asc",
    });
    expect(listSearch(read(search)).toString()).toBe(search);
    for (const filter of [
      "status=outstanding",
      "due=later",
      "type=billed",
      "period=12m",
      "currency=INR",
    ]) {
      expect(isFiltered(read(filter))).toBe(true);
    }
  });

  it("accepts three-letter currencies only", () => {
    expect(read("currency=GBP").currency).toBe("GBP");
    for (const currency of ["gbp", "GBPX", "G1P", ""]) {
      expect(read(`currency=${currency}`).currency).toBe("");
    }
  });
});

describe("the server query", () => {
  it("sends the stored status", () => {
    expect(documentQuery(read("status=partial"))).toEqual({
      page: 1,
      size: 10,
      sort: "createdAt",
      dir: "desc",
      status: "Not Fully Paid",
    });
  });

  it("export query has no page", () => {
    expect(
      documentFilterQuery(read("status=partial&q=acme&sort=total&dir=asc")),
    ).toEqual({
      q: "acme",
      sort: "total",
      dir: "asc",
      status: "Not Fully Paid",
    });
  });

  it("queries overdue documents", () => {
    const query = documentQuery(read("status=overdue&type=invoice&q=inv"));
    expect(query).toMatchObject({ overdue: "true", type: "Invoice", q: "inv" });
    expect(query).not.toHaveProperty("status");
  });

  it("queries drafts by state", () => {
    const query = documentQuery(read("status=drafts&type=quotation"));
    expect(query).toMatchObject({ state: "draft", type: "Quotation" });
    expect(query).not.toHaveProperty("status");
    expect(query).not.toHaveProperty("overdue");
  });

  it("queries void documents by state", () => {
    const query = documentQuery(read("status=void"));
    expect(query).toMatchObject({ state: "void" });
    expect(query).not.toHaveProperty("status");
  });

  it("queries outstanding invoices", () => {
    expect(
      documentQuery(read("status=outstanding&sort=dueDate&dir=asc")),
    ).toEqual({
      page: 1,
      size: 10,
      sort: "dueDate",
      dir: "asc",
      outstanding: "true",
    });
  });

  it("queries billed documents", () => {
    const query = documentFilterQuery(read("type=billed"));
    expect(query).toEqual({ sort: "createdAt", dir: "desc", billed: "true" });
    expect(TYPE_FILTERS[1].label).toBe(
      "Billed: invoices, receipts and credit notes",
    );
  });

  it("passes currency and due week", () => {
    expect(
      documentFilterQuery(
        read("status=outstanding&due=week-after&currency=EUR"),
      ),
    ).toEqual({
      sort: "createdAt",
      dir: "desc",
      outstanding: "true",
      due: "week-after",
      currency: "EUR",
    });
    expect(documentQuery(read("due=this-week"))).toMatchObject({
      due: "this-week",
    });
  });
});

describe("issue date periods", () => {
  const today = new Date(2026, 8, 30, 23, 30);

  it.each([
    ["this-month", "2026-09-01", "2026-09-30"],
    ["last-month", "2026-08-01", "2026-08-31"],
    ["this-quarter", "2026-07-01", "2026-09-30"],
    ["this-year", "2026-01-01", "2026-09-30"],
    ["last-year", "2025-01-01", "2025-12-31"],
  ])("Sep 30 %s: %s to %s", (period, from, to) => {
    expect(periodRange(period, today)).toEqual({ from, to });
  });

  it.each([
    ["this-month", "2026-10-01", "2026-10-08"],
    ["last-month", "2026-09-01", "2026-09-30"],
    ["this-quarter", "2026-10-01", "2026-10-08"],
    ["this-year", "2026-01-01", "2026-10-08"],
    ["last-year", "2025-01-01", "2025-12-31"],
  ])("Oct 8 %s: %s to %s", (period, from, to) => {
    expect(periodRange(period, new Date(2026, 9, 8))).toEqual({ from, to });
  });

  it("handles the year boundary", () => {
    expect(periodRange("last-month", new Date(2027, 0, 15))).toEqual({
      from: "2026-12-01",
      to: "2026-12-31",
    });
    expect(periodRange("this-quarter", new Date(2027, 0, 15))).toEqual({
      from: "2027-01-01",
      to: "2027-01-15",
    });
    expect(periodRange("this-year", new Date(2027, 0, 1))).toEqual({
      from: "2027-01-01",
      to: "2027-01-01",
    });
  });

  it("sends period dates", () => {
    expect(documentQuery(read("period=this-month"), today)).toMatchObject({
      from: "2026-09-01",
      to: "2026-09-30",
    });
    expect(
      documentQuery(read("period=this-year"), new Date(2026, 9, 8)),
    ).toMatchObject({ from: "2026-01-01", to: "2026-10-08" });
    expect(documentQuery(read(""), today)).not.toHaveProperty("from");
  });

  it.each(["30d", "90d", "12m"])("sends %s by name", (period) => {
    const query = documentQuery(read(`period=${period}`), today);
    expect(query).toMatchObject({ period });
    expect(query).not.toHaveProperty("from");
    expect(query).not.toHaveProperty("to");
  });

  it("orders the period options", () => {
    expect(PERIODS.map((period) => period.label)).toEqual([
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
  });
});

describe("the sort menu", () => {
  it("lists both directions per field", () => {
    expect(SORT_OPTIONS).toHaveLength(10);
    expect(SORT_OPTIONS[0]).toEqual({
      value: "createdAt:desc",
      label: "Newest first",
    });
  });
});

describe("filter summary line", () => {
  const usd = (totals) => ({ currency: "USD", ...totals });
  const words = (parts) =>
    parts.map(({ before = "", strong = "", after = "" }) =>
      [before, strong, after].join(""),
    );

  it("counts documents and their total", () => {
    const parts = documentResults(read("type=billed&period=90d"), {
      totalDocuments: 18,
      issuedFrom: "2026-07-07",
      issuedTo: "2026-10-04",
      totals: [
        usd({
          documents: 18,
          billed: 68848.08,
          balanceDue: 24285.79,
          overdue: 6326.03,
          overdueDocuments: 3,
        }),
      ],
    });
    expect(words(parts)).toEqual([
      "18 documents issued Jul 7 - Oct 4, 2026",
      "Billed 68,848.08",
      "Balance due 24,285.79",
    ]);
    expect(parts.map((part) => part.strong)).toEqual([
      "18 documents",
      "68,848.08",
      "24,285.79",
    ]);
  });

  it("omits missing days", () => {
    expect(
      words(
        documentResults(read("period=this-year"), {
          totalDocuments: 2,
          issuedFrom: null,
          issuedTo: null,
          totals: [],
        }),
      ),
    ).toEqual(["2 documents"]);
  });

  it("shows owed and overdue amounts", () => {
    const answer = {
      totalDocuments: 10,
      issuedFrom: null,
      issuedTo: null,
      totals: [
        usd({
          documents: 10,
          billed: 40411.65,
          balanceDue: 24285.79,
          overdue: 6326.03,
          overdueDocuments: 3,
        }),
      ],
    };
    const parts = documentResults(
      read("status=outstanding&sort=dueDate&dir=asc"),
      answer,
    );
    expect(words(parts)).toEqual([
      "10 invoices with money owed",
      "Balance due 24,285.79",
      "6,326.03 overdue on 3",
    ]);
    expect(parts.at(-1).tone).toBe("overdue");
    expect(
      words(
        documentResults(read("status=outstanding&period=30d"), {
          ...answer,
          issuedFrom: "2026-09-05",
          issuedTo: "2026-10-04",
        }),
      )[0],
    ).toBe("10 invoices with money owed, issued Sep 5 - Oct 4, 2026");
  });

  it("names each currency's amount", () => {
    expect(
      words(
        documentResults(read("status=unpaid"), {
          totalDocuments: 4,
          totals: [
            { currency: "", documents: 1, billed: 300, balanceDue: 300 },
            { currency: "EUR", documents: 1, billed: 500, balanceDue: 0 },
            { currency: "USD", documents: 2, billed: 1200, balanceDue: 950 },
          ],
        }),
      ),
    ).toEqual([
      "4 documents",
      "Billed 300.00 with no currency, 500.00 EUR, 1,200.00 USD",
      "Balance due 300.00 with no currency, 950.00 USD",
    ]);
  });

  it("names currency for multi-currency business", () => {
    const answer = {
      totalDocuments: 2,
      totals: [usd({ documents: 2, billed: 1200, balanceDue: 950 })],
    };
    expect(
      words(documentResults(read("currency=USD"), answer, ["USD", "EUR"])),
    ).toEqual(["2 documents", "Billed 1,200.00 USD", "Balance due 950.00 USD"]);
    expect(
      words(documentResults(read("currency=USD"), answer, ["USD"])),
    ).toEqual(["2 documents", "Billed 1,200.00", "Balance due 950.00"]);
  });

  it("omits zero amounts", () => {
    expect(
      words(
        documentResults(read("type=credit-note"), {
          totalDocuments: 1,
          totals: [usd({ documents: 1, billed: -250, balanceDue: 0 })],
        }),
      ),
    ).toEqual(["1 document"]);
    expect(
      words(documentResults(read("status=drafts"), { totalDocuments: 0 })),
    ).toEqual(["0 documents"]);
  });

  it("groups rupees in lakhs", () => {
    expect(
      words(
        documentResults(read("q=north"), {
          totalDocuments: 2,
          totals: [
            {
              currency: "INR",
              documents: 2,
              billed: 150000,
              balanceDue: 25000,
            },
          ],
        }),
      ),
    ).toEqual(["2 documents", "Billed 1,50,000.00", "Balance due 25,000.00"]);
  });
});

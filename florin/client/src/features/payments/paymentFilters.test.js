import { describe, expect, it } from "vitest";
import {
  SORT_OPTIONS,
  isPaymentListFiltered,
  methodLabel,
  methodOptions,
  paymentListSearch,
  paymentQuery,
  paymentResults,
  paymentSummaryQuery,
  readPaymentList,
} from "./paymentFilters";

const read = (search) => readPaymentList(new URLSearchParams(search));

describe("payments URL state", () => {
  it("starts with the defaults", () => {
    expect(read("")).toEqual({
      q: "",
      method: "",
      period: "",
      currency: "",
      sort: "date",
      dir: "desc",
      page: 1,
      size: 10,
    });
    expect(paymentListSearch(read("")).toString()).toBe("");
    expect(isPaymentListFiltered(read("sort=amount&page=2"))).toBe(false);
  });

  it("round-trips URL parameters", () => {
    const search =
      "q=deshmukh&method=Bank+Transfer&period=90d&currency=USD&sort=amount&dir=asc&page=2&size=25";
    expect(read(search)).toEqual({
      q: "deshmukh",
      method: "Bank Transfer",
      period: "90d",
      currency: "USD",
      sort: "amount",
      dir: "asc",
      page: 2,
      size: 25,
    });
    expect(paymentListSearch(read(search)).toString()).toBe(search);
    for (const filter of [
      "q=inv",
      "method=receipt",
      "period=this-year",
      "currency=EUR",
    ]) {
      expect(isPaymentListFiltered(read(filter))).toBe(true);
    }
  });

  it("defaults for invalid values", () => {
    expect(
      read(
        "method=Barter&period=forever&currency=usd&sort=document&dir=up&page=0",
      ),
    ).toEqual(read(""));
    expect(read("sort=client&dir=desc")).toMatchObject({
      sort: "date",
      dir: "desc",
    });
    expect(read("sort=client")).toMatchObject({ sort: "client", dir: "asc" });
  });

  it("lists the sort options", () => {
    expect(SORT_OPTIONS).toEqual([
      { value: "date:desc", label: "Newest first" },
      { value: "date:asc", label: "Oldest first" },
      { value: "amount:desc", label: "Highest amount" },
      { value: "amount:asc", label: "Lowest amount" },
      { value: "client:asc", label: "Customer A-Z" },
    ]);
  });
});

describe("the server query", () => {
  const today = new Date(2026, 9, 4, 12);

  it("sends periods by name or dates", () => {
    expect(paymentQuery(read("period=30d"), today)).toEqual({
      page: 1,
      size: 10,
      sort: "date",
      dir: "desc",
      period: "30d",
    });
    expect(paymentQuery(read("period=last-month"), today)).toMatchObject({
      from: "2026-09-01",
      to: "2026-09-30",
    });
    expect(paymentQuery(read("period=last-month"), today)).not.toHaveProperty(
      "period",
    );
  });

  it("passes search and filters", () => {
    expect(
      paymentQuery(read("q=inv-10&method=receipt&currency=EUR&sort=client")),
    ).toEqual({
      page: 1,
      size: 10,
      sort: "client",
      dir: "asc",
      q: "inv-10",
      method: "receipt",
      currency: "EUR",
    });
  });
});

describe("rows and method menu", () => {
  it("names payment and refund methods", () => {
    expect(methodLabel({ kind: "payment", method: "Bank Transfer" })).toBe(
      "Bank Transfer",
    );
    expect(methodLabel({ kind: "receipt", method: null })).toBe("On a receipt");
    expect(methodLabel({ kind: "refund", method: "Credit Card" })).toBe(
      "Refund · Credit Card",
    );
    expect(methodLabel({ kind: "refund", method: null })).toBe("Refund");
  });

  it("lists used methods, then receipts", () => {
    expect(methodOptions(["Bank Transfer", "UPI"])).toEqual([
      { value: "", label: "All methods" },
      { value: "Bank Transfer", label: "Bank Transfer" },
      { value: "UPI", label: "UPI" },
      { value: "receipt", label: "On a receipt" },
    ]);
    expect(
      methodOptions(["UPI"], "Cheque").map((option) => option.value),
    ).toEqual(["", "UPI", "Cheque", "receipt"]);
    expect(methodOptions(undefined, "receipt")).toHaveLength(2);
  });
});

describe("filter summary line", () => {
  const words = (parts) =>
    parts.map(({ before = "", strong = "", after = "" }) =>
      [before, strong, after].join(""),
    );
  const ninetyDays = {
    total: 18,
    from: "2026-07-07",
    to: "2026-10-04",
    totals: [
      { currency: "USD", payments: 18, received: 44562.29, receipts: 2 },
    ],
  };

  it("counts payments and receipts", () => {
    expect(words(paymentResults(read("period=90d"), ninetyDays))).toEqual([
      "18 payments from Jul 7 - Oct 4, 2026",
      "Received 44,562.29",
      "2 of them on receipts",
    ]);
    expect(
      words(
        paymentResults(read("period=30d"), {
          total: 7,
          from: "2026-09-05",
          to: "2026-10-04",
          totals: [
            { currency: "USD", payments: 7, received: 22763.97, receipts: 1 },
          ],
        }),
      ),
    ).toEqual([
      "7 payments from Sep 5 - Oct 4, 2026",
      "Received 22,763.97",
      "1 of them on a receipt",
    ]);
  });

  it("omits empty days and receipts", () => {
    expect(
      words(
        paymentResults(read("q=deshmukh"), {
          total: 1,
          from: null,
          to: null,
          totals: [
            { currency: "USD", payments: 1, received: 262.76, receipts: 0 },
          ],
        }),
      ),
    ).toEqual(["1 payment", "Received 262.76"]);
    expect(
      words(
        paymentResults(read("method=receipt&period=90d"), {
          ...ninetyDays,
          total: 2,
          totals: [
            { currency: "USD", payments: 2, received: 12011.28, receipts: 2 },
          ],
        }),
      ),
    ).toEqual(["2 payments from Jul 7 - Oct 4, 2026", "Received 12,011.28"]);
  });

  it("totals per currency with refunds", () => {
    expect(
      words(paymentResults(read("currency=USD"), ninetyDays, ["USD", "EUR"])),
    ).toEqual([
      "18 payments from Jul 7 - Oct 4, 2026",
      "Received 44,562.29 USD",
      "2 of them on receipts",
    ]);
    expect(
      words(
        paymentResults(read("method=Credit+Card"), {
          total: 3,
          totals: [
            { currency: "EUR", payments: 1, received: -50, receipts: 0 },
            { currency: "USD", payments: 2, received: 1200, receipts: 0 },
          ],
        }),
      ),
    ).toEqual(["3 payments", "Received -50.00 EUR, 1,200.00 USD"]);
    expect(
      words(
        paymentResults(read("q=a"), {
          total: 5,
          totals: [
            { currency: "EUR", payments: 2, received: 900, receipts: 1 },
            { currency: "USD", payments: 3, received: 1200, receipts: 2 },
          ],
        }),
      ).at(-1),
    ).toBe("3 of them on receipts");
  });
});

describe("summary query", () => {
  it("ignores search and method", () => {
    expect(
      paymentSummaryQuery(
        read("q=deshmukh&method=receipt&period=this-year&currency=EUR&page=2"),
      ),
    ).toEqual({ period: "this-year", currency: "EUR" });
    expect(paymentSummaryQuery(read("period=90d"))).toEqual({ period: "90d" });
    expect(paymentSummaryQuery(read("method=PayPal"))).toEqual({});
  });
});

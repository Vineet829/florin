import { describe, expect, it } from "vitest";
import {
  TYPE_NAMES,
  billedQuery,
  readSales,
  salesQuery,
  salesSearch,
} from "./salesModel";

const read = (search) => readSales(new URLSearchParams(search));
const OCT_4 = new Date(2026, 9, 4, 12);

describe("sales URL state", () => {
  it("starts with the defaults", () => {
    expect(read("")).toEqual({ period: "", currency: "", page: 1, size: 10 });
    expect(salesSearch(read("")).toString()).toBe("");
  });

  it("round-trips URL parameters", () => {
    const list = read("size=25&currency=EUR&page=3&period=this-year");
    expect(list).toEqual({
      period: "this-year",
      currency: "EUR",
      page: 3,
      size: 25,
    });
    expect(salesSearch(list).toString()).toBe(
      "period=this-year&currency=EUR&page=3&size=25",
    );
  });

  it("accepts every list period", () => {
    for (const period of [
      "30d",
      "90d",
      "12m",
      "this-month",
      "last-month",
      "this-quarter",
      "this-year",
      "last-year",
    ]) {
      expect(read(`period=${period}`).period).toBe(period);
    }
    expect(read("period=ytd&currency=usd").period).toBe("");
    expect(read("currency=usd").currency).toBe("");
  });
});

describe("server queries", () => {
  it("builds the summary query", () => {
    expect(salesQuery(read("period=this-year"))).toEqual({
      period: "this-year",
    });
    expect(salesQuery(read("period=90d&currency=EUR"))).toEqual({
      period: "90d",
      currency: "EUR",
    });
    expect(salesQuery(read(""))).toEqual({});
  });

  it("builds the documents query", () => {
    expect(billedQuery(read("period=90d"), "USD", OCT_4)).toEqual({
      page: 1,
      size: 10,
      sort: "createdAt",
      dir: "desc",
      billed: "true",
      period: "90d",
      currency: "USD",
    });
    expect(billedQuery(read("period=this-year&page=2"), "EUR", OCT_4)).toEqual({
      page: 2,
      size: 10,
      sort: "createdAt",
      dir: "desc",
      billed: "true",
      from: "2026-01-01",
      to: "2026-10-04",
      currency: "EUR",
    });
    expect(billedQuery(read(""), undefined, OCT_4)).toEqual({
      page: 1,
      size: 10,
      sort: "createdAt",
      dir: "desc",
      billed: "true",
    });
  });

  it("names each document type", () => {
    expect(TYPE_NAMES).toEqual({
      Invoice: "Invoices",
      Receipt: "Receipts",
      "Credit Note": "Credit notes",
    });
  });
});

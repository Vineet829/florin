import { describe, expect, it } from "vitest";
import {
  SORT_OPTIONS,
  customerSalesQuery,
  customerSalesSearch,
  readCustomerSales,
} from "./customerSalesModel";

const read = (search) => readCustomerSales(new URLSearchParams(search));

describe("customer sales URL state", () => {
  it("starts with the defaults", () => {
    expect(read("")).toEqual({
      period: "",
      currency: "",
      sort: "billed",
      dir: "desc",
      page: 1,
      size: 10,
    });
    expect(customerSalesSearch(read("")).toString()).toBe("");
  });

  it("round-trips URL parameters", () => {
    const list = read("page=2&dir=asc&sort=owed&currency=EUR&period=90d");
    expect(list).toEqual({
      period: "90d",
      currency: "EUR",
      sort: "owed",
      dir: "asc",
      page: 2,
      size: 10,
    });
    expect(customerSalesSearch(list).toString()).toBe(
      "period=90d&currency=EUR&sort=owed&dir=asc&page=2",
    );
  });

  it("defaults for invalid values", () => {
    expect(read("period=ytd&currency=usd&sort=client&size=7")).toEqual({
      period: "",
      currency: "",
      sort: "billed",
      dir: "desc",
      page: 1,
      size: 10,
    });
  });

  it("offers each server sort", () => {
    expect(SORT_OPTIONS.map((option) => [option.value, option.label])).toEqual([
      ["billed:desc", "Most billed"],
      ["billed:asc", "Least billed"],
      ["paid:desc", "Most paid"],
      ["paid:asc", "Least paid"],
      ["owed:desc", "Most owed"],
      ["owed:asc", "Least owed"],
      ["name:asc", "Name A-Z"],
      ["name:desc", "Name Z-A"],
    ]);
  });
});

it("builds the server query", () => {
  expect(customerSalesQuery(read("period=this-year&sort=name"))).toEqual({
    period: "this-year",
    sort: "name",
    dir: "asc",
    page: 1,
    size: 10,
  });
  expect(customerSalesQuery(read("currency=EUR&size=25"))).toEqual({
    currency: "EUR",
    sort: "billed",
    dir: "desc",
    page: 1,
    size: 25,
  });
});

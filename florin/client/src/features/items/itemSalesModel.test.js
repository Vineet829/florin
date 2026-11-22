import { describe, expect, it } from "vitest";
import {
  itemDetailQuery,
  itemDetailSearch,
  itemSalesLink,
  itemSalesQuery,
  itemSalesSearch,
  itemName,
  readItemDetail,
  readItemSales,
  shareLabel,
  shareLabels,
  soldCount,
} from "./itemSalesModel";
import fixture from "./pages/ItemSalesPage.fixture.json";

const read = (search) => readItemSales(new URLSearchParams(search));
const readDetail = (search) => readItemDetail(new URLSearchParams(search));

describe("item sales URL state", () => {
  it("starts with the defaults", () => {
    expect(read("")).toEqual({ period: "", currency: "", page: 1, size: 10 });
    expect(itemSalesSearch(read("")).toString()).toBe("");
  });

  it("round-trips URL parameters", () => {
    const list = read("size=25&page=2&currency=EUR&period=12m");
    expect(list).toEqual({
      period: "12m",
      currency: "EUR",
      page: 2,
      size: 25,
    });
    expect(itemSalesSearch(list).toString()).toBe(
      "period=12m&currency=EUR&page=2&size=25",
    );
    expect(read("period=ytd&currency=eur")).toEqual({
      period: "",
      currency: "",
      page: 1,
      size: 10,
    });
  });

  it("builds the server query", () => {
    expect(itemSalesQuery(read("period=this-year&page=3"))).toEqual({
      period: "this-year",
      page: 3,
      size: 10,
    });
    expect(itemSalesQuery(read("currency=EUR"))).toEqual({
      currency: "EUR",
      page: 1,
      size: 10,
    });
  });
});

describe("single item page", () => {
  it("reads item and filters", () => {
    expect(
      readDetail("name=+Consulting+%28hourly%29+&period=90d&currency=EUR"),
    ).toEqual({ name: "Consulting (hourly)", period: "90d", currency: "EUR" });
    expect(readDetail("period=soon")).toEqual({
      name: "",
      period: "",
      currency: "",
    });
  });

  it("builds the item link", () => {
    expect(itemSalesLink("Consulting (hourly)", "90d")).toBe(
      "/items/sales/item?name=Consulting+%28hourly%29&period=90d",
    );
    expect(itemSalesLink("Logo & identity", "this-year", "EUR")).toBe(
      "/items/sales/item?name=Logo+%26+identity&period=this-year&currency=EUR",
    );
    expect(itemSalesLink("Copywriting", "")).toBe(
      "/items/sales/item?name=Copywriting",
    );
    expect(
      itemDetailSearch(readDetail("currency=EUR&name=Copywriting")).toString(),
    ).toBe("name=Copywriting&currency=EUR");
  });

  it("builds the item query", () => {
    expect(itemDetailQuery(readDetail("name=Copywriting&period=30d"))).toEqual({
      name: "Copywriting",
      period: "30d",
    });
    expect(itemDetailQuery(readDetail("name=Copywriting"))).toEqual({
      name: "Copywriting",
    });
  });
});

describe("the figures", () => {
  it("names items and other lines", () => {
    expect(itemName(" Copywriting ")).toBe("Copywriting");
    expect(itemName("Other lines")).toBe("Other lines");
    expect(itemName(" ")).toBe("Other lines");
    expect(itemName(null)).toBe("Other lines");
  });

  it("shows whole percent shares", () => {
    expect(shareLabel(0.2835)).toBe("28%");
    expect(shareLabel(1)).toBe("100%");
    expect(shareLabel(0.004)).toBe("<1%");
    expect(shareLabel(0)).toBe("0%");
    expect(shareLabel(-0.1)).toBe("0%");
  });

  it("shares add up to 100%", () => {
    const labels = shareLabels(fixture.sales.items, fixture.sales.total);
    expect(labels).toEqual([
      "28%",
      "15%",
      "11%",
      "10%",
      "8%",
      "8%",
      "8%",
      "6%",
      "4%",
      "2%",
    ]);
    expect(labels.reduce((sum, label) => sum + parseInt(label, 10), 0)).toBe(
      100,
    );
    const thirds = [{ amount: 100 }, { amount: 100 }, { amount: 100 }];
    expect(shareLabels(thirds, 300)).toEqual(["34%", "33%", "33%"]);
  });

  it("groups other pages as one share", () => {
    expect(shareLabels([{ amount: 333 }, { amount: 333 }], 1000)).toEqual([
      "33%",
      "33%",
    ]);
  });

  it("labels tiny and zero shares", () => {
    expect(
      shareLabels([{ amount: 9990 }, { amount: 10 }, { amount: 0 }], 10000),
    ).toEqual(["100%", "<1%", "0%"]);
  });

  it("rounds negative shares separately", () => {
    expect(
      shareLabels(
        [
          { amount: 1200, share: 1.2 },
          { amount: -200, share: -0.2 },
        ],
        1000,
      ),
    ).toEqual(["120%", "0%"]);
    expect(shareLabels([{ amount: 0, share: 0 }], 0)).toEqual(["0%"]);
  });

  it("rounds quantities to two places", () => {
    expect(soldCount(28)).toBe("28");
    expect(soldCount(2.5)).toBe("2.5");
    expect(soldCount(1234.567)).toBe("1,234.57");
    expect(soldCount(null)).toBe("0");
  });
});

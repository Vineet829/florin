import { readSortAndPage, writeSortAndPage } from "../../utils/listState";
import { PERIODS, currencyOf } from "../documents/documentFilters";

export const OTHER_LINES = "Other lines";
export const itemName = (item) =>
  (typeof item === "string" && item.trim()) || OTHER_LINES;

const periodOf = (params) => {
  const period = params.get("period");
  return PERIODS.some((option) => option.value === period) ? period : "";
};

const BY_AMOUNT = { amount: { first: "desc" } };

export const readItemSales = (params) => {
  const { page, size } = readSortAndPage(params, BY_AMOUNT, "amount");
  return {
    period: periodOf(params),
    currency: currencyOf(params.get("currency")),
    page,
    size,
  };
};

export const itemSalesSearch = ({ period, currency, page, size }) => {
  const params = new URLSearchParams();
  if (period) params.set("period", period);
  if (currency) params.set("currency", currency);
  return writeSortAndPage(
    params,
    { sort: "amount", dir: "desc", page, size },
    BY_AMOUNT,
    "amount",
  );
};

export const itemSalesQuery = ({ period, currency, page, size }) => ({
  ...(period && { period }),
  ...(currency && { currency }),
  page,
  size,
});

export const readItemDetail = (params) => ({
  name: (params.get("name") || "").trim(),
  period: periodOf(params),
  currency: currencyOf(params.get("currency")),
});

export const itemDetailSearch = ({ name, period, currency }) => {
  const params = new URLSearchParams();
  if (name) params.set("name", name);
  if (period) params.set("period", period);
  if (currency) params.set("currency", currency);
  return params;
};

export const itemDetailQuery = ({ name, period, currency }) => ({
  name,
  ...(period && { period }),
  ...(currency && { currency }),
});

export const itemSalesLink = (name, period, currency) =>
  `/items/sales/item?${itemDetailSearch({ name, period, currency })}`;

export const shareLabel = (share) => {
  const percent = Number(share) > 0 ? Number(share) * 100 : 0;
  return percent > 0 && percent < 0.5 ? "<1%" : `${Math.round(percent)}%`;
};

export const shareLabels = (rows, total) => {
  const income = Number(total);
  const amounts = rows.map((row) => Number(row.amount) || 0);
  const others = income - amounts.reduce((sum, amount) => sum + amount, 0);
  if (
    !(income > 0) ||
    others < -0.005 ||
    amounts.some((amount) => amount < 0)
  ) {
    return rows.map((row) => shareLabel(row.share));
  }
  const exact = [...amounts, Math.max(others, 0)].map(
    (amount) => (amount / income) * 100,
  );
  const whole = exact.map(Math.floor);
  const lost = (index) => exact[index] - whole[index];
  let left = 100 - whole.reduce((sum, percent) => sum + percent, 0);
  [...exact.keys()]
    .sort((one, other) => lost(other) - lost(one) || one - other)
    .forEach((index) => {
      if (left <= 0) return;
      whole[index] += 1;
      left -= 1;
    });
  return amounts.map((amount, index) =>
    amount > 0 && whole[index] === 0 ? "<1%" : `${whole[index]}%`,
  );
};

export const soldCount = (quantity) =>
  Number(quantity || 0).toLocaleString("en", { maximumFractionDigits: 2 });

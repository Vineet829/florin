import { readSortAndPage, writeSortAndPage } from "../../utils/listState";
import { PERIODS, currencyOf, periodQuery } from "./documentFilters";

const NEWEST_FIRST = { createdAt: { first: "desc" } };

export const readSales = (params) => {
  const { page, size } = readSortAndPage(params, NEWEST_FIRST, "createdAt");
  const period = params.get("period");
  return {
    period: PERIODS.some((option) => option.value === period) ? period : "",
    currency: currencyOf(params.get("currency")),
    page,
    size,
  };
};

export const salesSearch = ({ period, currency, page, size }) => {
  const params = new URLSearchParams();
  if (period) params.set("period", period);
  if (currency) params.set("currency", currency);
  return writeSortAndPage(
    params,
    { sort: "createdAt", dir: "desc", page, size },
    NEWEST_FIRST,
    "createdAt",
  );
};

export const salesQuery = ({ period, currency }) => ({
  ...(period && { period }),
  ...(currency && { currency }),
});

export const billedQuery = (
  { period, page, size },
  currency,
  today = new Date(),
) => ({
  page,
  size,
  sort: "createdAt",
  dir: "desc",
  billed: "true",
  ...periodQuery(period, today),
  ...(currency && { currency }),
});

export const TYPE_NAMES = {
  Invoice: "Invoices",
  Receipt: "Receipts",
  "Credit Note": "Credit notes",
};

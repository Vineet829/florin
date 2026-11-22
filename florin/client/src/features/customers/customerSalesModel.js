import {
  readSortAndPage,
  sortOptions,
  writeSortAndPage,
} from "../../utils/listState";
import { PERIODS, currencyOf } from "../documents/documentFilters";

export const SORT_FIELDS = {
  billed: { first: "desc", asc: "Least billed", desc: "Most billed" },
  paid: { first: "desc", asc: "Least paid", desc: "Most paid" },
  owed: { first: "desc", asc: "Least owed", desc: "Most owed" },
  name: { first: "asc", asc: "Name A-Z", desc: "Name Z-A" },
};

export const SORT_OPTIONS = sortOptions(SORT_FIELDS);

export const readCustomerSales = (params) => {
  const period = params.get("period");
  return {
    period: PERIODS.some((option) => option.value === period) ? period : "",
    currency: currencyOf(params.get("currency")),
    ...readSortAndPage(params, SORT_FIELDS, "billed"),
  };
};

export const customerSalesSearch = ({ period, currency, ...sortAndPage }) => {
  const params = new URLSearchParams();
  if (period) params.set("period", period);
  if (currency) params.set("currency", currency);
  return writeSortAndPage(params, sortAndPage, SORT_FIELDS, "billed");
};

export const customerSalesQuery = ({
  period,
  currency,
  sort,
  dir,
  page,
  size,
}) => ({
  ...(period && { period }),
  ...(currency && { currency }),
  sort,
  dir,
  page,
  size,
});

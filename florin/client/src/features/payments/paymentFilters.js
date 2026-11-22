import {
  readSortAndPage,
  sortOptions,
  writeSortAndPage,
} from "../../utils/listState";
import { countOf } from "../../utils/counts";
import { dateSpan } from "../../utils/dates";
import { PERIODS, currencyOf, periodQuery } from "../documents/documentFilters";
import { amountsOf } from "../documents/listTotals";
import { PAYMENT_METHODS } from "../documents/paymentMethods";

export const RECEIPT = "receipt";

export const SORT_FIELDS = {
  date: { first: "desc", asc: "Oldest first", desc: "Newest first" },
  amount: { first: "desc", asc: "Lowest amount", desc: "Highest amount" },
  client: { first: "asc", asc: "Customer A-Z" },
};

export const SORT_OPTIONS = sortOptions(SORT_FIELDS).filter(
  (option) => option.label,
);

export const readPaymentList = (params) => {
  const method = params.get("method");
  const period = params.get("period");
  const sortAndPage = readSortAndPage(params, SORT_FIELDS, "date");
  return {
    q: params.get("q") || "",
    method:
      method === RECEIPT || PAYMENT_METHODS.includes(method) ? method : "",
    period: PERIODS.some((option) => option.value === period) ? period : "",
    currency: currencyOf(params.get("currency")),
    ...sortAndPage,
    ...(!SORT_FIELDS[sortAndPage.sort][sortAndPage.dir] && {
      sort: "date",
      dir: "desc",
    }),
  };
};

export const paymentListSearch = ({
  q,
  method,
  period,
  currency,
  ...sortAndPage
}) => {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (method) params.set("method", method);
  if (period) params.set("period", period);
  if (currency) params.set("currency", currency);
  return writeSortAndPage(params, sortAndPage, SORT_FIELDS, "date");
};

export const isPaymentListFiltered = ({ q, method, period, currency }) =>
  Boolean(q || method || period || currency);

export const paymentQuery = (
  { q, method, period, currency, sort, dir, page, size },
  today = new Date(),
) => ({
  page,
  size,
  sort,
  dir,
  ...(q && { q }),
  ...(method && { method }),
  ...periodQuery(period, today),
  ...(currency && { currency }),
});

export const paymentSummaryQuery = ({ period, currency }) => ({
  ...(period && { period }),
  ...(currency && { currency }),
});

export const methodOptions = (methods = [], chosen = "") => [
  { value: "", label: "All methods" },
  ...[
    ...methods,
    ...(chosen && chosen !== RECEIPT && !methods.includes(chosen)
      ? [chosen]
      : []),
  ].map((method) => ({ value: method, label: method })),
  { value: RECEIPT, label: "On a receipt" },
];

export const methodLabel = (row) =>
  row.kind === RECEIPT
    ? "On a receipt"
    : row.kind === "refund"
      ? ["Refund", row.method].filter(Boolean).join(" · ")
      : row.method || "-";

export const paymentResults = (list, data, currencies = []) => {
  const totals = data?.totals || [];
  const several = currencies.length > 1 || totals.length > 1;
  const received = amountsOf(totals, "received", { several, all: true });
  const receipts =
    list.method === RECEIPT
      ? 0
      : totals.reduce((sum, entry) => sum + (Number(entry.receipts) || 0), 0);
  return [
    {
      strong: countOf(data?.total ?? 0, "payment", "payments"),
      after:
        data?.from && data?.to ? ` from ${dateSpan(data.from, data.to)}` : "",
    },
    received && { before: "Received ", strong: received },
    receipts > 0 && {
      after:
        receipts === 1
          ? "1 of them on a receipt"
          : `${receipts.toLocaleString("en")} of them on receipts`,
    },
  ].filter(Boolean);
};

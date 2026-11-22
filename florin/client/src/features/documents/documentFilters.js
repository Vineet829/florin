import {
  readSortAndPage,
  sortOptions,
  writeSortAndPage,
} from "../../utils/listState";
import { countOf } from "../../utils/counts";
import { dateSpan } from "../../utils/dates";
import { amountsOf } from "./listTotals";

export const STATUS_FILTERS = [
  { value: "", label: "All" },
  { value: "outstanding", label: "Outstanding" },
  { value: "unpaid", label: "Unpaid", status: "Not Paid" },
  { value: "partial", label: "Partially paid", status: "Not Fully Paid" },
  { value: "paid", label: "Paid", status: "Paid" },
  { value: "overdue", label: "Overdue" },
  { value: "drafts", label: "Drafts", state: "draft" },
  { value: "void", label: "Void", state: "void" },
];

export const TYPE_FILTERS = [
  { value: "", label: "All types" },
  { value: "billed", label: "Billed: invoices, receipts and credit notes" },
  { value: "invoice", label: "Invoices", type: "Invoice" },
  { value: "quotation", label: "Quotations", type: "Quotation" },
  { value: "receipt", label: "Receipts", type: "Receipt" },
  { value: "credit-note", label: "Credit notes", type: "Credit Note" },
];

export const PERIODS = [
  { value: "", label: "All dates" },
  { value: "30d", label: "Last 30 days", recent: true },
  { value: "90d", label: "Last 90 days", recent: true },
  { value: "12m", label: "Last 12 months", recent: true },
  { value: "this-month", label: "This month" },
  { value: "last-month", label: "Last month" },
  { value: "this-quarter", label: "This quarter" },
  { value: "this-year", label: "This year" },
  { value: "last-year", label: "Last year" },
];

export const DUE_FILTERS = [
  { value: "this-week", label: "Due this week" },
  { value: "next-week", label: "Due next week" },
  { value: "week-after", label: "Due the week after" },
  { value: "later", label: "Due later" },
];

const localDay = (date) =>
  [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, "0"),
    String(date.getDate()).padStart(2, "0"),
  ].join("-");

export const periodRange = (period, today) => {
  const year = today.getFullYear();
  const month = today.getMonth();
  const quarter = month - (month % 3);
  const [from, to] =
    {
      "this-month": [new Date(year, month, 1), today],
      "last-month": [new Date(year, month - 1, 1), new Date(year, month, 0)],
      "this-quarter": [new Date(year, quarter, 1), today],
      "this-year": [new Date(year, 0, 1), today],
      "last-year": [new Date(year - 1, 0, 1), new Date(year - 1, 11, 31)],
    }[period] || [];
  return from ? { from: localDay(from), to: localDay(to) } : {};
};

export const periodQuery = (period, today) =>
  PERIODS.find((option) => option.value === period)?.recent
    ? { period }
    : periodRange(period, today);

export const SORT_FIELDS = {
  createdAt: { first: "desc", asc: "Oldest first", desc: "Newest first" },
  dueDate: { first: "asc", asc: "Due soonest", desc: "Due latest" },
  total: { first: "desc", asc: "Lowest amount", desc: "Highest amount" },
  client: { first: "asc", asc: "Customer A-Z", desc: "Customer Z-A" },
  number: { first: "asc", asc: "Number A-Z", desc: "Number Z-A" },
};

export const SORT_OPTIONS = sortOptions(SORT_FIELDS);

const oneOf = (value, filters) =>
  filters.some((filter) => filter.value === value) ? value : "";

export const currencyOf = (value) =>
  /^[A-Z]{3}$/.test(value || "") ? value : "";

export const readListState = (params) => ({
  q: params.get("q") || "",
  status: oneOf(params.get("status"), STATUS_FILTERS),
  due: oneOf(params.get("due"), DUE_FILTERS),
  type: oneOf(params.get("type"), TYPE_FILTERS),
  period: oneOf(params.get("period"), PERIODS),
  currency: currencyOf(params.get("currency")),
  ...readSortAndPage(params, SORT_FIELDS, "createdAt"),
});

export const listSearch = ({
  q,
  status,
  due,
  type,
  period,
  currency,
  ...sortAndPage
}) => {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (status) params.set("status", status);
  if (due) params.set("due", due);
  if (type) params.set("type", type);
  if (period) params.set("period", period);
  if (currency) params.set("currency", currency);
  return writeSortAndPage(params, sortAndPage, SORT_FIELDS, "createdAt");
};

export const isFiltered = ({ q, status, due, type, period, currency }) =>
  Boolean(q || status || due || type || period || currency);

export const documentFilterQuery = (
  { q, status, due, type, period, currency, sort, dir },
  today = new Date(),
) => {
  const statusFilter = STATUS_FILTERS.find((filter) => filter.value === status);
  const typeFilter = TYPE_FILTERS.find((filter) => filter.value === type);
  return {
    sort,
    dir,
    ...(q && { q }),
    ...(statusFilter?.status && { status: statusFilter.status }),
    ...(statusFilter?.state && { state: statusFilter.state }),
    ...(status === "overdue" && { overdue: "true" }),
    ...(status === "outstanding" && { outstanding: "true" }),
    ...(due && { due }),
    ...(typeFilter?.type && { type: typeFilter.type }),
    ...(type === "billed" && { billed: "true" }),
    ...periodQuery(period, today),
    ...(currency && { currency }),
  };
};

export const documentQuery = (list, today = new Date()) => ({
  page: list.page,
  size: list.size,
  ...documentFilterQuery(list, today),
});

export const documentResults = (list, data, currencies = []) => {
  const totals = data?.totals || [];
  const count = data?.totalDocuments ?? 0;
  const several = currencies.length > 1 || totals.length > 1;
  const issued =
    data?.issuedFrom && data?.issuedTo
      ? ` issued ${dateSpan(data.issuedFrom, data.issuedTo)}`
      : "";
  const owed = list.status === "outstanding";
  const billed = !owed && amountsOf(totals, "billed", { several });
  const balanceDue = amountsOf(totals, "balanceDue", { several });
  const overdue = owed && amountsOf(totals, "overdue", { several });
  const late = totals.reduce(
    (sum, entry) => sum + (Number(entry.overdueDocuments) || 0),
    0,
  );
  return [
    owed
      ? {
          strong: countOf(count, "invoice", "invoices"),
          after: ` with money owed${issued && `,${issued}`}`,
        }
      : { strong: countOf(count, "document", "documents"), after: issued },
    billed && { before: "Billed ", strong: billed },
    balanceDue && { before: "Balance due ", strong: balanceDue },
    overdue && {
      after: `${overdue} overdue on ${late.toLocaleString("en")}`,
      tone: "overdue",
    },
  ].filter(Boolean);
};

import { readSortAndPage, writeSortAndPage } from "../../utils/listState";
import { countOf } from "../../utils/counts";
import { dateSpan } from "../../utils/dates";
import { PERIODS, currencyOf, periodQuery } from "../documents/documentFilters";
import { amountsOf } from "../documents/listTotals";
import { cents } from "../documents/taxWithheld";

const NEWEST_FIRST = { date: { first: "desc" } };

export const readExpenseList = (params) => {
  const { page, size } = readSortAndPage(params, NEWEST_FIRST, "date");
  const period = params.get("period");
  return {
    q: params.get("q") || "",
    category: params.get("category") || "",
    period: PERIODS.some((option) => option.value === period) ? period : "",
    currency: currencyOf(params.get("currency")),
    page,
    size,
  };
};

export const expenseListSearch = ({
  q,
  category,
  period,
  currency,
  page,
  size,
}) => {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (category) params.set("category", category);
  if (period) params.set("period", period);
  if (currency) params.set("currency", currency);
  return writeSortAndPage(
    params,
    { sort: "date", dir: "desc", page, size },
    NEWEST_FIRST,
    "date",
  );
};

export const isFiltered = ({ q, category, period, currency }) =>
  Boolean(q || category || period || currency);

export const expenseFilterQuery = (
  { q, category, period, currency },
  today = new Date(),
) => ({
  ...(q && { q }),
  ...(category && { category }),
  ...periodQuery(period, today),
  ...(currency && { currency }),
});

export const expenseQuery = (list, today) => ({
  page: list.page,
  size: list.size,
  ...expenseFilterQuery(list, today),
});

export const expenseResults = (data, currencies = []) => {
  const totals = data?.totals || [];
  const [main] = currencies;
  const several =
    currencies.length > 1 ||
    totals.length > 1 ||
    totals.some((entry) => main && entry.currency !== main);
  const spent = amountsOf(totals, "spent", { several, all: true });
  return [
    {
      strong: countOf(data?.totalExpenses ?? 0, "expense", "expenses"),
      after:
        data?.from && data?.to ? ` from ${dateSpan(data.from, data.to)}` : "",
    },
    spent && { before: "Spent ", strong: spent },
  ].filter(Boolean);
};

export const expenseForm = (expense, { today, currency, base }) => ({
  date: expense?.date || today,
  category: expense?.category || "",
  vendor: expense?.vendor || "",
  total: expense ? String(expense.total) : "",
  tax: Number(expense?.taxAmount) > 0 ? String(expense.taxAmount) : "",
  taxReclaimable: Boolean(expense?.taxReclaimable),
  currency: expense?.currency || currency,
  exchangeRate:
    expense?.exchangeRate && expense.exchangeBase === base
      ? expense.exchangeRate
      : "",
  paymentMethod: expense?.paymentMethod || "",
  reference: expense?.reference || "",
  notes: expense?.notes || "",
});

export const taxError = ({ total, tax }) =>
  total !== "" && cents(tax) > 0 && cents(tax) >= cents(total)
    ? "The tax has to be less than the total paid."
    : null;

export const expensePayload = (values, base) => ({
  date: values.date,
  category: values.category.trim(),
  vendor: values.vendor,
  amount: cents(cents(values.total) - cents(values.tax)),
  taxAmount: cents(values.tax),
  taxReclaimable: values.taxReclaimable,
  currency: values.currency,
  exchangeRate: values.currency === base ? "" : values.exchangeRate,
  paymentMethod: values.paymentMethod,
  reference: values.reference,
  notes: values.notes,
});

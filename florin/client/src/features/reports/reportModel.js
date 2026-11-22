import { dayMonth, parseDate } from "../../utils/dates";

export const DEFAULT_PERIOD = "12m";

export const periodOptions = (years = []) => [
  { value: "30d", label: "Last 30 days" },
  { value: "90d", label: "Last 90 days" },
  { value: "3m", label: "Last 3 months" },
  { value: "6m", label: "Last 6 months" },
  { value: "12m", label: "Last 12 months" },
  { value: "ytd", label: "This year" },
  ...years.map((year) => ({ value: String(year), label: String(year) })),
];

export const axisTicks = (largest) => {
  const value = Number(largest);
  if (!Number.isFinite(value) || value <= 0) return [0];
  const raw = value / 4;
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const step =
    [1, 2, 2.5, 5, 10].find((nice) => raw <= nice * magnitude) * magnitude;
  return [0, 1, 2, 3, 4].map((index) => index * step);
};

export { compactAmount } from "../../utils/money";

const firstDay = (month) => {
  const [year, number] = String(month).split("-").map(Number);
  return new Date(Date.UTC(year, number - 1, 1));
};
export const monthName = (month) =>
  firstDay(month).toLocaleDateString("en", {
    month: "short",
    timeZone: "UTC",
  });
export const monthYear = (month) =>
  firstDay(month).toLocaleDateString("en", {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  });

export const monthSpan = (month, from, to) => {
  const [year, number] = String(month).split("-").map(Number);
  const first = `${month}-01`;
  const last = `${month}-${new Date(year, number, 0).getDate()}`;
  const start = from > first ? from : first;
  const end = to < last ? to : last;
  if (start === first && end === last) return monthYear(month);
  const days =
    start === end
      ? dayMonth(parseDate(start))
      : `${dayMonth(parseDate(start))}-${parseDate(end).getDate()}`;
  return `${days}, ${year}`;
};

const monthOf = (date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
export const previousMonth = (today = new Date()) =>
  monthOf(new Date(today.getFullYear(), today.getMonth() - 1, 1));
export const currentMonth = (today = new Date()) => monthOf(today);

export const gstFileName = (key, from, to) =>
  `florin-${key === "gstr3b" ? "gstr3b" : `gstr1-${key}`}-${from === to ? from : `${from}-to-${to}`}.csv`;

export const share = (value, largest) => {
  const amount = Number(value);
  const most = Number(largest);
  return most > 0 && amount > 0 ? Math.min(amount / most, 1) : 0;
};

export const margin = (profit, income) => {
  const earned = Number(income);
  return earned > 0 ? Math.round((Number(profit) / earned) * 100) : null;
};

export const percentLabel = (value, exact = value) => {
  if (value == null) return "-";
  if (value === 0 && Number(exact) > 0) return "<1%";
  return `${value.toLocaleString("en")}%`;
};

export const profitMonths = (months = []) => {
  let soFar = 0;
  return months.map((row) => {
    soFar += Math.round((Number(row.profit) || 0) * 100);
    return {
      ...row,
      margin: margin(row.profit, row.income),
      soFar: soFar / 100,
    };
  });
};

export const percentOf = (part, total) => {
  const all = Number(total);
  return all > 0 ? Math.round((Number(part) / all) * 100) : 0;
};

export const taxedSales = (entry) => {
  const rates = entry?.rates || [];
  const taxed = rates.filter((row) => Number(row.rate) > 0);
  const untaxed = rates.filter((row) => !(Number(row.rate) > 0));
  const taxable = (rows) =>
    rows.reduce((sum, row) => sum + Math.round(Number(row.taxable) * 100), 0) /
    100;
  const documents = taxed.reduce((sum, row) => sum + row.documents, 0);
  return {
    taxable: taxable(taxed),
    documents: Math.min(documents, entry?.documents ?? 0),
    untaxed: taxable(untaxed),
  };
};

import { readSortAndPage, writeSortAndPage } from "../../utils/listState";
import { parseDate } from "../../utils/dates";
import { currencyOf } from "./documentFilters";

export const MODES = [
  { value: "coming-in", label: "Coming in" },
  { value: "overdue", label: "Overdue" },
];

export const AGE_BANDS = [
  { key: "days1to15", label: "1-15 days", color: "bg-owed-age-1" },
  { key: "days16to30", label: "16-30 days", color: "bg-owed-age-2" },
  { key: "days31to45", label: "31-45 days", color: "bg-owed-age-3" },
  { key: "over45", label: "Over 45 days", color: "bg-owed-age-4" },
];

export const DUE_BANDS = [
  { key: "dueIn0to15", label: "Due in 0-15 days", color: "bg-owed-due" },
  { key: "dueIn16to30", label: "Due in 16-30 days", color: "bg-owed-due/70" },
  { key: "dueIn31to45", label: "Due in 31-45 days", color: "bg-owed-due/45" },
  { key: "dueAfter45", label: "Due after 45 days", color: "bg-owed-due/25" },
];

export const bandsOf = (group) =>
  Object.fromEntries((group?.bands || []).map((band) => [band.key, band]));

const BY_DUE_DATE = { dueDate: { first: "asc" } };

export const readReceivables = (params) => {
  const { page, size } = readSortAndPage(params, BY_DUE_DATE, "dueDate");
  return {
    mode: params.get("mode") === "overdue" ? "overdue" : "coming-in",
    currency: currencyOf(params.get("currency")),
    page,
    size,
  };
};

export const receivablesSearch = ({ mode, currency, page, size }) => {
  const params = new URLSearchParams();
  if (mode === "overdue") params.set("mode", mode);
  if (currency) params.set("currency", currency);
  return writeSortAndPage(
    params,
    { sort: "dueDate", dir: "asc", page, size },
    BY_DUE_DATE,
    "dueDate",
  );
};

export const receivablesQuery = ({ mode, page, size }, currency) => ({
  page,
  size,
  sort: "dueDate",
  dir: "asc",
  ...(mode === "overdue"
    ? { overdue: "true", outstanding: "true" }
    : { outstanding: "true", due: "current" }),
  ...(currency && { currency }),
});

const DAY = 24 * 60 * 60 * 1000;
export const daysUntil = (dueDate, today) => {
  const start = new Date(
    today.getFullYear(),
    today.getMonth(),
    today.getDate(),
  );
  return Math.round((parseDate(dueDate) - start) / DAY);
};

export const lateLabel = (dueDate, today) => {
  const days = Math.max(1, -daysUntil(dueDate, today));
  return `${days.toLocaleString("en")} ${days === 1 ? "day" : "days"} late`;
};

export const dueLabel = (dueDate, today) => {
  if (!dueDate) return "No due date";
  const days = Math.max(0, daysUntil(dueDate, today));
  if (days === 0) return "Due today";
  if (days === 1) return "Due tomorrow";
  return `Due in ${days.toLocaleString("en")} days`;
};

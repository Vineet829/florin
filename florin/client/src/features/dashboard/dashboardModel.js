import {
  dateSpan,
  dayMonth,
  dayMonthFrom,
  dayMonthYear,
  parseDate,
} from "../../utils/dates";
import { compactAmount, formatAmount } from "../../utils/money";
import {
  customerSalesLink,
  documentsLink,
  expensesLink,
  itemsSoldLink,
  paymentsLink,
  salesLink,
} from "../documents/listLinks";
import { itemName, itemSalesLink } from "../items/itemSalesModel";
import { share } from "../reports/reportModel";
const asAmount = (value) =>
  Number.isFinite(Number(value)) ? Number(value) : 0;

export const formatMoney = (value, currency) =>
  formatAmount(asAmount(value), currency);

export const DEFAULT_PERIOD = "90d";
export const PERIODS = [
  {
    key: "30d",
    label: "30 days",
    name: "Last 30 days",
    called: "the last 30 days",
    during: "in the last 30 days",
    these: "these 30 days",
    inThese: "in these 30 days",
    compared: "vs previous 30 days",
    report: "30d",
    list: "30d",
  },
  {
    key: "90d",
    label: "90 days",
    name: "Last 90 days",
    called: "the last 90 days",
    during: "in the last 90 days",
    these: "these 90 days",
    inThese: "in these 90 days",
    compared: "vs previous 90 days",
    report: "90d",
    list: "90d",
  },
  {
    key: "12m",
    label: "12 months",
    name: "Last 12 months",
    called: "the last 12 months",
    during: "in the last 12 months",
    these: "these 12 months",
    inThese: "in these 12 months",
    compared: "vs previous 12 months",
    list: "12m",
  },
  {
    key: "ytd",
    label: "This year",
    name: "This year",
    called: "this year",
    during: "this year",
    these: "this year",
    inThese: "this year",
    compared: "vs same period last year",
    report: "ytd",
    list: "this-year",
  },
];
export const findPeriod = (key) =>
  PERIODS.find((period) => period.key === key) ||
  PERIODS.find((period) => period.key === DEFAULT_PERIOD);

export const reportLink = (section, period, currency) => {
  const search = new URLSearchParams();
  const report = period && findPeriod(period).report;
  if (report) search.set("period", report);
  if (currency) search.set("currency", currency);
  return `/reports${search.toString() ? `?${search}` : ""}#${section}`;
};

export const receivablesLink = (mode, currency) => {
  const search = new URLSearchParams();
  if (mode) search.set("mode", mode);
  if (currency) search.set("currency", currency);
  return `/receivables${search.toString() ? `?${search}` : ""}`;
};

export { AGE_BANDS } from "../documents/receivablesModel";
export const owedBands = (owedNow) =>
  Array.isArray(owedNow?.bands)
    ? Object.fromEntries(owedNow.bands.map((band) => [band.key, band]))
    : null;

export const billedLink = (period, currency) =>
  salesLink(findPeriod(period).list, currency);
export const receivedLink = (period, currency) =>
  paymentsLink(findPeriod(period).list, currency);
export const salesByCustomerLink = (period, currency) =>
  customerSalesLink(findPeriod(period).list, currency);
export const moneyOutLink = (period, currency) =>
  expensesLink({ period: findPeriod(period).list, currency });

const shareOfTotal = (amount, total) => {
  const percent = total > 0 && amount > 0 ? (amount / total) * 100 : 0;
  return percent > 0 && percent < 0.5 ? "<1%" : `${Math.round(percent)}%`;
};

export const topClientRows = (topClients = []) => {
  const largest = Math.max(0, ...topClients.map((row) => asAmount(row.billed)));
  return topClients.map((row) => ({
    ...row,
    bar: share(row.billed, largest),
    to: row.clientId ? `/single-customer/${row.clientId}` : null,
  }));
};

export const topItemRows = (topItems = [], itemsTotal, period, currency) => {
  const largest = Math.max(0, ...topItems.map((row) => asAmount(row.amount)));
  const income = asAmount(itemsTotal);
  return topItems.map((row) => {
    const name = itemName(row.item);
    return {
      ...row,
      name,
      bar: share(row.amount, largest),
      share: income > 0 ? shareOfTotal(asAmount(row.amount), income) : null,
      to: itemSalesLink(name, findPeriod(period).list, currency),
    };
  });
};
export const salesByItemLink = (period, currency) =>
  itemsSoldLink(findPeriod(period).list, currency);

export const recurringLink = "/recurring";
export const quotationsLink = (currency) =>
  documentsLink({ type: "quotation", currency });
const dayOf = (value) => (value ? dayMonth(parseDate(value)) : "");
export const runRows = (nextRuns = [], opened = new Set()) => {
  const schedules = new Set();
  return [...nextRuns]
    .sort((one, other) => String(one.date).localeCompare(String(other.date)))
    .filter((run) => {
      const schedule = run.scheduleId || run.templateId;
      if (schedules.has(schedule)) return false;
      schedules.add(schedule);
      return true;
    })
    .map((run) => ({
      ...run,
      day: dayOf(run.date),
      number: run.documentNumber || "Draft invoice",
      to:
        run.templateId && !opened.has(run.templateId)
          ? `/document/${run.templateId}`
          : null,
    }));
};
export const quotationRows = (items = []) =>
  items.map((item) => ({
    ...item,
    until: item.validUntil
      ? `valid until ${dayOf(item.validUntil)}`
      : "No expiry",
    to: `/document/${item.documentId}`,
  }));

export { invoiceCount } from "../../utils/counts";

export { dateSpan };

export const periodSubtitle = (period, hasPrevious) => {
  const current = `${findPeriod(period.key).name}, ${dateSpan(period.from, period.to)}`;
  if (!hasPrevious) return current;
  const sameYear =
    parseDate(period.previousTo).getFullYear() ===
    parseDate(period.to).getFullYear();
  return `${current} · compared with ${dateSpan(period.previousFrom, period.previousTo, !sameYear)}`;
};

export const changeFrom = (current, previous) => {
  const now = Number(current);
  const before = Number(previous);
  if (!Number.isFinite(now) || !Number.isFinite(before) || before === 0) {
    return null;
  }
  const ratio = (now - before) / Math.abs(before);
  const percent = Math.round(Math.abs(ratio) * 1000) / 10;
  return {
    direction: percent === 0 ? "flat" : ratio > 0 ? "up" : "down",
    percent,
  };
};
export const percentLabel = (percent) =>
  `${percent.toLocaleString("en", {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  })}%`;

export const trendPoints = (values) => {
  const numbers = values.map(asAmount);
  const high = Math.max(0, ...numbers) * 1.1;
  const low = Math.min(0, ...numbers);
  const last = numbers.length - 1;
  return numbers.map((value, index) => ({
    x: last > 0 ? (index / last) * 100 : 50,
    y: high > low ? ((high - value) / (high - low)) * 100 : 100,
  }));
};

export const trendColumns = (values) => {
  const numbers = values.map(asAmount);
  const high = Math.max(0, ...numbers);
  const low = Math.min(0, ...numbers);
  const span = high - low;
  return {
    zero: span ? (high / span) * 100 : 100,
    columns: numbers.map((value) => ({
      value,
      top: span ? ((high - Math.max(value, 0)) / span) * 100 : 100,
      height: span ? (Math.abs(value) / span) * 100 : 0,
    })),
  };
};

const niceStep = (raw) => {
  if (!(raw > 1)) return 1;
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const steps = magnitude >= 10 ? [1, 2, 2.5, 5, 10] : [1, 2, 5, 10];
  return steps.find((nice) => raw <= nice * magnitude) * magnitude;
};

export const cashFlowScale = (trend) => {
  const high = Math.max(0, ...trend.map((bucket) => asAmount(bucket.moneyIn)));
  const low = Math.max(0, ...trend.map((bucket) => asAmount(bucket.moneyOut)));
  if (!high && !low) return null;
  const step = niceStep((high + low) / 4);
  const top = Math.max(1, Math.ceil(high / step)) * step;
  const bottom = Math.max(1, Math.ceil(low / step)) * step;
  const ticks = [];
  for (let tick = top; tick >= -bottom; tick -= step) ticks.push(tick);
  return {
    ticks,
    span: top + bottom,
    at: (value) => ((top - asAmount(value)) / (top + bottom)) * 100,
  };
};

export const wholeAmount = (value, currency) =>
  formatAmount(Math.round(asAmount(value)), currency).replace(/\.\d+$/, "");

export const axisLabel = (value, ticks, currency) =>
  (Math.max(...ticks.map(Math.abs)) >= 100000
    ? compactAmount(value, currency)
    : wholeAmount(value, currency)
  ).replace("-", "−");

export const dayRange = (from, to) => {
  const start = parseDate(from);
  const end = parseDate(to);
  if (from === to) return dayMonth(start);
  if (start.getFullYear() !== end.getFullYear()) {
    return `${dayMonthYear(start)} - ${dayMonthYear(end)}`;
  }
  return start.getMonth() === end.getMonth()
    ? `${dayMonth(start)}-${end.getDate()}`
    : `${dayMonth(start)}-${dayMonth(end)}`;
};

export const bucketLabel = (bucket, step) =>
  parseDate(bucket.from).toLocaleDateString(
    "en",
    step === "month" ? { month: "short" } : { month: "short", day: "numeric" },
  );

export const bucketRange = (bucket, step) => {
  if (step !== "month") return dayRange(bucket.from, bucket.to);
  const start = parseDate(bucket.from);
  const end = parseDate(bucket.to);
  const lastDay = new Date(end.getFullYear(), end.getMonth() + 1, 0);
  return start.getDate() === 1 && end.getDate() === lastDay.getDate()
    ? start.toLocaleDateString("en", { month: "long", year: "numeric" })
    : `${dayRange(bucket.from, bucket.to)}, ${end.getFullYear()}`;
};

export const labelStep = (count, width, room) =>
  Math.max(1, Math.ceil(count / Math.max(1, Math.floor(width / room))));
export const showsLabel = (index, count, step) =>
  (count - 1 - index) % step === 0;

const ATTENTION = {
  paymentReport: {
    label: "To confirm",
    count: "paymentReports",
    none: "payment reports to confirm",
    more: ["payment report", "payment reports"],
  },
  overdue: {
    label: "Overdue",
    count: "overdue",
    none: "overdue invoices",
    more: ["overdue invoice", "overdue invoices"],
  },
  dueSoon: {
    label: "Due soon",
    count: "dueSoon",
    none: "invoices due this week",
    more: ["due this week", "due this week"],
  },
  draft: {
    label: "To issue",
    count: "drafts",
    none: "drafts to issue",
    more: ["draft", "drafts"],
  },
};
const ATTENTION_ORDER = ["paymentReport", "overdue", "dueSoon", "draft"];
const dayCount = (days) => `${days} ${days === 1 ? "day" : "days"}`;
const soon = (days, today, tomorrow, later) =>
  days === 0 ? today : days === 1 ? tomorrow : later;
const listed = (phrases, last) =>
  phrases.length > 1
    ? `${phrases.slice(0, -1).join(", ")} ${last} ${phrases.at(-1)}`
    : phrases[0];

export const attentionItems = (attention) =>
  (attention?.items || []).filter((item) =>
    Object.hasOwn(ATTENTION, item.kind),
  );

export const attentionDocuments = (items = []) =>
  new Set(attentionItems({ items }).map((item) => item.documentId));

export const attentionRow = (item, today) => {
  const date = item.date ? dayMonthFrom(parseDate(item.date), today) : "";
  const days = item.days == null ? NaN : Number(item.days);
  const known = Number.isFinite(days);
  const [state, when] = {
    paymentReport: ["Payment reported", date && `Paid on ${date}`],
    overdue: [known ? `${dayCount(days)} late` : "", date && `Due ${date}`],
    dueSoon: [
      known
        ? soon(days, "Due today", "Due tomorrow", `Due in ${days} days`)
        : "",
      date && `Due ${date}`,
    ],
    draft: ["Draft", date && `Created ${date}`],
  }[item.kind] || ["", date];
  return {
    label: ATTENTION[item.kind]?.label || "",
    state,
    when,
    to: `/document/${item.documentId}`,
  };
};

export const attentionFooter = (counts = {}, items = []) => {
  const none = ATTENTION_ORDER.filter(
    (kind) => !counts[ATTENTION[kind].count],
  ).map((kind) => ATTENTION[kind].none);
  const more = ATTENTION_ORDER.flatMap((kind) => {
    const {
      count,
      more: [one, many],
    } = ATTENTION[kind];
    const shown = items.filter((item) => item.kind === kind).length;
    const left = (Number(counts[count]) || 0) - shown;
    return left > 0
      ? [
          `${left.toLocaleString("en")} ${shown ? "more " : ""}${left === 1 ? one : many}`,
        ]
      : [];
  });
  const sentences = [
    none.length && `No ${listed(none, "or")}.`,
    more.length && `Also ${listed(more, "and")}.`,
  ].filter(Boolean);
  return sentences.length ? sentences.join(" ") : null;
};

export const STATUS_LABELS = {
  Paid: "Paid",
  "Not Fully Paid": "Partially paid",
  "Not Paid": "Unpaid",
};

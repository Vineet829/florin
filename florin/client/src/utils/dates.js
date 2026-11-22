export const parseDate = (value) => {
  const day = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value || "");
  return day ? new Date(day[1], day[2] - 1, day[3]) : new Date(value);
};

export const dayMonth = (date) =>
  date.toLocaleDateString("en", { month: "short", day: "numeric" });
export const dayMonthYear = (date) =>
  date.toLocaleDateString("en", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });

export const dayMonthFrom = (date, today) =>
  date.getFullYear() === today.getFullYear()
    ? dayMonth(date)
    : dayMonthYear(date);

export const dateSpan = (from, to, withYear = true) => {
  const start = parseDate(from);
  const end = parseDate(to);
  if (start.getFullYear() !== end.getFullYear()) {
    return `${dayMonthYear(start)} - ${dayMonthYear(end)}`;
  }
  return `${dayMonth(start)} - ${withYear ? dayMonthYear(end) : dayMonth(end)}`;
};

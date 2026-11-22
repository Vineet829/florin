const localeOf = (currency) =>
  String(currency || "").toUpperCase() === "INR" ? "en-IN" : "en";

const formats = new Map();
const numberFormat = (locale, options) => {
  const key = `${locale} ${JSON.stringify(options)}`;
  if (!formats.has(key)) {
    formats.set(key, new Intl.NumberFormat(locale, options));
  }
  return formats.get(key);
};

export const formatAmount = (value, currency) =>
  numberFormat(localeOf(currency), {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number(value) || 0);

export const compactAmount = (value, currency) =>
  numberFormat(localeOf(currency), {
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(Number(value) || 0);

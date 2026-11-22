import { formatAmount } from "../../utils/money";

export const amountsOf = (
  totals = [],
  key,
  { several = totals.length > 1, all = false } = {},
) =>
  totals
    .filter((entry) => all || Number(entry[key]) > 0)
    .map((entry) => {
      const amount = formatAmount(entry[key], entry.currency);
      return several
        ? `${amount} ${entry.currency || "with no currency"}`
        : amount;
    })
    .join(", ");

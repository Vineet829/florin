export const TAX_WITHHELD_LABEL = "Tax withheld by client (TDS)";
export const TAX_WITHHELD_HINT =
  "Paid to the tax office for you. It counts toward the balance.";

export const cents = (value) => Math.round(Number(value || 0) * 100) / 100;

export const countOf = (count, one, many) =>
  `${Number(count || 0).toLocaleString("en")} ${count === 1 ? one : many}`;
export const invoiceCount = (count) => countOf(count, "invoice", "invoices");

import { normalizeHsnSac } from "../../gst/hsnSac";

export const roundMoney = (value) =>
  Number(`${Math.round(Number(`${Number(value).toPrecision(15)}e2`))}e-2`);
export const lineAmount = (item) =>
  roundMoney(
    (Number(item.quantity) || 0) *
      (Number(item.unitPrice) || 0) *
      (1 - (Number(item.discount) || 0) / 100),
  );
export const taxGroups = (items) => {
  const groups = new Map();
  for (const item of items) {
    const rate = Number(item.taxRate) || 0;
    groups.set(rate, roundMoney((groups.get(rate) || 0) + lineAmount(item)));
  }
  return [...groups]
    .sort(([a], [b]) => a - b)
    .map(([rate, taxable]) => ({
      rate,
      taxable,
      tax: roundMoney((taxable * rate) / 100),
    }));
};
export const gstPlace = ({ chosen, customer, sellerState }) =>
  chosen ||
  (customer?.gstin ? customer.gstin.slice(0, 2) : customer?.stateCode) ||
  sellerState ||
  "";

export const gstSupply = ({ place, sellerState, exportType }) =>
  !place
    ? null
    : place === "96"
      ? exportType === "IGST"
        ? "EXPORT_IGST"
        : "EXPORT_LUT"
      : place === sellerState
        ? "INTRA"
        : "INTER";

export const gstTotals = (items, supply, reverseCharge) => {
  const lut = supply === "EXPORT_LUT";
  const base = documentTotals(
    lut ? items.map((item) => ({ ...item, taxRate: 0 })) : items,
  );
  const groups = base.groups.map((group) => {
    if (supply === "INTRA") {
      const half = roundMoney((group.taxable * group.rate) / 200);
      return {
        ...group,
        cgst: half,
        sgst: half,
        igst: 0,
        tax: roundMoney(half * 2),
      };
    }
    return { ...group, cgst: 0, sgst: 0, igst: group.tax };
  });
  const tax = roundMoney(groups.reduce((sum, group) => sum + group.tax, 0));
  const salesTax = reverseCharge ? 0 : tax;
  return {
    ...base,
    groups,
    salesTax,
    total: roundMoney(base.subTotal + salesTax),
  };
};
export const documentTotals = (items) => {
  const subTotal = roundMoney(
    items.reduce((sum, item) => sum + lineAmount(item), 0),
  );
  const groups = taxGroups(items);
  const salesTax = roundMoney(
    groups.reduce((sum, group) => sum + group.tax, 0),
  );
  return { subTotal, groups, salesTax, total: roundMoney(subTotal + salesTax) };
};
export const customerSnapshot = (customer) =>
  customer &&
  Object.fromEntries(
    [
      "name",
      "email",
      "accountNo",
      "vatTinNo",
      "address",
      "city",
      "country",
      "phoneNumber",
    ].map((key) => [key, String(customer[key] ?? "")]),
  );
export const documentPayload = ({
  customer,
  documentType,
  issueDate,
  dueDate,
  currency,
  exchangeRate,
  items,
  additionalInfo,
  termsConditions,
  showPaymentDetails,
}) => ({
  customerId: customer?._id || undefined,
  customer: customerSnapshot(customer),
  documentType,
  issueDate: issueDate || undefined,
  dueDate,
  additionalInfo,
  termsConditions,
  showPaymentDetails:
    documentType === "Invoice" ? showPaymentDetails : undefined,
  currency,
  exchangeRate: exchangeRate === undefined ? undefined : exchangeRate.trim(),
  billingItems: items.map((item) => ({
    itemName: item.itemName || "",
    quantity: Number(item.quantity) || 0,
    unitPrice: Number(item.unitPrice) || 0,
    discount: String(item.discount || "0"),
    unit: item.unit?.trim() || undefined,
    taxRate: String(item.taxRate || "0"),
    hsnSac: normalizeHsnSac(item.hsnSac) || undefined,
  })),
});

export const creditNotePayload = (payload, creditReason) => ({
  issueDate: payload.issueDate,
  billingItems: payload.billingItems,
  additionalInfo: payload.additionalInfo,
  termsConditions: payload.termsConditions,
  creditReason: creditReason.trim(),
});

export const matchClients = (clients, query) => {
  const term = query.trim().toLowerCase();
  return clients
    .filter(
      (client) =>
        !term ||
        [client.name, client.email, client.accountNo].some((value) =>
          String(value || "")
            .toLowerCase()
            .includes(term),
        ),
    )
    .sort((a, b) => String(a.name).localeCompare(String(b.name)));
};

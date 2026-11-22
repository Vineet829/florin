import { expect, it } from "vitest";
import {
  matchClients,
  customerSnapshot,
  creditNotePayload,
  documentPayload,
  documentTotals,
  gstPlace,
  gstSupply,
  gstTotals,
  lineAmount,
  taxGroups,
} from "./documentEditorModel";

const customer = {
  _id: "client-1",
  name: "Morrow & Co.",
  email: "morrow@example.com",
  accountNo: "CUS-001",
  vatTinNo: 100200300,
  address: "18 Mason Street",
  city: "San Francisco",
  country: "United States",
  phoneNumber: "+15550100001",
};
const input = {
  customer,
  documentType: "Invoice",
  dueDate: "2026-10-01",
  currency: "USD",
  additionalInfo: "Project work",
  termsConditions: "Due in 14 days",
  items: [
    {
      itemName: "Design",
      quantity: "2",
      unitPrice: "150",
      discount: "10",
      taxRate: "10",
    },
  ],
};

it("taxes rounded discounted lines", () => {
  expect(
    lineAmount({ quantity: "3", unitPrice: "19.99", discount: "12.5" }),
  ).toBe(52.47);
  expect(lineAmount({ quantity: 1, unitPrice: "1.005", discount: "0" })).toBe(
    1.01,
  );
  expect(lineAmount({ quantity: "1.5", unitPrice: "33.33" })).toBe(50);
  expect(lineAmount({ quantity: "0.3333", unitPrice: "3" })).toBe(1);
  expect(
    lineAmount({ quantity: "2.25", unitPrice: "80", discount: "10" }),
  ).toBe(162);
  expect(
    documentTotals([
      { quantity: 2, unitPrice: 150, discount: 10, taxRate: "10" },
      { quantity: 1, unitPrice: 0.05, discount: 0, taxRate: "10" },
    ]),
  ).toEqual({
    subTotal: 270.05,
    groups: [{ rate: 10, taxable: 270.05, tax: 27.01 }],
    salesTax: 27.01,
    total: 297.06,
  });
  expect(documentTotals(input.items)).toMatchObject({
    subTotal: 270,
    salesTax: 27,
    total: 297,
  });
  expect(documentTotals([])).toEqual({
    subTotal: 0,
    groups: [],
    salesTax: 0,
    total: 0,
  });
});

it("rounds tax once per rate", () => {
  const items = [
    { quantity: 1, unitPrice: "100.05", taxRate: "5" },
    { quantity: 1, unitPrice: "200.03", taxRate: "18" },
    { quantity: 1, unitPrice: "50", taxRate: "18.00" },
    { quantity: 1, unitPrice: "99.99", taxRate: "5" },
  ];
  expect(taxGroups(items)).toEqual([
    { rate: 5, taxable: 200.04, tax: 10 },
    { rate: 18, taxable: 250.03, tax: 45.01 },
  ]);
  expect(documentTotals(items)).toMatchObject({
    subTotal: 450.07,
    salesTax: 55.01,
    total: 505.08,
  });
});

it("sends payment details flag", () => {
  expect(
    documentPayload({ ...input, showPaymentDetails: false }).showPaymentDetails,
  ).toBe(false);
  expect(
    documentPayload({ ...input, showPaymentDetails: true }).showPaymentDetails,
  ).toBe(true);
  expect(
    documentPayload({
      ...input,
      documentType: "Quotation",
      showPaymentDetails: false,
    }).showPaymentDetails,
  ).toBeUndefined();
});

it("serializes only backend fields", () => {
  expect(documentPayload(input)).toEqual({
    customerId: "client-1",
    customer: {
      name: "Morrow & Co.",
      email: "morrow@example.com",
      accountNo: "CUS-001",
      vatTinNo: "100200300",
      address: "18 Mason Street",
      city: "San Francisco",
      country: "United States",
      phoneNumber: "+15550100001",
    },
    documentType: "Invoice",
    issueDate: undefined,
    dueDate: input.dueDate,
    additionalInfo: "Project work",
    termsConditions: "Due in 14 days",
    currency: "USD",
    billingItems: [
      {
        itemName: "Design",
        quantity: 2,
        unitPrice: 150,
        discount: "10",
        unit: undefined,
        taxRate: "10",
      },
    ],
  });
});

it("serializes a credit note", () => {
  const payload = creditNotePayload(
    documentPayload({ ...input, issueDate: "2026-10-02", exchangeRate: "83" }),
    "  Returned goods ",
  );
  expect(Object.keys(payload).sort()).toEqual([
    "additionalInfo",
    "billingItems",
    "creditReason",
    "issueDate",
    "termsConditions",
  ]);
  expect(payload.creditReason).toBe("Returned goods");
  expect(payload.issueDate).toBe("2026-10-02");
});

it("trims the exchange rate", () => {
  const rate = (exchangeRate) =>
    documentPayload({ ...input, exchangeRate }).exchangeRate;
  expect(rate(" 83.25 ")).toBe("83.25");
  expect(rate("")).toBe("");
  expect(rate(undefined)).toBeUndefined();
});

it("trims the line unit", () => {
  const lines = (unit) =>
    documentPayload({
      ...input,
      items: [
        { itemName: "Consulting", quantity: "1.5", unitPrice: "80", unit },
      ],
    }).billingItems;
  expect(lines(" hours ")).toEqual([
    {
      itemName: "Consulting",
      quantity: 1.5,
      unitPrice: 80,
      discount: "0",
      unit: "hours",
      taxRate: "0",
    },
  ]);
  expect(lines("   ")[0].unit).toBeUndefined();
});

it("strips spaces from HSN/SAC", () => {
  const code = (hsnSac) =>
    documentPayload({
      ...input,
      items: [{ itemName: "Design", quantity: "1", unitPrice: "80", hsnSac }],
    }).billingItems[0].hsnSac;
  expect(code(" 9983 91 ")).toBe("998391");
  expect(code("")).toBeUndefined();
  expect(code(undefined)).toBeUndefined();
});

it("omits server-computed fields", () => {
  const result = documentPayload({ ...input, documentType: "Receipt" });
  expect(result.documentType).toBe("Receipt");
  ["status", "subTotal", "salesTax", "total", "totalAmountReceived"].forEach(
    (key) => expect(result).not.toHaveProperty(key),
  );
});

it("omits nulls and database ids", () => {
  expect(customerSnapshot({ name: "Avery", vatTinNo: 0 })).toEqual({
    name: "Avery",
    email: "",
    accountNo: "",
    vatTinNo: "0",
    address: "",
    city: "",
    country: "",
    phoneNumber: "",
  });
});

it("sends unlinked client details", () => {
  const result = documentPayload({ ...input, customer: { name: "Avery" } });
  expect(result).not.toHaveProperty("customerId", expect.anything());
  expect(result.customer.name).toBe("Avery");
});

it("searches clients sorted by name", () => {
  const clients = [
    { name: "Northwind", email: "ap@northwind.test", accountNo: "CUS-2" },
    { name: "Contoso", email: "billing@contoso.test", accountNo: "CUS-1" },
    { name: "Fabrikam", email: "hello@fabrikam.test", accountNo: "CUS-3" },
  ];
  expect(matchClients(clients, "").map((c) => c.name)).toEqual([
    "Contoso",
    "Fabrikam",
    "Northwind",
  ]);
  expect(matchClients(clients, "NORTH").map((c) => c.name)).toEqual([
    "Northwind",
  ]);
  expect(matchClients(clients, "billing@").map((c) => c.name)).toEqual([
    "Contoso",
  ]);
  expect(matchClients(clients, "cus-3").map((c) => c.name)).toEqual([
    "Fabrikam",
  ]);
  expect(matchClients(clients, "nobody")).toEqual([]);
});

it("finds place of supply", () => {
  const client = { gstin: "29AAGCB7383J1Z4", stateCode: "29" };
  expect(gstPlace({ customer: client, sellerState: "27" })).toBe("29");
  expect(gstPlace({ customer: { stateCode: "96" }, sellerState: "27" })).toBe(
    "96",
  );
  expect(gstPlace({ customer: {}, sellerState: "27" })).toBe("27");
  expect(gstPlace({ chosen: "07", customer: client, sellerState: "27" })).toBe(
    "07",
  );
  expect(gstSupply({ place: "27", sellerState: "27" })).toBe("INTRA");
  expect(gstSupply({ place: "29", sellerState: "27" })).toBe("INTER");
  expect(gstSupply({ place: "96", sellerState: "27" })).toBe("EXPORT_LUT");
  expect(
    gstSupply({ place: "96", sellerState: "27", exportType: "IGST" }),
  ).toBe("EXPORT_IGST");
});

it("splits GST like the server", () => {
  const lines = [
    { quantity: "1", unitPrice: "101", taxRate: "5" },
    { quantity: "1", unitPrice: "1000", taxRate: "18" },
  ];
  const intra = gstTotals(lines, "INTRA", false);
  expect(intra.groups[0]).toMatchObject({ cgst: 2.53, sgst: 2.53, tax: 5.06 });
  expect(intra.total).toBe(1286.06);
  expect(gstTotals(lines, "INTER", false).groups[0]).toMatchObject({
    igst: 5.05,
    tax: 5.05,
  });
  expect(gstTotals(lines, "INTER", true)).toMatchObject({
    salesTax: 0,
    total: 1101,
  });
  expect(gstTotals(lines, "EXPORT_LUT", false).total).toBe(1101);
});

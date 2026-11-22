const listLink = (path, filters) => {
  const search = new URLSearchParams(
    Object.entries(filters).filter(([, value]) => value),
  );
  return `${path}${search.toString() ? `?${search}` : ""}`;
};

export const documentsLink = (filters) => listLink("/documents", filters);

export const outstandingLink = (currency) =>
  documentsLink({
    status: "outstanding",
    currency,
    sort: "dueDate",
    dir: "asc",
  });

export const overdueLink = (currency) =>
  documentsLink({ status: "overdue", currency });

export const salesLink = (period, currency) =>
  listLink("/sales", { period, currency });

export const paymentsLink = (period, currency) =>
  listLink("/payments", { period, currency });

export const expensesLink = (filters) => listLink("/expenses", filters);

export const customerSalesLink = (period, currency) =>
  listLink("/customers/sales", { period, currency });

export const itemsSoldLink = (period, currency) =>
  listLink("/items/sales", { period, currency });

const ORDER_AND_PAGE = ["sort", "dir", "page", "size"];
export const opensList = (link, path, read, list) => {
  const [linkPath, search] = link.split("?");
  if (linkPath !== path) return false;
  const opened = read(new URLSearchParams(search));
  return Object.keys(opened).every(
    (key) => ORDER_AND_PAGE.includes(key) || opened[key] === list[key],
  );
};

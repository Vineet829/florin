export const readItemList = (params) => ({ q: params.get("q") || "" });

export const itemListSearch = ({ q }) => {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  return params;
};

export const matchingItems = (items, q) => {
  const typed = q.trim().toLowerCase();
  if (!typed) return items;
  return items.filter((item) =>
    [item.name, item.unit].some(
      (field) =>
        typeof field === "string" && field.toLowerCase().includes(typed),
    ),
  );
};

import {
  readSortAndPage,
  sortOptions,
  writeSortAndPage,
} from "../../utils/listState";

export const SORT_FIELDS = {
  createdAt: { first: "desc", asc: "Oldest first", desc: "Newest first" },
  name: { first: "asc", asc: "Name A-Z", desc: "Name Z-A" },
  balance: {
    first: "desc",
    asc: "Smallest balance",
    desc: "Largest balance",
  },
  documents: {
    first: "desc",
    asc: "Fewest documents",
    desc: "Most documents",
  },
};

export const SORT_OPTIONS = sortOptions(SORT_FIELDS);

export const readClientList = (params) => ({
  q: params.get("q") || "",
  ...readSortAndPage(params, SORT_FIELDS, "createdAt"),
});

export const clientListSearch = ({ q, ...sortAndPage }) => {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  return writeSortAndPage(params, sortAndPage, SORT_FIELDS, "createdAt");
};

export const clientFilterQuery = ({ q, sort, dir }) => ({
  sort,
  dir,
  ...(q && { q }),
});

export const clientQuery = (list) => ({
  page: list.page,
  size: list.size,
  ...clientFilterQuery(list),
});

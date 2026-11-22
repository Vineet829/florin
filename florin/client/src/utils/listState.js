export const PAGE_SIZES = [10, 25, 50, 100];
const DEFAULT_SIZE = 10;

export const sortOptions = (fields) =>
  Object.entries(fields).flatMap(([field, labels]) =>
    [labels.first, labels.first === "asc" ? "desc" : "asc"].map((dir) => ({
      value: `${field}:${dir}`,
      label: labels[dir],
    })),
  );

export const readSortAndPage = (params, fields, defaultSort) => {
  const requested = params.get("sort");
  const sort = Object.hasOwn(fields, requested) ? requested : defaultSort;
  const dir = params.get("dir");
  const page = Number(params.get("page"));
  const size = Number(params.get("size"));
  return {
    sort,
    dir: dir === "asc" || dir === "desc" ? dir : fields[sort].first,
    page: Number.isInteger(page) && page > 0 ? page : 1,
    size: PAGE_SIZES.includes(size) ? size : DEFAULT_SIZE,
  };
};

export const writeSortAndPage = (
  params,
  { sort, dir, page, size },
  fields,
  defaultSort,
) => {
  if (sort !== defaultSort || dir !== fields[defaultSort].first) {
    params.set("sort", sort);
    params.set("dir", dir);
  }
  if (page > 1) params.set("page", String(page));
  if (size !== DEFAULT_SIZE) params.set("size", String(size));
  return params;
};

export const nextSort = (fields, { sort, dir }, field) =>
  sort === field
    ? { sort, dir: dir === "asc" ? "desc" : "asc" }
    : { sort: field, dir: fields[field].first };

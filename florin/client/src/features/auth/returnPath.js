export const returnPath = (from) => {
  if (!from?.pathname) return null;
  return `${from.pathname}${from.search ?? ""}${from.hash ?? ""}`;
};

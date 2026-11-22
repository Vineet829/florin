export const normalizeHsnSac = (value) =>
  String(value ?? "").replace(/\s+/g, "");

export const isValidHsnSac = (value) =>
  /^[0-9]{4,8}$/.test(normalizeHsnSac(value));

export const HSN_SAC_PATTERN = "\\s*(\\d\\s*){4,8}";

export const MAX_PASSWORD_BYTES = 72;

export const PASSWORD_TOO_LONG =
  "Password can be at most 72 characters long, or fewer with letters outside the English alphabet";

export const fitsPassword = (value) =>
  new TextEncoder().encode(value ?? "").length <= MAX_PASSWORD_BYTES;

import { expect, it } from "vitest";
import {
  gstinCheckCharacter,
  gstinProblem,
  isValidGstin,
  looksLikeGstin,
  normalizeGstin,
} from "./gstin";

it("accepts valid GSTINs", () => {
  expect(isValidGstin("27AAPFU0939F1ZV")).toBe(true);
  expect(isValidGstin(" 29aagcb7383j1z4 ")).toBe(true);
  expect(gstinCheckCharacter("27AAPFU0939F1Z")).toBe("V");
});

it("rejects invalid GSTINs", () => {
  expect(isValidGstin("27AAPFU0939F1ZW")).toBe(false);
  expect(isValidGstin("27AAPFU0939F1Z")).toBe(false);
  expect(isValidGstin("")).toBe(false);
  expect(looksLikeGstin("27AAPFU0939F1ZW")).toBe(true);
  expect(looksLikeGstin("DE123456789")).toBe(false);
});

it("normalizes input", () => {
  expect(normalizeGstin(" 27aapfu0939f1zv ")).toBe("27AAPFU0939F1ZV");
  expect(normalizeGstin(undefined)).toBe("");
});

it("explains problems once certain", () => {
  expect(gstinProblem("")).toBeNull();
  expect(gstinProblem("27AAPF")).toBeNull();
  expect(gstinProblem("27AAPFU0939F1ZV")).toBeNull();
  expect(gstinProblem("A7")).toBe(
    "A GSTIN starts with the two-digit state code.",
  );
  expect(gstinProblem("27AA-")).toBe("A GSTIN has only letters and digits.");
  expect(gstinProblem("27AAPFU0939F1ZW")).toMatch(/last character/);
});

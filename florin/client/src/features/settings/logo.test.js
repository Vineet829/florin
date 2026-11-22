import { expect, it } from "vitest";
import { logoHint, printsOnPdfs } from "./logo";

const page = new URL("https://invoices.example/settings/business");
const PDFS_ONLY =
  "Your PDFs leave this logo out. Upload it below as a PNG, JPEG or GIF to print it on them too.";
const NOWHERE =
  "Logos from other sites don't show on your documents, share links or PDFs. Upload it below as a PNG, JPEG or GIF.";

it("uploaded images print on PDFs", () => {
  expect(printsOnPdfs("/uploads/3f2c.png")).toBe(true);
  expect(printsOnPdfs("/uploads/3f2c.jpg")).toBe(true);
  expect(printsOnPdfs("/uploads/3f2c.jpeg")).toBe(true);
  expect(printsOnPdfs("/uploads/3f2c.gif")).toBe(true);
});

it("WebP and external logos skip PDFs", () => {
  expect(printsOnPdfs("/uploads/3f2c.webp")).toBe(false);
  expect(printsOnPdfs("https://example.com/logo.png")).toBe(false);
  expect(printsOnPdfs("/uploads/3f2c.png?v=2")).toBe(false);
  expect(printsOnPdfs("/uploads/brand/3f2c.png")).toBe(false);
});

it("no warning without a logo", () => {
  expect(printsOnPdfs("")).toBe(true);
  expect(printsOnPdfs("  ")).toBe(true);
  expect(printsOnPdfs(null)).toBe(true);
  expect(printsOnPdfs(undefined)).toBe(true);
});

it("no warning for working logos", () => {
  expect(logoHint("/uploads/3f2c.png", page)).toBeUndefined();
  expect(logoHint(" /uploads/3f2c.jpeg ", page)).toBeUndefined();
  expect(logoHint("", page)).toBeUndefined();
  expect(logoHint(null, page)).toBeUndefined();
});

it("warns about PDFs only", () => {
  expect(logoHint("/uploads/3f2c.webp", page)).toBe(PDFS_ONLY);
  expect(logoHint("https://invoices.example/uploads/3f2c.png", page)).toBe(
    PDFS_ONLY,
  );
  expect(logoHint("data:image/png;base64,iVBORw0KGgo=", page)).toBe(PDFS_ONLY);
});

it("warns about external logos", () => {
  expect(logoHint("https://res.cloudinary.com/demo/logo.png", page)).toBe(
    NOWHERE,
  );
  expect(logoHint("//example.com/logo.png", page)).toBe(NOWHERE);
  expect(logoHint("https://invoices.example.other/logo.png", page)).toBe(
    NOWHERE,
  );
  expect(logoHint("https://exa mple.com/logo.png", page)).toBe(NOWHERE);
});

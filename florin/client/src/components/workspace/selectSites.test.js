import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { expect, it } from "vitest";

const pages = readdirSync(join(__dirname, "../.."), { recursive: true })
  .filter((file) => /\.jsx$/.test(file) && !/\.test\.jsx$/.test(file))
  .map((file) => join(__dirname, "../..", file));

it("no native selects anywhere", () => {
  expect(pages.length).toBeGreaterThan(50);
  const remaining = pages.filter((file) =>
    /<select[\s>]/.test(readFileSync(file, "utf8")),
  );
  expect(remaining).toEqual([]);
});

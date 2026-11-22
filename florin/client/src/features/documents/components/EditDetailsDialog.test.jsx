import { afterEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import EditDetailsDialog from "./EditDetailsDialog";

vi.mock("../documentsApiSlice", () => ({
  useUpdateDocMutation: () => [vi.fn(), { isLoading: false }],
}));
vi.mock("../../../components/workspace/Dialog", () => ({
  Dialog: ({ children }) => children,
}));

window.IS_REACT_ACT_ENVIRONMENT = true;

let root;
afterEach(() => act(() => root.unmount()));

const dueField = (dueDate) => {
  const container = document.createElement("div");
  root = createRoot(container);
  act(() => {
    root.render(
      <EditDetailsDialog
        open
        onClose={() => {}}
        document={{
          _id: "1",
          documentType: "Invoice",
          documentNumber: "INV-0001",
          issueDate: "2026-01-15",
          dueDate,
        }}
      />,
    );
  });
  return container.querySelector('input[type="date"]');
};

const pick = (input, day) =>
  act(() => {
    Object.getOwnPropertyDescriptor(
      HTMLInputElement.prototype,
      "value",
    ).set.call(input, day);
    input.dispatchEvent(new Event("input", { bubbles: true }));
  });

it("due date not before issue date", () => {
  const field = dueField("2026-02-14");
  pick(field, "2026-01-10");
  expect(field.min).toBe("2026-01-15");
  expect(field.validity.rangeUnderflow).toBe(true);
  pick(field, "2026-01-15");
  expect(field.validity.valid).toBe(true);
});

it("keeps saved early due date", () => {
  const field = dueField("2026-01-10");
  expect(field.validity.valid).toBe(true);
});

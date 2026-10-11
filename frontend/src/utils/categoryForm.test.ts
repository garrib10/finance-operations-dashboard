import { describe, expect, it } from "vitest";
import { groceries, other, petCare, sampleCategories } from "../test/categoryFixtures";
import {
  CATEGORY_SELECTION_FIELDS,
  CREATE_CATEGORY_VALUE,
  buildCategorySelection,
  findEquivalentCategory,
  focusFirstInvalid,
  splitFieldErrors,
  validateCategoryName,
  withoutFieldError,
} from "./categoryForm";

describe("categoryForm", () => {
  it("uses a non-numeric create value distinct from every category ID", () => {
    expect(Number.isNaN(Number(CREATE_CATEGORY_VALUE))).toBe(true);
    expect(sampleCategories.map((category) => String(category.id))).not.toContain(CREATE_CATEGORY_VALUE);
  });

  it("builds exactly one of categoryId or newCategory", () => {
    expect(buildCategorySelection(String(other.id), { name: "ignored", iconKey: "gift" }))
      .toEqual({ categoryId: 13 });
    expect(buildCategorySelection(CREATE_CATEGORY_VALUE, { name: "  Pet  Care ", iconKey: "paw-print" }))
      .toEqual({ newCategory: { name: "  Pet  Care ", iconKey: "paw-print" } });
  });

  it.each([
    ["", "Category name is required"],
    ["   \t ", "Category name is required"],
    ["x".repeat(101), "Category name must be 100 characters or fewer"],
    [`  ${"x".repeat(100)}  `, undefined],
    ["a     b", undefined],
    ["Ünïcödé Café ☕", undefined],
  ])("validates %j", (name, expected) => {
    expect(validateCategoryName(name)).toBe(expected);
  });

  it("keeps listed fields beside controls and turns the rest into summary text", () => {
    expect(splitFieldErrors({
      amount: "Too big",
      "newCategory.name": "Required",
      "<img src=x>": "<b>Other</b>",
      blank: "",
    }, ["amount", ...CATEGORY_SELECTION_FIELDS])).toEqual({
      fieldErrors: { amount: "Too big", "newCategory.name": "Required" },
      otherMessages: ["<b>Other</b>"],
    });
    expect(splitFieldErrors(undefined, ["amount"])).toEqual({ fieldErrors: {}, otherMessages: [] });
  });

  it("removes field errors without mutating the original", () => {
    const errors = { amount: "x", "newCategory.name": "y", type: "z" };
    expect(withoutFieldError(errors, "amount", "newCategory.name")).toEqual({ type: "z" });
    expect(errors).toHaveProperty("amount");
  });

  it("finds the existing category behind a duplicate name", () => {
    expect(findEquivalentCategory(sampleCategories, "  pet   CARE ")).toBe(petCare);
    expect(findEquivalentCategory(sampleCategories, "GROCERIES")).toBe(groceries);
    expect(findEquivalentCategory(sampleCategories, "Pets")).toBeUndefined();
  });

  it("focuses the first invalid control, using the checked radio of a radio group", () => {
    document.body.innerHTML = `
      <form id="f">
        <input id="ok">
        <div role="radiogroup" aria-invalid="true">
          <input type="radio" name="i" id="first"><input type="radio" name="i" id="second" checked>
        </div>
        <input id="later" aria-invalid="true">
      </form>`;

    expect(focusFirstInvalid(document.getElementById("f"))).toBe(true);
    expect(document.activeElement?.id).toBe("second");

    document.body.innerHTML = `<form id="f"><div role="radiogroup" aria-invalid="true"></div></form>`;
    expect(focusFirstInvalid(document.getElementById("f"))).toBe(false);
    expect(focusFirstInvalid(null)).toBe(false);
  });
});

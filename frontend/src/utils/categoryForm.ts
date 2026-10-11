import { DEFAULT_ICON_KEY, type ApprovedIconKey } from "../components/categoryIconRegistry";
import type { CategoryResponse, CategorySelection } from "../types/category";

/**
 * Select value for "Create a custom category…". Never numeric, never sent to the API:
 * it only switches the form into new-category mode. A persisted category named "Other"
 * is an ordinary numeric option.
 */
export const CREATE_CATEGORY_VALUE = "__create_category__";

export const CATEGORY_NAME_MAX_LENGTH = 100;

export interface CategoryDraft {
  name: string;
  iconKey: ApprovedIconKey;
}

export const EMPTY_CATEGORY_DRAFT: CategoryDraft = { name: "", iconKey: DEFAULT_ICON_KEY };

/** Server field names that belong to the category controls of a financial form. */
export const CATEGORY_SELECTION_FIELDS = [
  "categoryId",
  "newCategory",
  "newCategory.name",
  "newCategory.iconKey",
] as const;

export const DUPLICATE_CATEGORY_MESSAGE =
  "You already have a category with this name. Choose it from the list or use a different name.";

/**
 * Early feedback that mirrors the API's rules (required, at most 100 characters once
 * spaces are collapsed). The API stays authoritative; the typed value is sent as is.
 */
export function validateCategoryName(name: string): string | undefined {
  const collapsed = name.trim().replace(/\s+/gu, " ");

  if (!collapsed) {
    return "Category name is required";
  }

  if (collapsed.length > CATEGORY_NAME_MAX_LENGTH) {
    return "Category name must be 100 characters or fewer";
  }

  return undefined;
}

/** Exactly one of categoryId or newCategory, from the form's selection. */
export function buildCategorySelection(selection: string, draft: CategoryDraft): CategorySelection {
  if (selection === CREATE_CATEGORY_VALUE) {
    return { newCategory: { name: draft.name, iconKey: draft.iconKey } };
  }

  return { categoryId: Number(selection) };
}

/**
 * Splits server field errors into those shown beside a known control and the rest,
 * which the form shows as text in its summary. Only listed keys ever reach the DOM as
 * identifiers.
 */
export function splitFieldErrors(
  fields: Record<string, string> | undefined,
  allowed: readonly string[],
): { fieldErrors: Record<string, string>; otherMessages: string[] } {
  const fieldErrors: Record<string, string> = {};
  const otherMessages: string[] = [];

  for (const [field, message] of Object.entries(fields ?? {})) {
    if (typeof message !== "string" || !message) continue;

    if (allowed.includes(field)) {
      fieldErrors[field] = message;
    } else {
      otherMessages.push(message);
    }
  }

  return { fieldErrors, otherMessages };
}

/** Removes one field's error, and every new-category error when leaving that mode. */
export function withoutFieldError(
  errors: Record<string, string>,
  ...fields: string[]
): Record<string, string> {
  const next = { ...errors };
  for (const field of fields) delete next[field];
  return next;
}

/**
 * Best-effort match for offering the existing category after a duplicate response.
 * Display only: the server decides what is a duplicate.
 */
export function findEquivalentCategory(
  categories: CategoryResponse[],
  name: string,
): CategoryResponse | undefined {
  const comparable = (value: string) =>
    value.normalize("NFC").trim().replace(/\s+/gu, " ").toLowerCase().normalize("NFC");
  const target = comparable(name);

  return categories.find((category) => comparable(category.name) === target);
}

/**
 * Focuses the first invalid control in a form, in document order. A radio group is
 * focused through its selected (or first) radio.
 */
export function focusFirstInvalid(form: HTMLElement | null): boolean {
  const invalid = form?.querySelector<HTMLElement>('[aria-invalid="true"]');

  if (!invalid) return false;

  if (invalid.getAttribute("role") === "radiogroup") {
    const radio = invalid.querySelector<HTMLInputElement>("input:checked")
      ?? invalid.querySelector<HTMLInputElement>("input");
    radio?.focus();
    return Boolean(radio);
  }

  invalid.focus();
  return true;
}

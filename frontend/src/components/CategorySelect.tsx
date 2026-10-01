import type { Ref } from "react";
import { useCategories } from "../context/CategoryContext";
import type { CategoryResponse } from "../types/category";
import { CREATE_CATEGORY_VALUE, type CategoryDraft } from "../utils/categoryForm";
import { CategoryIcon } from "./CategoryIcon";
import { CategoryIconPicker } from "./CategoryIconPicker";
import type { ApprovedIconKey } from "./categoryIconRegistry";

export interface CategorySelectErrors {
  /** categoryId, or newCategory for an exactly-one error. */
  selection?: string;
  name?: string;
  iconKey?: string;
}

interface CategorySelectProps {
  id: string;
  label?: string;
  value: string;
  onChange: (value: string) => void;
  draft: CategoryDraft;
  onNameChange: (name: string) => void;
  onNameBlur: () => void;
  onIconChange: (iconKey: ApprovedIconKey) => void;
  errors: CategorySelectErrors;
  disabled?: boolean;
  selectRef?: Ref<HTMLSelectElement>;
  /** Shown after a duplicate-name response when the existing category is known. */
  existingMatch?: CategoryResponse;
  onUseExisting?: (category: CategoryResponse) => void;
}

/**
 * Category choice for transaction and budget forms: a native select of the user's
 * categories plus a separate "Create a custom category…" option that reveals name and
 * icon fields. The new category is saved together with the transaction or budget.
 */
export function CategorySelect({
  id,
  label = "Category",
  value,
  onChange,
  draft,
  onNameChange,
  onNameBlur,
  onIconChange,
  errors,
  disabled = false,
  selectRef,
  existingMatch,
  onUseExisting,
}: CategorySelectProps) {
  const { categories, status, loadError, reload } = useCategories();
  const creating = value === CREATE_CATEGORY_VALUE;
  const selected = categories.find((category) => String(category.id) === value);
  // An edit can start before the list arrives: keep the stored ID until it loads.
  const pendingValue = Boolean(value) && !creating && !selected && status !== "ready";
  const errorId = `${id}-error`;
  const statusId = `${id}-status`;
  const helpId = `${id}-new-help`;
  const nameId = `${id}-new-name`;
  const nameErrorId = `${id}-new-name-error`;

  const describedBy = [
    errors.selection ? errorId : null,
    status === "loading" || status === "error" ? statusId : null,
  ].filter(Boolean).join(" ") || undefined;

  return (
    <div className="category-select">
      <div className="form-field">
        <label htmlFor={id}>{label}</label>

        <div className="category-select__control">
          {(selected || creating) && (
            <CategoryIcon iconKey={selected ? selected.iconKey : draft.iconKey} />
          )}

          <select
            ref={selectRef}
            id={id}
            value={creating || selected || pendingValue ? value : ""}
            disabled={disabled}
            aria-invalid={errors.selection ? true : undefined}
            aria-describedby={describedBy}
            onChange={(event) => onChange(event.target.value)}
            required
          >
            <option value="">Select a category</option>

            {pendingValue && <option value={value}>Loading category…</option>}

            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}

            <option value={CREATE_CATEGORY_VALUE}>Create a custom category…</option>
          </select>
        </div>

        {status === "loading" && (
          <p id={statusId} className="field-hint" role="status">
            Loading categories…
          </p>
        )}

        {status === "error" && (
          <div id={statusId} className="field-hint" role="alert">
            <span>{loadError} You can still create a custom category.</span>{" "}
            <button
              type="button"
              className="button button--secondary button--small"
              onClick={() => {
                void reload();
              }}
            >
              Retry loading categories
            </button>
          </div>
        )}

        {errors.selection && (
          <p id={errorId} className="field-error">
            {errors.selection}
          </p>
        )}
      </div>

      {creating && (
        <fieldset className="category-create">
          <legend>New category</legend>

          <p id={helpId} className="field-hint">
            This category is saved with this entry and is then available everywhere
            you choose a category.
          </p>

          <div className="form-field">
            <label htmlFor={nameId}>Category name</label>

            <input
              id={nameId}
              type="text"
              value={draft.name}
              disabled={disabled}
              autoComplete="off"
              aria-invalid={errors.name ? true : undefined}
              aria-describedby={[helpId, errors.name ? nameErrorId : null].filter(Boolean).join(" ")}
              onChange={(event) => onNameChange(event.target.value)}
              onBlur={onNameBlur}
            />

            {errors.name && (
              <p id={nameErrorId} className="field-error">
                {errors.name}
              </p>
            )}

            {errors.name && existingMatch && onUseExisting && (
              <button
                type="button"
                className="button button--secondary button--small"
                onClick={() => onUseExisting(existingMatch)}
              >
                Use existing category “{existingMatch.name}”
              </button>
            )}
          </div>

          <CategoryIconPicker
            name={`${id}-icon`}
            labelId={`${id}-icon-label`}
            value={draft.iconKey}
            onChange={onIconChange}
            error={errors.iconKey}
            errorId={`${id}-icon-error`}
            disabled={disabled}
          />
        </fieldset>
      )}
    </div>
  );
}

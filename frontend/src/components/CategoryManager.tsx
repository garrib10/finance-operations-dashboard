import { useEffect, useId, useRef, useState } from "react";
import type { SubmitEvent as ReactSubmitEvent } from "react";
import { useCategories } from "../context/CategoryContext";
import { ApiError } from "../services/api";
import {
  CATEGORY_BUILT_IN,
  CATEGORY_DUPLICATE,
  CATEGORY_IN_USE,
  CATEGORY_NOT_FOUND,
} from "../services/categoryService";
import type { CategoryResponse } from "../types/category";
import {
  DUPLICATE_CATEGORY_MESSAGE,
  focusFirstInvalid,
  splitFieldErrors,
  validateCategoryName,
  withoutFieldError,
  type CategoryDraft,
} from "../utils/categoryForm";
import { CategoryLabel } from "./CategoryIcon";
import { CategoryIconPicker } from "./CategoryIconPicker";
import { resolveIconKey } from "./categoryIconRegistry";

export type CategoryChange =
  | { type: "updated"; category: CategoryResponse }
  | { type: "deleted"; categoryId: number };

interface CategoryManagerProps {
  /** Lets the page refresh records that display category names. */
  onChange?: (change: CategoryChange) => void;
}

const MANAGEMENT_FIELDS = ["name", "iconKey"] as const;

type Mode =
  | { kind: "list" }
  | { kind: "edit"; category: CategoryResponse }
  | { kind: "confirmDelete"; category: CategoryResponse };

/**
 * Rename, re-icon, or delete the user's custom categories. Built-in categories are
 * listed without controls (the API refuses those changes regardless).
 */
export function CategoryManager({ onChange }: CategoryManagerProps) {
  const { categories, status, loadError, reload, updateCategory, deleteCategory } = useCategories();
  const baseId = useId();
  const panelId = `${baseId}-panel`;
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState<Mode>({ kind: "list" });
  const [draft, setDraft] = useState<CategoryDraft>({ name: "", iconKey: "tag" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [alert, setAlert] = useState("");
  const [statusMessage, setStatusMessage] = useState("");
  const [failureAttempt, setFailureAttempt] = useState(0);
  const busy = useRef(false);
  const [pending, setPending] = useState(false);
  const formRef = useRef<HTMLFormElement>(null);
  const toggleRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);
  const pendingFocus = useRef<string | null>(null);

  useEffect(() => {
    if (failureAttempt) focusFirstInvalid(formRef.current);
  }, [failureAttempt]);

  useEffect(() => {
    if (mode.kind === "confirmDelete") confirmRef.current?.focus();
  }, [mode]);

  // Returns focus to a row action (or the toggle) once the list has re-rendered.
  useEffect(() => {
    if (!pendingFocus.current || mode.kind !== "list") return;
    const target = pendingFocus.current === "toggle"
      ? toggleRef.current
      : panelRef.current?.querySelector<HTMLElement>(`[data-category-action="${pendingFocus.current}"]`);
    if (target) {
      target.focus();
      pendingFocus.current = null;
    }
  });

  function backToList(focusTarget: string): void {
    pendingFocus.current = focusTarget;
    setErrors({});
    setMode({ kind: "list" });
  }

  function startEdit(category: CategoryResponse): void {
    setAlert("");
    setStatusMessage("");
    setErrors({});
    setDraft({ name: category.name, iconKey: resolveIconKey(category.iconKey) });
    setMode({ kind: "edit", category });
  }

  function startDelete(category: CategoryResponse): void {
    setAlert("");
    setStatusMessage("");
    setMode({ kind: "confirmDelete", category });
  }

  async function handleSave(event: ReactSubmitEvent<HTMLFormElement>, category: CategoryResponse): Promise<void> {
    event.preventDefault();
    if (busy.current) return;

    setAlert("");
    const nameError = validateCategoryName(draft.name);
    if (nameError) {
      setErrors({ name: nameError });
      setFailureAttempt((attempt) => attempt + 1);
      return;
    }

    busy.current = true;
    setPending(true);
    try {
      const updated = await updateCategory(category.id, {
        name: draft.name,
        budgetEnabled: category.budgetEnabled,
        iconKey: draft.iconKey,
      });
      setStatusMessage(`Saved “${updated.name}”.`);
      onChange?.({ type: "updated", category: updated });
      backToList(`edit-${category.id}`);
    } catch (error) {
      if (error instanceof ApiError && error.code === CATEGORY_DUPLICATE) {
        setErrors({ name: DUPLICATE_CATEGORY_MESSAGE });
        setAlert("That name is already used by another of your categories.");
      } else if (error instanceof ApiError && (error.code === CATEGORY_NOT_FOUND || error.code === CATEGORY_BUILT_IN)) {
        setAlert(error.code === CATEGORY_NOT_FOUND
          ? "This category no longer exists. The list has been refreshed."
          : error.message);
        void reload();
        backToList("toggle");
      } else if (error instanceof ApiError && error.status === 400) {
        const { fieldErrors, otherMessages } = splitFieldErrors(error.validationErrors, MANAGEMENT_FIELDS);
        setErrors(fieldErrors);
        setAlert(otherMessages.join(" ") || "Please check the highlighted fields.");
      } else {
        setAlert("Unable to save the category. Please try again.");
      }
      setFailureAttempt((attempt) => attempt + 1);
    } finally {
      busy.current = false;
      setPending(false);
    }
  }

  async function handleDelete(category: CategoryResponse): Promise<void> {
    if (busy.current) return;

    busy.current = true;
    setPending(true);
    setAlert("");
    try {
      await deleteCategory(category.id);
      setStatusMessage(`Deleted “${category.name}”.`);
      onChange?.({ type: "deleted", categoryId: category.id });
      backToList("toggle");
    } catch (error) {
      if (error instanceof ApiError && error.code === CATEGORY_IN_USE) {
        setAlert(error.message);
        backToList(`delete-${category.id}`);
      } else if (error instanceof ApiError && error.code === CATEGORY_NOT_FOUND) {
        setAlert("This category no longer exists. The list has been refreshed.");
        void reload();
        backToList("toggle");
      } else if (error instanceof ApiError && error.code === CATEGORY_BUILT_IN) {
        setAlert(error.message);
        backToList("toggle");
      } else {
        setAlert("Unable to delete the category. Please try again.");
        backToList(`delete-${category.id}`);
      }
    } finally {
      busy.current = false;
      setPending(false);
    }
  }

  return (
    <section className="category-manager" aria-labelledby={`${baseId}-heading`}>
      <div className="category-manager__header">
        <h2 id={`${baseId}-heading`}>Categories</h2>

        <button
          ref={toggleRef}
          type="button"
          className="button button--secondary"
          aria-expanded={open}
          aria-controls={panelId}
          onClick={() => {
            setOpen((value) => !value);
            setMode({ kind: "list" });
            setAlert("");
            setStatusMessage("");
          }}
        >
          {open ? "Hide categories" : "Manage categories"}
        </button>
      </div>

      {open && (
        <div id={panelId} ref={panelRef} className="category-manager__panel">
          <p className="field-hint">
            Built-in categories cannot be changed. Renaming a custom category updates it on every
            transaction and budget that uses it.
          </p>

          <p role="status" className="form-status">
            {statusMessage}
          </p>

          {alert && (
            <p role="alert" className="form-error">
              {alert}
            </p>
          )}

          {status === "loading" && <p role="status">Loading categories…</p>}

          {status === "error" && (
            <div role="alert" className="form-error">
              <span>{loadError}</span>{" "}
              <button type="button" className="button button--secondary button--small" onClick={() => void reload()}>
                Retry loading categories
              </button>
            </div>
          )}

          <ul className="category-manager__list">
            {categories.map((category) => (
              <li key={category.id} className="category-manager__item">
                {mode.kind === "edit" && mode.category.id === category.id ? (
                  <form
                    ref={formRef}
                    className="category-manager__form"
                    aria-label={`Edit ${category.name}`}
                    onSubmit={(event) => void handleSave(event, category)}
                    noValidate
                  >
                    <div className="form-field">
                      <label htmlFor={`${baseId}-name-${category.id}`}>Category name</label>
                      <input
                        id={`${baseId}-name-${category.id}`}
                        type="text"
                        value={draft.name}
                        autoComplete="off"
                        disabled={pending}
                        aria-invalid={errors.name ? true : undefined}
                        aria-describedby={errors.name ? `${baseId}-name-error-${category.id}` : undefined}
                        onChange={(event) => {
                          setDraft({ ...draft, name: event.target.value });
                          setErrors(withoutFieldError(errors, "name"));
                        }}
                        onBlur={() => {
                          const nameError = validateCategoryName(draft.name);
                          if (nameError) setErrors({ ...errors, name: nameError });
                        }}
                      />
                      {errors.name && (
                        <p id={`${baseId}-name-error-${category.id}`} className="field-error">
                          {errors.name}
                        </p>
                      )}
                    </div>

                    <CategoryIconPicker
                      name={`${baseId}-icon-${category.id}`}
                      labelId={`${baseId}-icon-label-${category.id}`}
                      value={draft.iconKey}
                      onChange={(iconKey) => {
                        setDraft({ ...draft, iconKey });
                        setErrors(withoutFieldError(errors, "iconKey"));
                      }}
                      error={errors.iconKey}
                      errorId={`${baseId}-icon-error-${category.id}`}
                      disabled={pending}
                    />

                    <div className="category-manager__actions">
                      <button type="submit" className="button button--primary" disabled={pending}>
                        {pending ? "Saving…" : "Save category"}
                      </button>
                      <button
                        type="button"
                        className="button button--secondary"
                        disabled={pending}
                        onClick={() => backToList(`edit-${category.id}`)}
                      >
                        Cancel
                      </button>
                    </div>
                  </form>
                ) : (
                  <>
                    <CategoryLabel name={category.name} iconKey={category.iconKey} />

                    {category.builtIn ? (
                      <span className="category-badge">Built-in</span>
                    ) : mode.kind === "confirmDelete" && mode.category.id === category.id ? (
                      <div role="group" aria-labelledby={`${baseId}-confirm-${category.id}`} className="category-manager__confirm">
                        <span id={`${baseId}-confirm-${category.id}`}>
                          Delete “{category.name}”? This cannot be undone.
                        </span>
                        <button
                          ref={confirmRef}
                          type="button"
                          className="button button--danger"
                          disabled={pending}
                          onClick={() => void handleDelete(category)}
                        >
                          {pending ? "Deleting…" : "Delete category"}
                        </button>
                        <button
                          type="button"
                          className="button button--secondary"
                          disabled={pending}
                          onClick={() => backToList(`delete-${category.id}`)}
                        >
                          Keep category
                        </button>
                      </div>
                    ) : (
                      <div className="category-manager__actions">
                        <button
                          type="button"
                          className="button button--secondary button--small"
                          data-category-action={`edit-${category.id}`}
                          aria-label={`Edit ${category.name}`}
                          disabled={mode.kind !== "list"}
                          onClick={() => startEdit(category)}
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          className="button button--secondary button--small"
                          data-category-action={`delete-${category.id}`}
                          aria-label={`Delete ${category.name}`}
                          disabled={mode.kind !== "list"}
                          onClick={() => startDelete(category)}
                        >
                          Delete
                        </button>
                      </div>
                    )}
                  </>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}

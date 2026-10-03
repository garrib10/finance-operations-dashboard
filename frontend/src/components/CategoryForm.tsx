import { useEffect, useId, useRef, useState } from "react";
import type { SubmitEvent as ReactSubmitEvent } from "react";
import { ApiError } from "../services/api";
import { CATEGORY_DUPLICATE } from "../services/categoryService";
import {
  DUPLICATE_CATEGORY_MESSAGE,
  focusFirstInvalid,
  splitFieldErrors,
  validateCategoryName,
  withoutFieldError,
  type CategoryDraft,
} from "../utils/categoryForm";
import { CategoryIconPicker } from "./CategoryIconPicker";

const CATEGORY_FIELDS = ["name", "iconKey"] as const;

interface CategoryFormProps {
  /** Accessible name of the form, for example "Create category" or "Edit Pet Care". */
  label: string;
  initial: CategoryDraft;
  submitLabel: string;
  pendingLabel: string;
  /** Shown for an unexpected failure; the draft is kept for another attempt. */
  failureMessage: string;
  /**
   * Saves the draft. Errors it throws are shown on the form (duplicate names and field
   * messages beside their inputs); errors it handles itself never reach the form.
   */
  onSubmit: (draft: CategoryDraft) => Promise<void>;
  onCancel: () => void;
  /** Focus the name field when the form opens (for the create form). */
  focusNameOnOpen?: boolean;
}

/** Name and icon fields for creating or editing a custom category. */
export function CategoryForm({
  label,
  initial,
  submitLabel,
  pendingLabel,
  failureMessage,
  onSubmit,
  onCancel,
  focusNameOnOpen = false,
}: CategoryFormProps) {
  const baseId = useId();
  const [draft, setDraft] = useState<CategoryDraft>(initial);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [alert, setAlert] = useState("");
  const [pending, setPending] = useState(false);
  const [failureAttempt, setFailureAttempt] = useState(0);
  const busy = useRef(false);
  const formRef = useRef<HTMLFormElement>(null);
  const nameRef = useRef<HTMLInputElement>(null);
  const alertRef = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    if (focusNameOnOpen) nameRef.current?.focus();
  }, [focusNameOnOpen]);

  useEffect(() => {
    // The first invalid field; with none (a general failure) the form's error message, so
    // focus is not lost when the disabled submit button comes back.
    if (failureAttempt && !focusFirstInvalid(formRef.current)) alertRef.current?.focus();
  }, [failureAttempt]);

  async function handleSubmit(event: ReactSubmitEvent<HTMLFormElement>): Promise<void> {
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
      await onSubmit(draft);
    } catch (error) {
      if (error instanceof ApiError && error.code === CATEGORY_DUPLICATE) {
        setErrors({ name: DUPLICATE_CATEGORY_MESSAGE });
        setAlert("That name is already used by another of your categories.");
      } else if (error instanceof ApiError && error.status === 400) {
        const { fieldErrors, otherMessages } = splitFieldErrors(error.validationErrors, CATEGORY_FIELDS);
        setErrors(fieldErrors);
        setAlert(otherMessages.join(" ") || "Please check the highlighted fields.");
      } else {
        setAlert(failureMessage);
      }
      setFailureAttempt((attempt) => attempt + 1);
    } finally {
      busy.current = false;
      setPending(false);
    }
  }

  return (
    <form
      ref={formRef}
      className="category-form"
      aria-label={label}
      onSubmit={(event) => void handleSubmit(event)}
      noValidate
    >
      {alert && (
        <p ref={alertRef} role="alert" className="form-error" tabIndex={-1}>
          {alert}
        </p>
      )}

      <div className="form-field">
        <label htmlFor={`${baseId}-name`}>Category name</label>
        <input
          ref={nameRef}
          id={`${baseId}-name`}
          type="text"
          value={draft.name}
          autoComplete="off"
          disabled={pending}
          aria-invalid={errors.name ? true : undefined}
          aria-describedby={errors.name ? `${baseId}-name-error` : undefined}
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
          <p id={`${baseId}-name-error`} className="field-error">
            {errors.name}
          </p>
        )}
      </div>

      <CategoryIconPicker
        name={`${baseId}-icon`}
        labelId={`${baseId}-icon-label`}
        value={draft.iconKey}
        onChange={(iconKey) => {
          setDraft({ ...draft, iconKey });
          setErrors(withoutFieldError(errors, "iconKey"));
        }}
        error={errors.iconKey}
        errorId={`${baseId}-icon-error`}
        disabled={pending}
      />

      <div className="category-form__actions">
        <button type="submit" className="button button--primary" disabled={pending}>
          {pending ? pendingLabel : submitLabel}
        </button>
        <button type="button" className="button button--secondary" disabled={pending} onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  );
}

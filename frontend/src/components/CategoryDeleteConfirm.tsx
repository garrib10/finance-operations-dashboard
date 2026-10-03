import { useEffect, useId, useRef, useState } from "react";

interface CategoryDeleteConfirmProps {
  categoryName: string;
  /** Deletes the category; the caller handles failures and closes the confirmation. */
  onConfirm: () => Promise<void>;
  onCancel: () => void;
}

/** An inline confirmation (not window.confirm) that guards against double submission. */
export function CategoryDeleteConfirm({ categoryName, onConfirm, onCancel }: CategoryDeleteConfirmProps) {
  const labelId = useId();
  const [pending, setPending] = useState(false);
  const busy = useRef(false);
  const confirmRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    confirmRef.current?.focus();
  }, []);

  async function handleConfirm(): Promise<void> {
    if (busy.current) return;
    busy.current = true;
    setPending(true);
    try {
      await onConfirm();
    } finally {
      busy.current = false;
      setPending(false);
    }
  }

  return (
    <div role="group" aria-labelledby={labelId} className="category-delete-confirm">
      <p id={labelId} className="category-delete-confirm__message">
        Delete “{categoryName}”? This cannot be undone.
      </p>
      <div className="category-form__actions">
        <button
          ref={confirmRef}
          type="button"
          className="button button--danger"
          disabled={pending}
          onClick={() => void handleConfirm()}
        >
          {pending ? "Deleting…" : "Delete category"}
        </button>
        <button type="button" className="button button--secondary" disabled={pending} onClick={onCancel}>
          Keep category
        </button>
      </div>
    </div>
  );
}

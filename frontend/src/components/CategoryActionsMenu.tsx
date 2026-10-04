import { Ellipsis, Pencil, Trash2 } from "lucide-react";
import { useEffect, useRef, type FocusEvent, type KeyboardEvent } from "react";
import { categoryCardIds } from "../utils/categoryUsage";

interface CategoryActionsMenuProps {
  categoryId: number;
  categoryName: string;
  /** Controlled by the page, so only one card's actions are open at a time. */
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onEdit: () => void;
  onDelete: () => void;
  /** Shown under an unavailable Delete; null when the category can be deleted. */
  deleteBlockedReason: string | null;
}

/**
 * A custom category's Edit and Delete, behind a "More actions" button. This is a
 * disclosure (a button that shows ordinary buttons), not an ARIA menu: opening leaves
 * focus on the trigger and Tab moves through the actions in document order. It closes on
 * the trigger, Escape (focus returns to the trigger), a click outside, or focus moving
 * elsewhere on the page.
 */
export function CategoryActionsMenu({
  categoryId,
  categoryName,
  open,
  onOpenChange,
  onEdit,
  onDelete,
  deleteBlockedReason,
}: CategoryActionsMenuProps) {
  const ids = categoryCardIds(categoryId);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;

    function handlePointerDown(event: PointerEvent): void {
      if (event.target instanceof Node && !wrapperRef.current?.contains(event.target)) {
        onOpenChange(false); // Focus stays wherever the user clicked.
      }
    }

    document.addEventListener("pointerdown", handlePointerDown);
    return () => document.removeEventListener("pointerdown", handlePointerDown);
  }, [open, onOpenChange]);

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>): void {
    if (event.key !== "Escape" || !open) return;
    event.stopPropagation();
    onOpenChange(false);
    triggerRef.current?.focus();
  }

  function handleBlur(event: FocusEvent<HTMLDivElement>): void {
    // Only when focus moves to something else on the page. Some browsers (Safari) give a
    // clicked button no focus, so a null target is left to the outside-click handler;
    // closing here would swallow the click on an action.
    const next = event.relatedTarget;
    if (open && next instanceof Node && !wrapperRef.current?.contains(next)) onOpenChange(false);
  }

  return (
    <div className="category-actions" ref={wrapperRef} onKeyDown={handleKeyDown} onBlur={handleBlur}>
      <button
        ref={triggerRef}
        id={ids.actions}
        type="button"
        className="category-actions__trigger"
        aria-label={`More actions for ${categoryName}`}
        aria-expanded={open}
        aria-controls={ids.actionsPanel}
        onClick={() => onOpenChange(!open)}
      >
        <Ellipsis aria-hidden="true" focusable="false" size={20} />
      </button>

      <div id={ids.actionsPanel} className="category-actions__panel" hidden={!open}>
        <button
          id={ids.edit}
          type="button"
          className="category-actions__item"
          aria-label={`Edit ${categoryName}`}
          onClick={() => {
            onOpenChange(false);
            onEdit();
          }}
        >
          <Pencil aria-hidden="true" focusable="false" size={16} />
          Edit
        </button>

        {deleteBlockedReason === null ? (
          <button
            id={ids.delete}
            type="button"
            className="category-actions__item category-actions__item--danger"
            aria-label={`Delete ${categoryName}`}
            onClick={() => {
              onOpenChange(false);
              onDelete();
            }}
          >
            <Trash2 aria-hidden="true" focusable="false" size={16} />
            Delete
          </button>
        ) : (
          <>
            {/* Focusable (aria-disabled, not disabled) so keyboard users reach the reason. */}
            <button
              id={ids.delete}
              type="button"
              className="category-actions__item"
              aria-label={`Delete ${categoryName}`}
              aria-disabled="true"
              aria-describedby={ids.deleteReason}
              onClick={(event) => event.preventDefault()}
            >
              <Trash2 aria-hidden="true" focusable="false" size={16} />
              Delete
            </button>
            <p id={ids.deleteReason} className="category-actions__reason">
              {deleteBlockedReason}
            </p>
          </>
        )}
      </div>
    </div>
  );
}

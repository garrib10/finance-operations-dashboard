import { useEffect, useRef, type RefObject } from "react";
import { Link } from "react-router-dom";
import { X } from "lucide-react";
import { PrimaryNav } from "./PrimaryNav";

export const MOBILE_NAV_ID = "mobile-navigation";

interface MobileNavDrawerProps {
  open: boolean;
  onClose: () => void;
  /** The menu button; focus returns here when the drawer closes. */
  returnFocusRef: RefObject<HTMLElement | null>;
}

/**
 * Navigation drawer for screens 1100px and narrower. It is a native modal <dialog>,
 * so the browser contains focus, makes the page behind it inert, and reports Escape
 * as a cancel event. Escape, the close button, a backdrop click, following a link,
 * and unmounting (logout, expired session) all close it and unlock page scrolling.
 */
export function MobileNavDrawer({ open, onClose, returnFocusRef }: MobileNavDrawerProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;

    // The dialog is always rendered, and the cleanup below closes it, so it is closed here.
    const dialog = dialogRef.current!;
    dialog.showModal();
    closeButtonRef.current?.focus();
    document.documentElement.classList.add("scroll-locked");
    const returnTarget = returnFocusRef.current;

    return () => {
      document.documentElement.classList.remove("scroll-locked");
      if (dialog.open) dialog.close();
      // After logout the menu button is gone; never focus a detached element.
      if (returnTarget?.isConnected) returnTarget.focus();
    };
  }, [open, returnFocusRef]);

  return (
    <dialog
      ref={dialogRef}
      id={MOBILE_NAV_ID}
      className="mobile-nav"
      aria-label="Navigation menu"
      onCancel={(event) => {
        // Escape: close through state so React stays the source of truth.
        event.preventDefault();
        onClose();
      }}
      onClose={onClose}
      onClick={(event) => {
        // The panel fills the dialog, so a click on the dialog itself is the backdrop.
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div className="mobile-nav__panel">
        <div className="mobile-nav__header">
          <Link className="app-brand" to="/" onClick={onClose}>
            FinTrack
          </Link>

          <button
            ref={closeButtonRef}
            type="button"
            className="mobile-nav__close"
            aria-label="Close navigation menu"
            onClick={onClose}
          >
            <X aria-hidden="true" size={20} />
          </button>
        </div>

        <PrimaryNav onNavigate={onClose} />
      </div>
    </dialog>
  );
}

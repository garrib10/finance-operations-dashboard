import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { getAccountName } from "../utils/accountIdentity";
import { Avatar } from "./Avatar";

/**
 * The signed-in user's account menu: identity, Profile, Account Settings, and Logout.
 * It sits at the bottom of the navigation (the desktop sidebar, or the drawer at 1100px
 * and below; issue #121), so its panel opens upward.
 */
export function AccountMenu() {
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const [isAccountMenuOpen, setIsAccountMenuOpen] = useState(false);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [logoutError, setLogoutError] = useState("");
  const accountMenuRef = useRef<HTMLDivElement>(null);
  const accountTriggerRef = useRef<HTMLButtonElement>(null);

  const fullName = getAccountName(user);

  useEffect(() => {
    if (!isAccountMenuOpen) {
      return;
    }

    function handlePointerDown(event: PointerEvent): void {
      if (
        event.target instanceof Node &&
        !accountMenuRef.current?.contains(event.target)
      ) {
        setIsAccountMenuOpen(false);
      }
    }

    function handleKeyDown(event: KeyboardEvent): void {
      if (event.key !== "Escape") {
        return;
      }

      // Escape closes only this menu: cancelling the key keeps the drawer <dialog> open.
      event.preventDefault();
      event.stopPropagation();
      setIsAccountMenuOpen(false);
      accountTriggerRef.current?.focus();
    }

    document.addEventListener("pointerdown", handlePointerDown);
    // Capture phase: runs before the drawer dialog treats Escape as a cancel.
    document.addEventListener("keydown", handleKeyDown, true);

    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown, true);
    };
  }, [isAccountMenuOpen]);

  if (!isAuthenticated) {
    return null;
  }

  // Repeat clicks are blocked by the disabled button and de-duplicated by AuthProvider.
  async function handleLogout(): Promise<void> {
    setIsLoggingOut(true);
    setLogoutError("");
    try {
      await logout();
      setIsAccountMenuOpen(false);
      navigate("/login");
    } catch {
      // Not signed out: the server did not confirm the session was revoked.
      setLogoutError("We couldn’t sign you out. Check your connection and try again.");
    } finally {
      setIsLoggingOut(false);
    }
  }

  function toggleAccountMenu(): void {
    setIsAccountMenuOpen((isOpen) => !isOpen);
  }

  return (
    <div className="account-menu" ref={accountMenuRef}>
      <button
        ref={accountTriggerRef}
        id="account-menu-trigger"
        className="account-menu__trigger"
        type="button"
        aria-expanded={isAccountMenuOpen}
        aria-controls="account-menu-panel"
        aria-label={`${isAccountMenuOpen ? "Close" : "Open"} account menu for ${fullName}`}
        onClick={toggleAccountMenu}
      >
        <Avatar
          className="account-menu__avatar"
          name={fullName}
          photoUrl={user?.profilePhotoUrl}
          decorative
        />

        <span className="account-menu__name">{fullName}</span>

        {/* Closed, the arrow points where the panel opens (up); open, it flips. */}
        <span className="account-menu__chevron" aria-hidden="true">
          {isAccountMenuOpen ? "▾" : "▴"}
        </span>

        {/* Shown only for the collapsed desktop sidebar; the button's label is its name. */}
        <span className="sidebar-tooltip" aria-hidden="true">{fullName}</span>
      </button>

      {isAccountMenuOpen && (
        <div
          id="account-menu-panel"
          className="account-menu__panel"
          aria-labelledby="account-menu-trigger"
        >
          <div className="account-menu__identity">
            <strong>{fullName}</strong>
            <span>{user?.email}</span>
          </div>

          <nav className="account-menu__links" aria-label="Account navigation">
            <Link to="/profile" onClick={() => setIsAccountMenuOpen(false)}>Profile</Link>
            <Link to="/settings" onClick={() => setIsAccountMenuOpen(false)}>Account Settings</Link>
          </nav>

          {logoutError && (
            <p className="form-error" role="alert">
              {logoutError}
            </p>
          )}

          <button
            className="button button--secondary account-menu__logout"
            type="button"
            disabled={isLoggingOut}
            onClick={() => void handleLogout()}
          >
            {isLoggingOut ? "Signing out..." : "Logout"}
          </button>
        </div>
      )}
    </div>
  );
}

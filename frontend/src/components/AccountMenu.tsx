import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { getAccountName } from "../utils/accountIdentity";
import { Avatar } from "./Avatar";

/** The signed-in user's account dropdown: identity, Profile, Account Settings, and Logout. */
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

      setIsAccountMenuOpen(false);
      accountTriggerRef.current?.focus();
    }

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
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

        <span className="account-menu__chevron" aria-hidden="true">
          {isAccountMenuOpen ? "▴" : "▾"}
        </span>
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

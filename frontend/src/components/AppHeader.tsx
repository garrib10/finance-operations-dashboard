import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

function AppHeader() {
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const [isAccountMenuOpen, setIsAccountMenuOpen] = useState(false);
  const accountMenuRef = useRef<HTMLDivElement>(null);
  const accountTriggerRef = useRef<HTMLButtonElement>(null);

  const fullName = user
    ? `${user.firstName} ${user.lastName}`.trim()
    : "Account";

  const initials = user
    ? `${user.firstName.charAt(0)}${user.lastName.charAt(0)}`.toUpperCase()
    : "";

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

  function handleLogout(): void {
    setIsAccountMenuOpen(false);
    logout();
    navigate("/login");
  }

  function toggleAccountMenu(): void {
    setIsAccountMenuOpen((isOpen) => !isOpen);
  }

  return (
    <header className="app-header">
      <div className="app-header__content">
        <Link className="app-brand" to="/">
          FinTrack
        </Link>

        {isAuthenticated ? (
          <>
            <nav className="app-nav" aria-label="Primary navigation">
              <Link to="/">Dashboard</Link>

              <Link to="/transactions">Transactions</Link>

              <Link to="/budgets">Budgets</Link>
            </nav>

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
                <span className="account-menu__initials" aria-hidden="true">
                  {initials}
                </span>

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

                  <button
                    className="button button--secondary account-menu__logout"
                    type="button"
                    onClick={handleLogout}
                  >
                    Logout
                  </button>
                </div>
              )}
            </div>
          </>
        ) : (
          <nav className="app-nav" aria-label="Authentication navigation">
            <Link to="/login">Login</Link>

            <Link to="/register">Register</Link>
          </nav>
        )}
      </div>
    </header>
  );
}

export default AppHeader;

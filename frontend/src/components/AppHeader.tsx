import { Link, useNavigate } from "react-router-dom";

import { useAuth } from "../context/AuthContext";

function AppHeader() {
  const { user, isAuthenticated, logout } = useAuth();

  const navigate = useNavigate();

  function handleLogout(): void {
    logout();
    navigate("/login");
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

            <div className="app-user">
              <span className="app-user__name">{user?.firstName}</span>

              <button
                className="button button--secondary"
                type="button"
                onClick={handleLogout}
              >
                Logout
              </button>
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

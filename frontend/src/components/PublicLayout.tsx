import type { ReactNode } from "react";
import { Link, Outlet } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

/**
 * Header and main landmark for pages outside the signed-in shell: Login, Register, and
 * the session loading and recovery screens. The sign-in links stay hidden while a
 * session is being restored, so a signed-in user never sees them flash.
 */
export function PublicShell({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth();

  return (
    <div className="public-layout">
      <header className="public-header">
        <div className="public-header__content">
          <Link className="app-brand" to="/">
            FinTrack
          </Link>

          {!isLoading && !isAuthenticated && (
            <nav className="public-nav" aria-label="Authentication navigation">
              <Link to="/login">Login</Link>

              <Link to="/register">Register</Link>
            </nav>
          )}
        </div>
      </header>

      <main id="main-content" className="app-main" tabIndex={-1}>
        {children}
      </main>
    </div>
  );
}

/** Layout route for the Login and Register pages. */
export function PublicLayout() {
  return (
    <PublicShell>
      <Outlet />
    </PublicShell>
  );
}

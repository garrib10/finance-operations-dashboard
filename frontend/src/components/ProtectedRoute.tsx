import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { PublicShell } from "./PublicLayout";

function ProtectedRoute() {
  const { isAuthenticated, isLoading, restorationError, retrySessionRestore } =
    useAuth();

  const location = useLocation();

  // Restoration screens use the public shell: the signed-in layout only renders once
  // the user is known, so the sidebar never flashes before authentication resolves.
  if (restorationError) {
    return (
      <PublicShell>
        <section
          className="auth-card"
          aria-labelledby="session-restoration-heading"
        >
          <div className="auth-card__header">
            <h1 id="session-restoration-heading">Unable to restore session</h1>

            <p role="alert">{restorationError}</p>
          </div>

          <button
            className="button button--primary"
            type="button"
            disabled={isLoading}
            onClick={() => void retrySessionRestore()}
          >
            {isLoading ? "Retrying..." : "Retry"}
          </button>
        </section>
      </PublicShell>
    );
  }

  if (isLoading) {
    return (
      <PublicShell>
        <p role="status">Loading...</p>
      </PublicShell>
    );
  }

  if (!isAuthenticated) {
    return (
      <Navigate
        to="/login"
        replace
        state={{
          from: location,
        }}
      />
    );
  }

  return <Outlet />;
}

export default ProtectedRoute;

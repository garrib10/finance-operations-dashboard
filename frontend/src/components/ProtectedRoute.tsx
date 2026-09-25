import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

function ProtectedRoute() {
  const { isAuthenticated, isLoading, restorationError, retrySessionRestore } =
    useAuth();

  const location = useLocation();

  if (restorationError) {
    return (
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
    );
  }

  if (isLoading) {
    return <p role="status">Loading...</p>;
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

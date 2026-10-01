import { useCategories } from "../context/CategoryContext";

/**
 * Non-blocking warning when the category list could not be refreshed after a change that
 * did succeed. The saved record is not affected and nothing is resubmitted.
 */
export function CategoryRefreshNotice() {
  const { refreshError, reload } = useCategories();

  if (!refreshError) return null;

  return (
    <div className="form-warning" role="status">
      <span>{refreshError} Your changes were saved.</span>{" "}
      <button
        type="button"
        className="button button--secondary button--small"
        onClick={() => {
          void reload();
        }}
      >
        Retry loading categories
      </button>
    </div>
  );
}

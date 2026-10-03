import { useCategories } from "../context/CategoryContext";
import { InlineNotice } from "./InlineNotice";

/**
 * Non-blocking warning when the category list could not be refreshed after a change that
 * did succeed. The saved record is not affected and nothing is resubmitted.
 */
export function CategoryRefreshNotice() {
  const { refreshError, reload } = useCategories();

  if (!refreshError) return null;

  return (
    <InlineNotice
      variant="warning"
      action={{ label: "Retry loading categories", onClick: () => void reload() }}
    >
      {refreshError} Your changes were saved.
    </InlineNotice>
  );
}

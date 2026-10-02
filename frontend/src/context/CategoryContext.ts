import { createContext, useContext, useEffect } from "react";
import type { CategoryResponse, UpdateCategoryRequest } from "../types/category";

/** idle: not requested yet; loading: first load in progress; error: first load failed. */
export type CategoryLoadStatus = "idle" | "loading" | "ready" | "error";

export interface CategoryContextValue {
  /** The signed-in user's categories (built-in and custom); never another user's. */
  categories: CategoryResponse[];
  status: CategoryLoadStatus;
  /** The first load failed, so there is no usable list. */
  loadError: string;
  /** A later refresh failed; the last loaded list is still shown. */
  refreshError: string;
  /** Starts the first load if nothing has been requested for this user yet. */
  ensureLoaded: () => void;
  /** Loads the latest list; resolves to it, or null if the request failed or became stale. */
  reload: () => Promise<CategoryResponse[] | null>;
  updateCategory: (id: number, request: UpdateCategoryRequest) => Promise<CategoryResponse>;
  deleteCategory: (id: number) => Promise<void>;
}

export const CategoryContext = createContext<CategoryContextValue | undefined>(undefined);

/** Shared categories for the signed-in user, loaded on first use. */
export function useCategories(): CategoryContextValue {
  const context = useContext(CategoryContext);

  if (!context) {
    throw new Error("useCategories must be used within a CategoryProvider");
  }

  const { status, ensureLoaded } = context;

  useEffect(() => {
    if (status === "idle") ensureLoaded();
  }, [status, ensureLoaded]);

  return context;
}

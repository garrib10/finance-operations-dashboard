import { useCallback, useLayoutEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import { useAuth } from "./AuthContext";
import { CategoryContext, type CategoryContextValue, type CategoryLoadStatus } from "./CategoryContext";
import * as categoryService from "../services/categoryService";
import type { CategoryResponse, UpdateCategoryRequest } from "../types/category";

const LOAD_ERROR = "Categories could not be loaded.";
const REFRESH_ERROR = "Category options could not be refreshed.";

interface CategoryState {
  /** The user these categories belong to; anything else is never rendered. */
  owner: number | null;
  categories: CategoryResponse[];
  status: CategoryLoadStatus;
  loadError: string;
  refreshError: string;
}

function emptyState(owner: number | null): CategoryState {
  return { owner, categories: [], status: "idle", loadError: "", refreshError: "" };
}

/**
 * Category state shared by the transaction, budget, and management views. Lists are
 * held in memory only, tied to the signed-in user ID, and cleared when the user changes
 * or signs out; responses from an earlier user or an older request are ignored.
 */
export function CategoryProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const ownerId = user?.id ?? null;

  const [state, setState] = useState<CategoryState>(() => emptyState(ownerId));
  const ownerRef = useRef(ownerId);
  const epochRef = useRef(0);
  const latestRequestRef = useRef(0);
  const loadingRef = useRef(false);

  // Account change or sign-out: forget pending requests. A layout effect runs before
  // every page's passive effects, so their next ensureLoaded starts the new user's load.
  // Stored state needs no reset: only state owned by the current user is ever rendered.
  useLayoutEffect(() => {
    if (ownerRef.current === ownerId) return;
    ownerRef.current = ownerId;
    epochRef.current += 1;
    loadingRef.current = false;
  }, [ownerId]);

  const fetchLatest = useCallback((): Promise<CategoryResponse[] | null> => {
    const owner = ownerRef.current;
    if (owner === null) return Promise.resolve(null);

    const epoch = epochRef.current;
    const request = ++latestRequestRef.current;
    const isCurrent = () => epoch === epochRef.current && request === latestRequestRef.current;

    loadingRef.current = true;
    setState((previous) => {
      if (previous.owner !== owner) return { ...emptyState(owner), status: "loading" };
      return previous.status === "ready" ? previous : { ...previous, status: "loading", loadError: "" };
    });

    return categoryService.getCategories().then(
      (categories) => {
        if (!isCurrent()) return null;
        loadingRef.current = false;
        setState({ owner, categories, status: "ready", loadError: "", refreshError: "" });
        return categories;
      },
      () => {
        if (!isCurrent()) return null;
        loadingRef.current = false;
        setState((previous) => previous.owner === owner && previous.status === "ready"
          ? { ...previous, refreshError: REFRESH_ERROR }
          : { ...emptyState(owner), status: "error", loadError: LOAD_ERROR });
        return null;
      },
    );
  }, []);

  const ensureLoaded = useCallback(() => {
    if (!loadingRef.current) void fetchLatest();
  }, [fetchLatest]);

  const updateCategory = useCallback(async (id: number, request: UpdateCategoryRequest) => {
    const updated = await categoryService.updateCategory(id, request);
    await fetchLatest();
    return updated;
  }, [fetchLatest]);

  const deleteCategory = useCallback(async (id: number) => {
    const owner = ownerRef.current;
    await categoryService.deleteCategory(id);
    // The delete succeeded: remove it now so a failed refresh never shows it again.
    setState((previous) => previous.owner === owner
      ? { ...previous, categories: previous.categories.filter((category) => category.id !== id) }
      : previous);
    await fetchLatest();
  }, [fetchLatest]);

  const visible = state.owner === ownerId ? state : emptyState(ownerId);

  const value = useMemo<CategoryContextValue>(() => ({
    categories: visible.categories,
    status: visible.status,
    loadError: visible.loadError,
    refreshError: visible.refreshError,
    ensureLoaded,
    reload: fetchLatest,
    updateCategory,
    deleteCategory,
  }), [visible.categories, visible.status, visible.loadError, visible.refreshError,
    ensureLoaded, fetchLatest, updateCategory, deleteCategory]);

  return <CategoryContext.Provider value={value}>{children}</CategoryContext.Provider>;
}

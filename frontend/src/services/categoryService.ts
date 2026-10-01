import { apiRequest } from "./api";
import type {
  CategoryResponse,
  CreateCategoryRequest,
  UpdateCategoryRequest,
} from "../types/category";

/** Stable category error codes from the API (see docs/categories-api.md). */
export const CATEGORY_DUPLICATE = "CATEGORY_DUPLICATE";
export const CATEGORY_NOT_FOUND = "CATEGORY_NOT_FOUND";
export const CATEGORY_BUILT_IN = "CATEGORY_BUILT_IN";
export const CATEGORY_IN_USE = "CATEGORY_IN_USE";

export function getCategories(): Promise<CategoryResponse[]> {
  return apiRequest<CategoryResponse[]>("/api/categories");
}

export function createCategory(
  request: CreateCategoryRequest,
): Promise<CategoryResponse> {
  return apiRequest<CategoryResponse>("/api/categories", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function updateCategory(
  id: number,
  request: UpdateCategoryRequest,
): Promise<CategoryResponse> {
  return apiRequest<CategoryResponse>(`/api/categories/${id}`, {
    method: "PUT",
    body: JSON.stringify(request),
  });
}

export function deleteCategory(id: number): Promise<void> {
  return apiRequest<void>(`/api/categories/${id}`, {
    method: "DELETE",
  });
}

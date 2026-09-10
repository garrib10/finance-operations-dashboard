import { apiRequest } from "./api";
import type { CategoryResponse } from "../types/category";

export function getCategories(): Promise<CategoryResponse[]> {
  return apiRequest<CategoryResponse[]>("/api/categories");
}

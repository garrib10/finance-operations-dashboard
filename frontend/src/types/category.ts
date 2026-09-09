export interface CategoryResponse {
  id: number;
  name: string;
  budgetEnabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCategoryRequest {
  name: string;
  budgetEnabled: boolean;
}

export interface UpdateCategoryRequest {
  name: string;
  budgetEnabled: boolean;
}

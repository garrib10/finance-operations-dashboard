/**
 * Semantic icon key from the backend's approved catalog (for example "house" or "tag").
 * Typed as a string because the catalog can grow; an unknown stored key is returned as "tag".
 */
export type CategoryIconKey = string;

export interface CategoryResponse {
  id: number;
  name: string;
  budgetEnabled: boolean;
  /** Seeded default category; the API rejects changes and deletion. */
  builtIn: boolean;
  iconKey: CategoryIconKey;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCategoryRequest {
  name: string;
  budgetEnabled: boolean;
  /** Omitted or blank means "tag". */
  iconKey?: CategoryIconKey;
}

export interface UpdateCategoryRequest {
  name: string;
  budgetEnabled: boolean;
  /** Omitted or blank keeps the current icon. */
  iconKey?: CategoryIconKey;
}

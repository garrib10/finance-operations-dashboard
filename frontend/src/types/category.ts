import type { BudgetStatus } from "./budget";

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

/**
 * A custom category created together with a transaction or budget. The server sets the
 * owner, built-in status, and budgetEnabled (always true); an omitted icon means "tag".
 */
export interface NewCategoryRequest {
  name: string;
  iconKey?: CategoryIconKey;
}

/** Exactly one of an existing category or a new one; the API rejects both or neither. */
export type CategorySelection =
  | { categoryId: number; newCategory?: never }
  | { categoryId?: never; newCategory: NewCategoryRequest };

/** The category's budget for the server's reporting month (same metrics as budget analytics). */
export interface CurrentMonthBudget {
  budgetId: number;
  monthlyLimit: number;
  amountSpent: number;
  amountRemaining: number;
  percentageUsed: number;
  status: BudgetStatus;
}

/** One category's usage from GET /api/categories/summary. Spending counts expenses only. */
export interface CategorySummary {
  id: number;
  name: string;
  iconKey: CategoryIconKey;
  builtIn: boolean;
  budgetEnabled: boolean;
  /** Income and expense transactions, any month. Decides deletion with budgetCount. */
  transactionCount: number;
  /** Income and expense transactions in the response's reporting month only. */
  currentMonthTransactionCount: number;
  /** Budgets in any month or year. */
  budgetCount: number;
  /** ISO date, or null when the category has never been used. */
  lastTransactionDate: string | null;
  currentMonthSpent: number;
  allTimeSpent: number;
  currentMonthBudget: CurrentMonthBudget | null;
  /** Custom and unused right now; the API still re-checks on delete. */
  canDelete: boolean;
}

export interface CategorySummaryList {
  /** The server's reporting month (1–12) and year. */
  month: number;
  year: number;
  /** Ordered by name, then ID. */
  categories: CategorySummary[];
}

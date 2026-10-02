import type { CategoryIconKey, CategorySelection } from "./category";

export type BudgetStatus = "ON_TRACK" | "CAUTION" | "WARNING" | "OVER_BUDGET";

interface BudgetFields {
  monthlyLimit: number;
  month: number;
  year: number;
}

export type CreateBudgetRequest = BudgetFields & CategorySelection;

export type UpdateBudgetRequest = BudgetFields & CategorySelection;

export interface BudgetResponse {
  id: number;
  categoryId: number;
  categoryName: string;
  categoryIconKey: CategoryIconKey;
  monthlyLimit: number;
  month: number;
  year: number;
  createdAt: string;
  updatedAt: string;
}

export interface BudgetAnalyticsResponse {
  budgetId: number;
  categoryId: number;
  categoryName: string;
  categoryIconKey: CategoryIconKey;
  monthlyLimit: number;
  amountSpent: number;
  amountRemaining: number;
  percentageUsed: number;
  status: BudgetStatus;
  month: number;
  year: number;
}

export type BudgetStatus = "ON_TRACK" | "CAUTION" | "WARNING" | "OVER_BUDGET";

export interface CreateBudgetRequest {
  categoryId: number;
  monthlyLimit: number;
  month: number;
  year: number;
}

export interface UpdateBudgetRequest {
  categoryId: number;
  monthlyLimit: number;
  month: number;
  year: number;
}

export interface BudgetResponse {
  id: number;
  categoryId: number;
  categoryName: string;
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
  monthlyLimit: number;
  amountSpent: number;
  amountRemaining: number;
  percentageUsed: number;
  status: BudgetStatus;
  month: number;
  year: number;
}

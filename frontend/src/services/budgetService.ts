import { apiRequest } from "./api";
import type {
  BudgetAnalyticsResponse,
  BudgetResponse,
  CreateBudgetRequest,
  UpdateBudgetRequest,
} from "../types/budget";

export function getBudgets(): Promise<BudgetResponse[]> {
  return apiRequest<BudgetResponse[]>("/api/budgets");
}

export function getBudget(id: number): Promise<BudgetResponse> {
  return apiRequest<BudgetResponse>(`/api/budgets/${id}`);
}

export function getBudgetAnalytics(
  id: number,
): Promise<BudgetAnalyticsResponse> {
  return apiRequest<BudgetAnalyticsResponse>(`/api/budgets/${id}/analytics`);
}

export function createBudget(
  request: CreateBudgetRequest,
): Promise<BudgetResponse> {
  return apiRequest<BudgetResponse>("/api/budgets", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function updateBudget(
  id: number,
  request: UpdateBudgetRequest,
): Promise<BudgetResponse> {
  return apiRequest<BudgetResponse>(`/api/budgets/${id}`, {
    method: "PUT",
    body: JSON.stringify(request),
  });
}

export function deleteBudget(id: number): Promise<void> {
  return apiRequest<void>(`/api/budgets/${id}`, {
    method: "DELETE",
  });
}

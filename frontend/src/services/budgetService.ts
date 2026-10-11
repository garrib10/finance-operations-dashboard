import { apiRequest } from "./api";
import type {
  BudgetAnalyticsResponse,
  BudgetMonthAnalyticsResponse,
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

/**
 * Every budget for one month with its analytics, in one request (one fixed set of server
 * queries however many budgets there are). Both values are always sent.
 */
export function getMonthBudgetAnalytics(
  period: { month: number; year: number },
  signal?: AbortSignal,
): Promise<BudgetMonthAnalyticsResponse> {
  const query = new URLSearchParams({ month: String(period.month), year: String(period.year) });
  const endpoint = `/api/budgets/analytics?${query}`;
  return signal
    ? apiRequest<BudgetMonthAnalyticsResponse>(endpoint, { signal })
    : apiRequest<BudgetMonthAnalyticsResponse>(endpoint);
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

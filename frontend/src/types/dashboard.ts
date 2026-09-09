import type { BudgetStatus } from "./budget";
import type { TransactionType } from "./transaction";

export interface BudgetSummaryResponse {
  budgetId: number;
  categoryId: number;
  categoryName: string;
  monthlyLimit: number;
  amountSpent: number;
  amountRemaining: number;
  percentageUsed: number;
  status: BudgetStatus;
}

export interface CategorySpendingResponse {
  categoryId: number;
  categoryName: string;
  amountSpent: number;
}

export interface RecentTransactionResponse {
  id: number;
  categoryId: number;
  categoryName: string;
  type: TransactionType;
  amount: number;
  description: string;
  transactionDate: string;
}

export interface DashboardResponse {
  currentBalance: number;
  totalIncome: number;
  totalExpenses: number;
  monthlyIncome: number;
  monthlyExpenses: number;
  recentTransactions: RecentTransactionResponse[];
  budgetSummaries: BudgetSummaryResponse[];
  categorySpending: CategorySpendingResponse[];
}

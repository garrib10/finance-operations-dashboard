import type { CategoryIconKey } from "./category";
import type { BudgetStatus } from "./budget";
import type { TransactionType } from "./transaction";

export interface BudgetSummaryResponse {
  budgetId: number;
  categoryId: number;
  categoryName: string;
  categoryIconKey: CategoryIconKey;
  monthlyLimit: number;
  amountSpent: number;
  amountRemaining: number;
  percentageUsed: number;
  status: BudgetStatus;
}

export interface CategorySpendingResponse {
  categoryId: number;
  categoryName: string;
  categoryIconKey: CategoryIconKey;
  amountSpent: number;
}

export interface RecentTransactionResponse {
  id: number;
  categoryId: number;
  categoryName: string;
  categoryIconKey: CategoryIconKey;
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

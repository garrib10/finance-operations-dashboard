export type TransactionType = "INCOME" | "EXPENSE";

export type TransactionSortField = "amount" | "transactionDate" | "createdAt";

export type SortDirection = "asc" | "desc";

export interface CreateTransactionRequest {
  categoryId: number;
  type: TransactionType;
  amount: number;
  description: string;
  transactionDate: string;
}

export interface UpdateTransactionRequest {
  categoryId: number;
  type: TransactionType;
  amount: number;
  description: string;
  transactionDate: string;
}

export interface TransactionResponse {
  id: number;
  categoryId: number;
  categoryName: string;
  type: TransactionType;
  amount: number;
  description: string;
  transactionDate: string;
  createdAt: string;
  updatedAt: string;
}

export interface TransactionFilterRequest {
  type?: TransactionType;
  search?: string;
  startDate?: string;
  endDate?: string;
  minAmount?: number;
  maxAmount?: number;
  sortBy?: TransactionSortField;
  sortDirection?: SortDirection;
  page?: number;
  size?: number;
}

export interface PagedTransactionResponse {
  transactions: TransactionResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

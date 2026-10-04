import { TrendingDown, TrendingUp } from "lucide-react";
import type { TransactionType } from "../types/transaction";

/**
 * Income trends up (green) and expenses trend down (red). Decorative: it always sits
 * beside the visible type name or the labelled Type control.
 */
export function TransactionTypeIcon({ type }: { type: TransactionType }) {
  const props = {
    className: `transaction-type-icon transaction-type-icon--${type.toLowerCase()}`,
    "aria-hidden": true,
    focusable: false,
    size: 18,
  } as const;
  return type === "INCOME" ? <TrendingUp {...props} /> : <TrendingDown {...props} />;
}

/** Icon plus the visible type name, for tables. */
export function TransactionTypeLabel({ type }: { type: TransactionType }) {
  return (
    <span className="transaction-type-label">
      <TransactionTypeIcon type={type} />
      <span>{type === "INCOME" ? "Income" : "Expense"}</span>
    </span>
  );
}

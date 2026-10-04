function plural(count: number, word: string): string {
  return `${count} ${word}${count === 1 ? "" : "s"}`;
}

/**
 * Why a custom category cannot be deleted, from its usage counts (never from server
 * text), for example "Used by 12 transactions and 1 budget."
 */
export function deleteBlockedReason(transactionCount: number, budgetCount: number): string {
  const parts = [
    transactionCount > 0 ? plural(transactionCount, "transaction") : null,
    budgetCount > 0 ? plural(budgetCount, "budget") : null,
  ].filter(Boolean);

  return parts.length > 0 ? `Used by ${parts.join(" and ")}.` : "Used by transactions or budgets.";
}

/**
 * This month's activity for a card, for example "1 transaction in October". The count is
 * the server's reporting month only; the all-time counts stay with the delete rules.
 */
export function monthActivity(currentMonthTransactionCount: number, monthName: string): string {
  const noun = currentMonthTransactionCount === 1 ? "transaction" : "transactions";
  return `${currentMonthTransactionCount} ${noun} in ${monthName}`;
}

/** Stable element IDs on a category card, so focus can move after the list re-renders. */
export function categoryCardIds(id: number) {
  return {
    heading: `category-${id}-heading`,
    actions: `category-${id}-actions`,
    actionsPanel: `category-${id}-actions-panel`,
    edit: `category-${id}-edit`,
    delete: `category-${id}-delete`,
    deleteReason: `category-${id}-delete-reason`,
  };
}

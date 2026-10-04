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

/** Stable element IDs on a category card, so focus can move after the list re-renders. */
export function categoryCardIds(id: number) {
  return {
    heading: `category-${id}-heading`,
    edit: `category-${id}-edit`,
    delete: `category-${id}-delete`,
    deleteReason: `category-${id}-delete-reason`,
  };
}

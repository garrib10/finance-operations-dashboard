import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TransactionTypeIcon, TransactionTypeLabel } from "./TransactionTypeIcon";

describe("TransactionTypeLabel", () => {
  it.each([["INCOME", "Income"], ["EXPENSE", "Expense"]] as const)("shows %s with its own decorative icon", (type, name) => {
    const { container } = render(<TransactionTypeLabel type={type} />);
    const icon = container.querySelector("svg");

    expect(screen.getByText(name)).toBeInTheDocument();
    expect(icon).toHaveClass("transaction-type-icon", `transaction-type-icon--${type.toLowerCase()}`);
    expect(icon).toHaveAttribute("aria-hidden", "true");
  });

  it("colours the standalone icon by type", () => {
    const { container } = render(<TransactionTypeIcon type="INCOME" />);

    expect(container.querySelector("svg")).toHaveClass("transaction-type-icon--income");
  });
});

import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { groceriesRow, petCareRow, salaryRow, summaryRow, unusedRow } from "../test/categorySummaryFixtures";
import { CategorySpendingTable } from "./CategorySpendingTable";

function renderTable(categories = [groceriesRow, salaryRow, petCareRow, unusedRow], monthLabel = "October 2026") {
  return render(<CategorySpendingTable categories={categories} monthLabel={monthLabel} />);
}

const bodyRows = () => within(screen.getByRole("table")).getAllByRole("row").slice(1);

describe("CategorySpendingTable", () => {
  it("is a table named by its heading for the server's month, with column and row headers", () => {
    renderTable(undefined, "September 2026");

    const table = screen.getByRole("table", { name: "Spending in September 2026" });
    expect(screen.getByRole("heading", { level: 2, name: "Spending in September 2026" })).toBeInTheDocument();
    expect(within(table).getAllByRole("columnheader").map((cell) => cell.textContent))
      .toEqual(["Category", "Spent", "Share"]);
    expect(within(table).getByRole("rowheader", { name: "Groceries" })).toBeInTheDocument();
  });

  it("lists only categories with spending, largest first, with formatted amounts and shares", () => {
    renderTable();

    expect(bodyRows().map((row) => within(row).getAllByRole("cell").map((cell) => cell.textContent)))
      .toEqual([["$300.00", "75.0%"], ["$100.00", "25.0%"]]);
    expect(bodyRows().map((row) => within(row).getByRole("rowheader").textContent)).toEqual(["Groceries", "Pet Care"]);
    expect(screen.queryByRole("rowheader", { name: "Hobbies" })).not.toBeInTheDocument();
    expect(screen.getByText(/Categories with no spending this month are not listed\./)).toBeInTheDocument();
    expect(screen.getByText(/Share of \$400\.00 in expenses/)).toBeInTheDocument();
  });

  it("names an earlier month instead of this month", () => {
    render(<CategorySpendingTable categories={[groceriesRow]} monthLabel="May 2025" historical />);

    expect(screen.getByText(/Categories with no spending in May 2025 are not listed\./)).toBeInTheDocument();
    expect(screen.queryByText(/this month/)).not.toBeInTheDocument();
  });

  it("orders ties by name, then ID", () => {
    renderTable([
      summaryRow({ id: 4, name: "Books", currentMonthSpent: 10 }),
      summaryRow({ id: 2, name: "apps", currentMonthSpent: 10 }),
      summaryRow({ id: 3, name: "Apps", currentMonthSpent: 10 }),
    ]);

    expect(bodyRows().map((row) => within(row).getByRole("rowheader").textContent)).toEqual(["apps", "Apps", "Books"]);
  });

  it("draws decorative bars proportional to each share", () => {
    const { container } = renderTable();
    const bars = container.querySelectorAll<HTMLElement>(".category-spending__bar");

    expect(bars).toHaveLength(2);
    bars.forEach((bar) => expect(bar).toHaveAttribute("aria-hidden", "true"));
    expect((bars[0].firstChild as HTMLElement).style.width).toBe("75%");
    expect((bars[1].firstChild as HTMLElement).style.width).toBe("25%");
    // Icons repeat the visible name, so they are decorative too.
    expect(container.querySelector(".category-spending__name svg")).toHaveAttribute("aria-hidden", "true");
  });

  it("explains rounding only when the shown shares do not add up to 100%", () => {
    renderTable([1, 2, 3].map((id) => summaryRow({ id, name: `Cat ${id}`, currentMonthSpent: 10 })));

    expect(screen.getAllByText("33.3%")).toHaveLength(3);
    expect(screen.getByText("Percentages are rounded, so they may not add up to exactly 100%.")).toBeInTheDocument();
  });

  it("has no rounding note when the shares already add up", () => {
    renderTable();

    expect(screen.queryByText(/Percentages are rounded/)).not.toBeInTheDocument();
  });

  it("shows a tiny share as under 0.1% rather than 0%", () => {
    renderTable([summaryRow({ id: 1, name: "Big", currentMonthSpent: 100000 }), summaryRow({ id: 2, name: "Tiny", currentMonthSpent: 0.01 })]);

    expect(within(bodyRows()[1]).getByText("<0.1%")).toBeInTheDocument();
  });

  it("explains a month with no spending instead of an empty table", () => {
    renderTable([salaryRow, unusedRow]);

    expect(screen.getByText("No spending recorded for October 2026.")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });
});

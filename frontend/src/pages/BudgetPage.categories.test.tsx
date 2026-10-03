vi.mock("../context/AuthContext", () => ({ useAuth: vi.fn() }));
vi.mock("../services/budgetService");
vi.mock("../services/categoryService", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../services/categoryService")>()),
  getCategories: vi.fn(),
  deleteCategory: vi.fn(),
}));
vi.mock("recharts", () => ({
  ResponsiveContainer: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  BarChart: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  CartesianGrid: () => null,
  XAxis: () => null,
  YAxis: () => null,
  Tooltip: () => null,
  Bar: () => null,
}));

import type { ReactNode } from "react";
import { render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter, useLocation, useNavigate } from "react-router-dom";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../context/AuthContext";
import { CategoryProvider } from "../context/CategoryProvider";
import { ApiError } from "../services/api";
import * as budgetService from "../services/budgetService";
import * as categoryService from "../services/categoryService";
import { accountContext, deferred } from "../test/accountFixtures";
import { category, legacy, petCare, sampleCategories } from "../test/categoryFixtures";
import type { BudgetAnalyticsResponse, BudgetResponse } from "../types/budget";
import { CREATE_CATEGORY_VALUE } from "../utils/categoryForm";
import BudgetPage from "./BudgetPage";

const today = new Date();
const month = today.getMonth() + 1;
const year = today.getFullYear();
const gym = category({ id: 77, name: "Gym", iconKey: "dumbbell" });

function budget(overrides: Partial<BudgetResponse> = {}): BudgetResponse {
  return {
    id: 1, categoryId: 1, categoryName: "Groceries", categoryIconKey: "shopping-cart", monthlyLimit: 500,
    month, year, createdAt: "2026-09-01T10:00:00", updatedAt: "2026-09-01T10:00:00", ...overrides,
  };
}

function analyticsFor(item: BudgetResponse, percentageUsed = 40): BudgetAnalyticsResponse {
  return {
    budgetId: item.id, categoryId: item.categoryId, categoryName: item.categoryName,
    categoryIconKey: item.categoryIconKey, monthlyLimit: item.monthlyLimit, amountSpent: item.monthlyLimit * percentageUsed / 100,
    amountRemaining: item.monthlyLimit * (1 - percentageUsed / 100), percentageUsed, status: "ON_TRACK", month: item.month,
    year: item.year,
  };
}

function useBudgets(budgets: BudgetResponse[]) {
  vi.mocked(budgetService.getBudgets).mockResolvedValue(budgets);
  vi.mocked(budgetService.getBudgetAnalytics).mockImplementation(async (id) =>
    analyticsFor(budgets.find((item) => item.id === id)!));
}

async function renderPage() {
  const user = userEvent.setup();
  render(<MemoryRouter><CategoryProvider><BudgetPage /></CategoryProvider></MemoryRouter>);
  await screen.findByRole("heading", { name: "Create Budget" });
  await waitFor(() => expect(within(screen.getByLabelText("Category")).getByRole("option", { name: "Pet Care" }))
    .toBeInTheDocument());
  return user;
}

const submit = (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getByRole("button", { name: /Create Budget|Update Budget/ }));

// Full user-event flows are slow under coverage instrumentation.
vi.setConfig({ testTimeout: 20_000 });

describe("BudgetPage categories", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useAuth).mockReturnValue(accountContext());
    Object.defineProperty(HTMLElement.prototype, "scrollIntoView", { configurable: true, value: vi.fn() });
    vi.mocked(categoryService.getCategories).mockResolvedValue(sampleCategories);
    useBudgets([budget()]);
    vi.mocked(budgetService.createBudget).mockResolvedValue(budget());
    vi.mocked(budgetService.updateBudget).mockResolvedValue(budget());
  });

  it("sends only categoryId for an existing category", async () => {
    const user = await renderPage();
    await user.selectOptions(screen.getByLabelText("Category"), String(petCare.id));
    await user.type(screen.getByLabelText("Monthly Limit"), "60");
    await submit(user);

    await waitFor(() => expect(budgetService.createBudget).toHaveBeenCalledWith(
      { categoryId: 40, monthlyLimit: 60, month, year }));
  });

  it("creates a new category with the budget and makes it reusable", async () => {
    const user = await renderPage();
    await user.selectOptions(screen.getByLabelText("Category"), CREATE_CATEGORY_VALUE);
    await user.type(screen.getByLabelText("Category name"), "Gym");
    await user.click(screen.getByRole("radio", { name: "Dumbbell" }));
    await user.type(screen.getByLabelText("Monthly Limit"), "45");
    vi.mocked(categoryService.getCategories).mockResolvedValue([...sampleCategories, gym]);
    useBudgets([budget(), budget({ id: 2, categoryId: gym.id, categoryName: "Gym", categoryIconKey: "dumbbell" })]);
    await submit(user);

    await waitFor(() => expect(budgetService.createBudget).toHaveBeenCalledWith(
      { newCategory: { name: "Gym", iconKey: "dumbbell" }, monthlyLimit: 45, month, year }));
    expect(vi.mocked(budgetService.createBudget).mock.calls[0][0]).not.toHaveProperty("categoryId");
    await waitFor(() => expect(within(screen.getByLabelText("Category")).getByRole("option", { name: "Gym" }))
      .toBeInTheDocument());
    expect(within(screen.getByLabelText("Filter by category")).getByRole("option", { name: "Gym" })).toBeInTheDocument();
    expect(within(screen.getByTestId("budget-card-2")).getByRole("heading", { name: "Gym" })
      .querySelector("svg")).toHaveClass("lucide-dumbbell");
  });

  it("creates a new category while editing and keeps the budget ID", async () => {
    const user = await renderPage();
    await user.click(screen.getByRole("button", { name: "Edit" }));
    expect(screen.getByLabelText("Category")).toHaveValue("1");

    await user.selectOptions(screen.getByLabelText("Category"), CREATE_CATEGORY_VALUE);
    await user.type(screen.getByLabelText("Category name"), "Date Night");
    await submit(user);

    await waitFor(() => expect(budgetService.updateBudget).toHaveBeenCalledWith(1,
      { newCategory: { name: "Date Night", iconKey: "tag" }, monthlyLimit: 500, month, year }));
  });

  it.each([
    ["monthlyLimit", () => screen.getByLabelText("Monthly Limit"), "Monthly limit can have at most 10 whole digits and 2 decimal places"],
    ["newCategory.name", () => screen.getByLabelText("Category name"), "Category name contains unsupported characters"],
    ["newCategory.iconKey", () => screen.getByRole("radiogroup", { name: "Icon" }), "Icon must be one of the approved category icons"],
  ])("shows server %s beside its control, focused and invalid until edited", async (field, control, message) => {
    vi.mocked(budgetService.createBudget).mockRejectedValue(new ApiError("Validation failed.", 400, { [field]: message }));
    const user = await renderPage();
    await user.selectOptions(screen.getByLabelText("Category"), CREATE_CATEGORY_VALUE);
    await user.type(screen.getByLabelText("Category name"), "Pets");
    await user.type(screen.getByLabelText("Monthly Limit"), "99");
    await submit(user);

    const element = await waitFor(() => {
      const found = control();
      expect(found).toHaveAttribute("aria-invalid", "true");
      return found;
    });
    expect(element).toHaveAccessibleDescription(expect.stringContaining(message));
    expect(screen.getByRole("alert")).toHaveTextContent("Please check the highlighted fields.");
    expect(element.getAttribute("role") === "radiogroup" ? within(element).getByRole("radio", { name: "Tag" }) : element)
      .toHaveFocus();
    expect(screen.getByLabelText("Monthly Limit")).toHaveValue(99);

    if (field === "monthlyLimit") await user.type(element, "1");
    else if (field === "newCategory.name") await user.type(element, "s");
    else await user.click(within(element).getByRole("radio", { name: "Gift" }));
    expect(screen.queryByText(message)).not.toBeInTheDocument();
  });

  it("keeps the form after a duplicate category and offers the existing one", async () => {
    vi.mocked(budgetService.createBudget).mockRejectedValue(
      new ApiError("Category already exists", 409, undefined, "CATEGORY_DUPLICATE"));
    const user = await renderPage();
    await user.selectOptions(screen.getByLabelText("Category"), CREATE_CATEGORY_VALUE);
    await user.type(screen.getByLabelText("Category name"), "pet care");
    await user.type(screen.getByLabelText("Monthly Limit"), "60");
    await submit(user);

    expect(await screen.findByRole("alert")).toHaveTextContent("That category already exists. Nothing was saved.");
    expect(screen.getByLabelText("Monthly Limit")).toHaveValue(60);
    await user.click(await screen.findByRole("button", { name: "Use existing category “Pet Care”" }));
    expect(screen.getByLabelText("Category")).toHaveValue("40");
    expect(budgetService.createBudget).toHaveBeenCalledTimes(1);
  });

  it("keeps the duplicate-budget message for an existing period", async () => {
    vi.mocked(budgetService.createBudget).mockRejectedValue(
      new ApiError("Budget already exists for this category and month", 409));
    const user = await renderPage();
    await user.selectOptions(screen.getByLabelText("Category"), "1");
    await user.type(screen.getByLabelText("Monthly Limit"), "60");
    await submit(user);

    expect(await screen.findByRole("alert")).toHaveTextContent("Budget already exists for this category and month");
  });

  it("keeps the saved budget and warns when the category refresh fails", async () => {
    const user = await renderPage();
    await user.selectOptions(screen.getByLabelText("Category"), CREATE_CATEGORY_VALUE);
    await user.type(screen.getByLabelText("Category name"), "Gym");
    await user.type(screen.getByLabelText("Monthly Limit"), "45");
    vi.mocked(categoryService.getCategories).mockRejectedValueOnce(new Error("network"));
    await submit(user);

    expect(await screen.findByText(/Category options could not be refreshed\. Your changes were saved\./))
      .toBeInTheDocument();
    expect(budgetService.createBudget).toHaveBeenCalledTimes(1);
  });

  it("ignores a second submit while the first is in flight", async () => {
    const pending = deferred<BudgetResponse>();
    vi.mocked(budgetService.createBudget).mockReturnValue(pending.promise);
    const user = await renderPage();
    await user.selectOptions(screen.getByLabelText("Category"), "1");
    await user.type(screen.getByLabelText("Monthly Limit"), "45");
    const form = screen.getByRole("button", { name: "Create Budget" }).closest("form")!;

    form.requestSubmit();
    form.requestSubmit();
    pending.resolve(budget());

    await waitFor(() => expect(screen.getByRole("button", { name: "Create Budget" })).toBeEnabled());
    expect(budgetService.createBudget).toHaveBeenCalledTimes(1);
  });

  it("filters the period by category on the client and distinguishes the empty states", async () => {
    useBudgets([
      budget(),
      budget({ id: 2, categoryId: petCare.id, categoryName: "Pet Care", categoryIconKey: "paw-print" }),
      budget({ id: 3, categoryId: legacy.id, categoryName: "Legacy", categoryIconKey: "retired-icon" }),
    ]);
    const user = await renderPage();
    await screen.findByTestId("budget-card-3");
    expect(within(screen.getByTestId("budget-card-3")).getByRole("heading", { name: "Legacy" })
      .querySelector("svg")).toHaveClass("lucide-tag");

    const filterIcon = () => screen.getByLabelText("Filter by category").parentElement?.querySelector("svg");
    expect(filterIcon()).toHaveClass("lucide-tags");
    await user.selectOptions(screen.getByLabelText("Filter by category"), String(petCare.id));
    expect(filterIcon()).toHaveClass("category-icon");
    expect(screen.getByTestId("budget-card-2")).toBeInTheDocument();
    expect(screen.queryByTestId("budget-card-1")).not.toBeInTheDocument();
    expect(screen.getByText("Pet Care", { selector: ".category-label__name" })).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Filter by category"), "2");
    expect(screen.getByText(/No Housing budget for/)).toBeInTheDocument();
    expect(budgetService.getBudgets).toHaveBeenCalledTimes(1);

    const otherMonth = month === 12 ? "1" : String(month + 1);
    await user.selectOptions(screen.getAllByLabelText("Month")[1], otherMonth);
    expect(screen.getByText(/No budgets found for/)).toBeInTheDocument();
  });

  it("reports a category that no longer exists on the category field", async () => {
    vi.mocked(budgetService.createBudget).mockRejectedValue(
      new ApiError("Category not found", 404, undefined, "CATEGORY_NOT_FOUND"));
    const user = await renderPage();
    await user.selectOptions(screen.getByLabelText("Category"), String(petCare.id));
    await user.type(screen.getByLabelText("Monthly Limit"), "60");
    await submit(user);

    await waitFor(() => expect(screen.getByLabelText("Category")).toHaveAccessibleDescription(
      "This category is no longer available. Choose another category."));
    expect(screen.getByLabelText("Category")).toHaveFocus();
    expect(categoryService.getCategories).toHaveBeenCalledTimes(2);
  });

  it("has no category management panel or link; inline creation stays in the form", async () => {
    await renderPage();

    expect(screen.queryByRole("link", { name: "Manage categories" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Manage categories|Hide categories/ })).not.toBeInTheDocument();
    expect(within(screen.getByLabelText("Category")).getByRole("option", { name: "Create a custom category…" }))
      .toBeInTheDocument();
  });

  it("announces a successful save in a status region", async () => {
    const user = await renderPage();
    await user.selectOptions(screen.getByLabelText("Category"), String(petCare.id));
    await user.type(screen.getByLabelText("Monthly Limit"), "60");
    await submit(user);

    expect(await screen.findByText("Budget created.")).toHaveAttribute("role", "status");

    await user.click(screen.getByRole("button", { name: "Dismiss message" }));
    expect(screen.queryByText("Budget created.")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Edit" }));
    await submit(user);
    expect(await screen.findByText("Budget updated.")).toHaveAttribute("role", "status");

    // Starting another edit clears the earlier confirmation.
    await user.click(screen.getByRole("button", { name: "Edit" }));
    expect(screen.queryByText("Budget updated.")).not.toBeInTheDocument();
  });
});

function HistoryProbe() {
  const location = useLocation();
  const navigate = useNavigate();
  return (
    <>
      <output data-testid="url">{location.pathname}{location.search}</output>
      <button type="button" onClick={() => navigate(-1)}>History back</button>
      <button type="button" onClick={() => navigate(1)}>History forward</button>
    </>
  );
}

describe("BudgetPage category deep link", () => {
  const savings = category({ id: 5, name: "Savings", builtIn: true, budgetEnabled: false, iconKey: "piggy-bank" });

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useAuth).mockReturnValue(accountContext());
    Object.defineProperty(HTMLElement.prototype, "scrollIntoView", { configurable: true, value: vi.fn() });
    vi.mocked(categoryService.getCategories).mockResolvedValue([...sampleCategories, savings]);
    // Groceries (ID 1) has a budget this month; Pet Care does not.
    useBudgets([budget()]);
  });

  async function renderAt(entries: string[], index = entries.length - 1) {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={entries} initialIndex={index}>
        <CategoryProvider><BudgetPage /></CategoryProvider>
        <HistoryProbe />
      </MemoryRouter>,
    );
    await screen.findByRole("heading", { name: /Create Budget|Edit Budget/ });
    return user;
  }

  it("opens this month's existing budget for editing", async () => {
    await renderAt(["/budgets?category=1"]);

    expect(await screen.findByRole("heading", { name: "Edit Budget" })).toHaveFocus();
    expect(screen.getByLabelText("Category")).toHaveValue("1");
    expect(screen.getByLabelText("Monthly Limit")).toHaveValue(500);
    expect(screen.getByRole("button", { name: "Update Budget" })).toBeInTheDocument();
    expect(budgetService.createBudget).not.toHaveBeenCalled();
  });

  it("starts a new budget with the category preselected when there is none this month", async () => {
    await renderAt([`/budgets?category=${petCare.id}`]);

    await waitFor(() => expect(screen.getByLabelText("Category")).toHaveValue(String(petCare.id)));
    expect(screen.getByRole("heading", { name: "Create Budget" })).toHaveFocus();
    // The form's month and year (the period filter has its own "Month" field).
    expect(document.getElementById("budget-month")).toHaveValue(String(month));
    expect(document.getElementById("budget-year")).toHaveValue(year);
    expect(budgetService.createBudget).not.toHaveBeenCalled();
  });

  it("does not start a budget for a category that does not take budgets", async () => {
    await renderAt([`/budgets?category=${savings.id}`]);

    await waitFor(() => expect(screen.getByTestId("url")).toHaveTextContent(/^\/budgets$/));
    expect(screen.getByLabelText("Category")).toHaveValue("");
    expect(screen.getByRole("heading", { name: "Create Budget" })).not.toHaveFocus();
  });

  it.each(["abc", "1.5", "0", "-1", "999"])("ignores %s and removes only that parameter", async (value) => {
    await renderAt([`/budgets?tab=list&category=${value}`]);

    await waitFor(() => expect(screen.getByTestId("url")).toHaveTextContent("/budgets?tab=list"));
    expect(screen.getByLabelText("Category")).toHaveValue("");
    expect(screen.getByRole("heading", { name: "Create Budget" })).toBeInTheDocument();
  });

  it("follows browser history between linked categories", async () => {
    const user = await renderAt(["/budgets?category=1", `/budgets?category=${petCare.id}`]);
    await waitFor(() => expect(screen.getByLabelText("Category")).toHaveValue(String(petCare.id)));
    expect(screen.getByRole("heading", { name: "Create Budget" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "History back" }));

    expect(await screen.findByRole("heading", { name: "Edit Budget" })).toHaveFocus();
    expect(screen.getByLabelText("Category")).toHaveValue("1");
  });
});

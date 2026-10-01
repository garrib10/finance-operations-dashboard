vi.mock("../context/AuthContext", () => ({ useAuth: vi.fn() }));
vi.mock("../services/categoryService", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../services/categoryService")>()),
  getCategories: vi.fn(),
  updateCategory: vi.fn(),
  deleteCategory: vi.fn(),
}));
vi.mock("../services/transactionService");

import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../context/AuthContext";
import { CategoryProvider } from "../context/CategoryProvider";
import { ApiError } from "../services/api";
import * as categoryService from "../services/categoryService";
import * as transactionService from "../services/transactionService";
import { accountContext, deferred } from "../test/accountFixtures";
import { category, legacy, longUnicode, petCare, sampleCategories } from "../test/categoryFixtures";
import type { TransactionResponse } from "../types/transaction";
import { CREATE_CATEGORY_VALUE } from "../utils/categoryForm";
import TransactionPage from "./TransactionPage";

function transaction(overrides: Partial<TransactionResponse> = {}): TransactionResponse {
  return {
    id: 1,
    categoryId: 1,
    categoryName: "Groceries",
    categoryIconKey: "shopping-cart",
    type: "EXPENSE",
    amount: 75.5,
    description: "Food Lion",
    transactionDate: "2026-09-10",
    createdAt: "2026-09-10T12:00:00",
    updatedAt: "2026-09-10T12:00:00",
    ...overrides,
  };
}

function page(transactions: TransactionResponse[], pageNumber = 0, totalPages = 1) {
  return { transactions, page: pageNumber, size: 10, totalElements: transactions.length, totalPages };
}

const gym = category({ id: 77, name: "Gym", iconKey: "dumbbell" });

async function renderPage() {
  const user = userEvent.setup();
  render(<CategoryProvider><TransactionPage /></CategoryProvider>);
  await screen.findByText("Food Lion");
  await waitFor(() => expect(within(screen.getByLabelText("Category")).getByRole("option", { name: "Pet Care" }))
    .toBeInTheDocument());
  return user;
}

async function fillFinancialFields(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText("Amount"), "40");
  await user.type(screen.getByLabelText("Description"), "Vet visit");
  await user.type(screen.getByLabelText("Date"), "2026-09-20");
}

async function chooseNewCategory(user: ReturnType<typeof userEvent.setup>, name: string, icon?: string) {
  await user.selectOptions(screen.getByLabelText("Category"), CREATE_CATEGORY_VALUE);
  if (name) await user.type(screen.getByLabelText("Category name"), name);
  if (icon) await user.click(screen.getByRole("radio", { name: icon }));
}

const submit = (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getByRole("button", { name: /Add Transaction|Update Transaction/ }));

// Full user-event flows through the whole page are slow under coverage instrumentation.
vi.setConfig({ testTimeout: 20_000 });

describe("TransactionPage categories", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useAuth).mockReturnValue(accountContext());
    Object.defineProperty(HTMLElement.prototype, "scrollIntoView", { configurable: true, value: vi.fn() });
    vi.mocked(categoryService.getCategories).mockResolvedValue(sampleCategories);
    vi.mocked(transactionService.getTransactions).mockResolvedValue(page([transaction()]));
    vi.mocked(transactionService.createTransaction).mockResolvedValue(transaction());
    vi.mocked(transactionService.updateTransaction).mockResolvedValue(transaction());
  });

  it("sends only categoryId for an existing category", async () => {
    const user = await renderPage();
    await user.selectOptions(screen.getByLabelText("Category"), String(petCare.id));
    await fillFinancialFields(user);
    await submit(user);

    await waitFor(() => expect(transactionService.createTransaction).toHaveBeenCalledTimes(1));
    const request = vi.mocked(transactionService.createTransaction).mock.calls[0][0];
    expect(request).toEqual({ categoryId: 40, type: "EXPENSE", amount: 40, description: "Vet visit",
      transactionDate: "2026-09-20" });
    expect(request).not.toHaveProperty("newCategory");
  });

  it("creates a new category with the transaction and makes it reusable immediately", async () => {
    vi.mocked(transactionService.createTransaction).mockResolvedValue(
      transaction({ id: 9, categoryId: gym.id, categoryName: "Gym", categoryIconKey: "dumbbell", description: "Vet visit" }));
    const user = await renderPage();
    await chooseNewCategory(user, "  Gym ", "Dumbbell");
    await fillFinancialFields(user);
    vi.mocked(categoryService.getCategories).mockResolvedValue([...sampleCategories, gym]);
    vi.mocked(transactionService.getTransactions).mockResolvedValue(
      page([transaction({ id: 9, categoryId: gym.id, categoryName: "Gym", categoryIconKey: "dumbbell" })]));
    await submit(user);

    await waitFor(() => expect(transactionService.createTransaction).toHaveBeenCalledTimes(1));
    const request = vi.mocked(transactionService.createTransaction).mock.calls[0][0];
    expect(request).toEqual({ newCategory: { name: "  Gym ", iconKey: "dumbbell" }, type: "EXPENSE", amount: 40,
      description: "Vet visit", transactionDate: "2026-09-20" });
    expect(request).not.toHaveProperty("categoryId");

    // The new category is selectable in the form and the filter without reloading the page.
    await waitFor(() => expect(within(screen.getByLabelText("Category")).getByRole("option", { name: "Gym" }))
      .toBeInTheDocument());
    expect(within(screen.getByLabelText("Filter by category")).getByRole("option", { name: "Gym" })).toBeInTheDocument();
    expect(screen.queryByRole("group", { name: "New category" })).not.toBeInTheDocument();
    expect(screen.getByLabelText("Category")).toHaveValue("");
    expect(categoryService.getCategories).toHaveBeenCalledTimes(2);
  });

  it("creates a new category while editing and keeps the transaction ID", async () => {
    const user = await renderPage();
    await user.click(screen.getByRole("button", { name: "Edit" }));
    expect(screen.getByLabelText("Category")).toHaveValue("1");

    await chooseNewCategory(user, "Farmers Market");
    await submit(user);

    await waitFor(() => expect(transactionService.updateTransaction).toHaveBeenCalledTimes(1));
    const [id, request] = vi.mocked(transactionService.updateTransaction).mock.calls[0];
    expect(id).toBe(1);
    expect(request).toEqual({ newCategory: { name: "Farmers Market", iconKey: "tag" }, type: "EXPENSE",
      amount: 75.5, description: "Food Lion", transactionDate: "2026-09-10" });
  });

  it("clears the new-category draft and its errors on cancel or when switching back", async () => {
    const user = await renderPage();
    await user.click(screen.getByRole("button", { name: "Edit" }));
    await chooseNewCategory(user, "");
    await submit(user);
    expect(screen.getByLabelText("Category name")).toHaveAttribute("aria-invalid", "true");

    await user.selectOptions(screen.getByLabelText("Category"), "2");
    expect(screen.queryByLabelText("Category name")).not.toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText("Category"), CREATE_CATEGORY_VALUE);
    expect(screen.getByLabelText("Category name")).not.toHaveAttribute("aria-invalid");

    await user.click(screen.getByRole("button", { name: "Cancel" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Edit" })).toHaveFocus());
    expect(screen.getByLabelText("Category")).toHaveValue("");
    expect(screen.queryByLabelText("Category name")).not.toBeInTheDocument();
  });

  it("validates a blank or oversized name on blur and on submit, focusing it", async () => {
    const user = await renderPage();
    await chooseNewCategory(user, "   ");
    await user.tab();
    const name = screen.getByLabelText("Category name");
    expect(name).toHaveAccessibleDescription(expect.stringContaining("Category name is required"));

    await user.clear(name);
    await user.click(name);
    await user.paste("x".repeat(101));
    await fillFinancialFields(user);
    await submit(user);

    expect(name).toHaveAttribute("aria-invalid", "true");
    expect(name).toHaveAccessibleDescription(expect.stringContaining("Category name must be 100 characters or fewer"));
    expect(name).toHaveFocus();
    expect(name).toHaveValue("x".repeat(101));
    expect(transactionService.createTransaction).not.toHaveBeenCalled();
  });

  describe("server field messages beside their inputs", () => {
    it.each([
      ["amount", () => screen.getByLabelText("Amount"), "Amount can have at most 10 whole digits and 2 decimal places"],
      ["newCategory.name", () => screen.getByLabelText("Category name"), "Category name contains unsupported characters"],
      ["newCategory.iconKey", () => screen.getByRole("radiogroup", { name: "Icon" }),
        "Icon must be one of the approved category icons"],
    ])("shows %s beside its control, marks it invalid, focuses it, and clears it on edit", async (field, control, message) => {
      vi.mocked(transactionService.createTransaction).mockRejectedValue(
        new ApiError("Validation failed.", 400, { [field]: message }));
      const user = await renderPage();
      await chooseNewCategory(user, "Pets");
      await fillFinancialFields(user);
      await submit(user);

      const element = await waitFor(() => {
        const found = control();
        expect(found).toHaveAttribute("aria-invalid", "true");
        return found;
      });
      expect(element).toHaveAccessibleDescription(expect.stringContaining(message));
      const errorId = element.getAttribute("aria-describedby")!.split(" ").find((id) => id.endsWith("error"))!;
      expect(document.getElementById(errorId)).toHaveTextContent(message);
      expect(screen.getByRole("alert")).toHaveTextContent("Please check the highlighted fields.");
      expect(screen.getByRole("alert")).not.toHaveTextContent(message);
      expect(element.getAttribute("role") === "radiogroup" ? within(element).getByRole("radio", { name: "Tag" }) : element)
        .toHaveFocus();
      expect(screen.getByLabelText("Description")).toHaveValue("Vet visit");

      if (field === "amount") await user.type(element, "1");
      else if (field === "newCategory.name") await user.type(element, "s");
      else await user.click(within(element).getByRole("radio", { name: "Gift" }));
      expect(element).not.toHaveAttribute("aria-invalid", "true");
      expect(screen.queryByText(message)).not.toBeInTheDocument();
    });

    it("focuses the first invalid control in form order and keeps unknown fields as summary text", async () => {
      vi.mocked(transactionService.createTransaction).mockRejectedValue(new ApiError("Validation failed.", 400, {
        amount: "Amount is too large",
        "newCategory.iconKey": "Icon must be one of the approved category icons",
        debug: "<img src=x onerror=alert(1)>Server note",
      }));
      const user = await renderPage();
      await chooseNewCategory(user, "Pets");
      await fillFinancialFields(user);
      await submit(user);

      const alert = await screen.findByRole("alert");
      expect(alert).toHaveTextContent("<img src=x onerror=alert(1)>Server note");
      expect(alert.querySelector("img")).toBeNull();
      expect(screen.getByRole("radio", { name: "Tag" })).toHaveFocus();
      expect(screen.getByLabelText("Amount")).toHaveAttribute("aria-invalid", "true");
    });
  });

  it("keeps the draft and offers the existing category after a duplicate response", async () => {
    vi.mocked(transactionService.createTransaction).mockRejectedValue(
      new ApiError("Category already exists", 409, undefined, "CATEGORY_DUPLICATE"));
    const user = await renderPage();
    await chooseNewCategory(user, "PET  care");
    await fillFinancialFields(user);
    await submit(user);

    expect(await screen.findByRole("alert")).toHaveTextContent("That category already exists. Nothing was saved.");
    const name = screen.getByLabelText("Category name");
    expect(name).toHaveValue("PET  care");
    expect(name).toHaveAccessibleDescription(expect.stringContaining("already have a category with this name"));
    expect(screen.getByLabelText("Amount")).toHaveValue(40);

    await user.click(await screen.findByRole("button", { name: "Use existing category “Pet Care”" }));
    expect(screen.getByLabelText("Category")).toHaveValue("40");
    expect(transactionService.createTransaction).toHaveBeenCalledTimes(1);
    expect(categoryService.getCategories).toHaveBeenCalledTimes(2);
  });

  it("reports an unavailable category on the category field", async () => {
    vi.mocked(transactionService.createTransaction).mockRejectedValue(
      new ApiError("Category not found", 404, undefined, "CATEGORY_NOT_FOUND"));
    const user = await renderPage();
    await user.selectOptions(screen.getByLabelText("Category"), String(petCare.id));
    await fillFinancialFields(user);
    await submit(user);

    await waitFor(() => expect(screen.getByLabelText("Category")).toHaveAccessibleDescription(
      "This category is no longer available. Choose another category."));
  });

  it("keeps the saved transaction and warns when the category refresh fails afterwards", async () => {
    const user = await renderPage();
    await chooseNewCategory(user, "Gym");
    await fillFinancialFields(user);
    vi.mocked(categoryService.getCategories).mockRejectedValueOnce(new Error("network"));
    await submit(user);

    expect(await screen.findByText(/Category options could not be refreshed\. Your changes were saved\./))
      .toBeInTheDocument();
    expect(screen.queryByText(/Unable to create the transaction/)).not.toBeInTheDocument();
    expect(transactionService.createTransaction).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("heading", { name: "Add Transaction" })).toBeInTheDocument();
  });

  it("ignores a second submit while the first is in flight", async () => {
    const pending = deferred<TransactionResponse>();
    vi.mocked(transactionService.createTransaction).mockReturnValue(pending.promise);
    const user = await renderPage();
    await chooseNewCategory(user, "Gym");
    await fillFinancialFields(user);
    const form = screen.getByRole("button", { name: "Add Transaction" }).closest("form")!;

    form.requestSubmit();
    form.requestSubmit();
    pending.resolve(transaction());

    await waitFor(() => expect(screen.getByRole("button", { name: "Add Transaction" })).toBeEnabled());
    expect(transactionService.createTransaction).toHaveBeenCalledTimes(1);
  });

  it("shows category icons in rows, falling back to tag, and wraps long names", async () => {
    vi.mocked(transactionService.getTransactions).mockResolvedValue(page([
      transaction(),
      transaction({ id: 2, categoryId: legacy.id, categoryName: legacy.name, categoryIconKey: legacy.iconKey,
        description: "Old" }),
      transaction({ id: 3, categoryId: longUnicode.id, categoryName: longUnicode.name, categoryIconKey: "coffee",
        description: "Latte" }),
    ]));
    await renderPage();

    const icon = (rowId: number) => screen.getByTestId(`transaction-row-${rowId}`).querySelector(".category-label svg");
    expect(icon(1)).toHaveClass("lucide-shopping-cart");
    expect(icon(2)).toHaveClass("lucide-tag");
    expect(icon(3)).toHaveAttribute("aria-hidden", "true");
    expect(within(screen.getByTestId("transaction-row-3")).getByText(longUnicode.name))
      .toHaveClass("category-label__name");
  });

  it("filters by category ID combined with other filters, resetting to the first page", async () => {
    vi.mocked(transactionService.getTransactions).mockResolvedValue(page([transaction()], 1, 3));
    const user = await renderPage();
    await user.click(screen.getByRole("button", { name: "Next" }));

    await user.selectOptions(screen.getByLabelText("Filter by category"), String(petCare.id));
    await user.selectOptions(screen.getByLabelText("Type", { selector: "#filter-type" }), "EXPENSE");
    await user.type(screen.getByLabelText("Search"), "vet");
    await user.click(screen.getByRole("button", { name: "Apply Filters" }));

    await waitFor(() => expect(transactionService.getTransactions).toHaveBeenLastCalledWith(expect.objectContaining({
      page: 0, categoryId: 40, type: "EXPENSE", search: "vet",
    })));
    expect(document.querySelector(".transaction-filters .category-select__control svg")).toHaveClass("lucide-paw-print");

    await user.click(screen.getByRole("button", { name: "Reset" }));
    expect(screen.getByLabelText("Filter by category")).toHaveValue("");
    expect(vi.mocked(transactionService.getTransactions).mock.lastCall![0]).not.toHaveProperty("categoryId");
  });

  it("refreshes the list after a rename and drops a deleted category from the form and filter", async () => {
    vi.mocked(categoryService.deleteCategory).mockResolvedValue(undefined);
    const user = await renderPage();
    await user.selectOptions(screen.getByLabelText("Category"), String(petCare.id));
    await user.selectOptions(screen.getByLabelText("Filter by category"), String(petCare.id));
    await user.click(screen.getByRole("button", { name: "Apply Filters" }));
    vi.mocked(categoryService.getCategories).mockResolvedValue(sampleCategories.filter((item) => item !== petCare));

    await user.click(screen.getByRole("button", { name: "Manage categories" }));
    await user.click(screen.getByRole("button", { name: "Delete Pet Care" }));
    await user.click(screen.getByRole("button", { name: "Delete category" }));

    await waitFor(() => expect(screen.getByLabelText("Filter by category")).toHaveValue(""));
    expect(screen.getByLabelText("Category")).toHaveValue("");
    expect(vi.mocked(transactionService.getTransactions).mock.lastCall![0]).not.toHaveProperty("categoryId");
  });

  it("requires a category choice before submitting", async () => {
    const user = await renderPage();
    await fillFinancialFields(user);
    const form = screen.getByRole("button", { name: "Add Transaction" }).closest("form")!;
    form.noValidate = true;
    await submit(user);

    const select = screen.getByLabelText("Category");
    expect(select).toHaveAttribute("aria-invalid", "true");
    expect(select).toHaveAccessibleDescription("Choose an existing category or create a new one");
    expect(select).toHaveFocus();
    expect(transactionService.createTransaction).not.toHaveBeenCalled();
  });

  it("warns when the list cannot refresh after a category change", async () => {
    vi.mocked(categoryService.updateCategory).mockResolvedValue({ ...petCare, name: "Pets" });
    const user = await renderPage();
    vi.mocked(transactionService.getTransactions).mockRejectedValueOnce(new Error("network"));

    await user.click(screen.getByRole("button", { name: "Manage categories" }));
    await user.click(screen.getByRole("button", { name: "Edit Pet Care" }));
    await user.click(screen.getByRole("button", { name: "Save category" }));

    expect(await screen.findByText(/The category was updated, but the transaction list could not be refreshed/))
      .toBeInTheDocument();
  });
});

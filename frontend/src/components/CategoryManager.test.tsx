vi.mock("../context/AuthContext", () => ({ useAuth: vi.fn() }));
vi.mock("../services/categoryService", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../services/categoryService")>()),
  getCategories: vi.fn(),
  updateCategory: vi.fn(),
  deleteCategory: vi.fn(),
}));

import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../context/AuthContext";
import { CategoryProvider } from "../context/CategoryProvider";
import { ApiError } from "../services/api";
import * as categoryService from "../services/categoryService";
import { accountContext } from "../test/accountFixtures";
import { groceries, housing, other, petCare, sampleCategories } from "../test/categoryFixtures";
import { CategoryManager, type CategoryChange } from "./CategoryManager";

const onChange = vi.fn<(change: CategoryChange) => void>();

async function openManager() {
  const user = userEvent.setup();
  render(<CategoryProvider><CategoryManager onChange={onChange} /></CategoryProvider>);
  await user.click(screen.getByRole("button", { name: "Manage categories" }));
  await screen.findByText("Pet Care");
  return user;
}

function row(name: string): HTMLElement {
  return screen.getByText(name).closest("li")!;
}

// Full user-event flows are slow under coverage instrumentation.
vi.setConfig({ testTimeout: 20_000 });

describe("CategoryManager", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useAuth).mockReturnValue(accountContext());
    vi.mocked(categoryService.getCategories).mockResolvedValue(sampleCategories);
  });

  it("expands from an accessible toggle and explains the rules", async () => {
    await openManager();

    const toggle = screen.getByRole("button", { name: "Hide categories" });
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    expect(document.getElementById(toggle.getAttribute("aria-controls")!)).toBeInTheDocument();
    expect(screen.getByText(/Renaming a custom category updates it on every transaction and budget/))
      .toBeInTheDocument();
  });

  it("offers no rename, icon, or delete controls for built-in categories", async () => {
    await openManager();

    for (const builtIn of [groceries, housing, other]) {
      const item = row(builtIn.name);
      expect(within(item).getByText("Built-in")).toBeInTheDocument();
      expect(within(item).queryByRole("button")).not.toBeInTheDocument();
    }
    expect(within(row("Pet Care")).getByRole("button", { name: "Edit Pet Care" })).toBeInTheDocument();
    expect(within(row("Pet Care")).getByRole("button", { name: "Delete Pet Care" })).toBeInTheDocument();
  });

  it("renames and re-icons a custom category, then restores focus", async () => {
    const renamed = { ...petCare, name: "Pets", iconKey: "heart-pulse" };
    vi.mocked(categoryService.updateCategory).mockResolvedValue(renamed);
    const user = await openManager();
    vi.mocked(categoryService.getCategories).mockResolvedValue([groceries, housing, other, renamed]);

    await user.click(screen.getByRole("button", { name: "Edit Pet Care" }));
    const form = screen.getByRole("form", { name: "Edit Pet Care" });
    const name = within(form).getByLabelText("Category name");
    expect(name).toHaveValue("Pet Care");
    expect(within(form).getByRole("radio", { name: "Paw print" })).toBeChecked();

    await user.clear(name);
    await user.type(name, "Pets");
    await user.click(within(form).getByRole("radio", { name: "Heart with pulse" }));
    await user.click(within(form).getByRole("button", { name: "Save category" }));

    expect(categoryService.updateCategory).toHaveBeenCalledWith(petCare.id,
      { name: "Pets", budgetEnabled: true, iconKey: "heart-pulse" });
    expect(await screen.findByText("Saved “Pets”.")).toHaveAttribute("role", "status");
    await waitFor(() => expect(screen.getByRole("button", { name: "Edit Pets" })).toHaveFocus());
    expect(onChange).toHaveBeenCalledWith({ type: "updated", category: renamed });
    expect(categoryService.getCategories).toHaveBeenCalledTimes(2);
  });

  it("validates the name before saving and focuses it", async () => {
    const user = await openManager();
    await user.click(screen.getByRole("button", { name: "Edit Pet Care" }));
    const name = screen.getByLabelText("Category name");

    await user.clear(name);
    await user.click(screen.getByRole("button", { name: "Save category" }));

    expect(name).toHaveAttribute("aria-invalid", "true");
    expect(name).toHaveAccessibleDescription("Category name is required");
    expect(name).toHaveFocus();
    expect(categoryService.updateCategory).not.toHaveBeenCalled();

    await user.type(name, "x");
    expect(name).not.toHaveAttribute("aria-invalid");
  });

  it("puts a duplicate-name conflict on the name field and keeps the draft", async () => {
    vi.mocked(categoryService.updateCategory).mockRejectedValue(
      new ApiError("Category already exists", 409, undefined, "CATEGORY_DUPLICATE"));
    const user = await openManager();
    await user.click(screen.getByRole("button", { name: "Edit Pet Care" }));
    const name = screen.getByLabelText("Category name");
    await user.clear(name);
    await user.type(name, "groceries");
    await user.click(screen.getByRole("button", { name: "Save category" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("That name is already used by another of your categories.");
    expect(name).toHaveValue("groceries");
    expect(name).toHaveAttribute("aria-invalid", "true");
    expect(name).toHaveAccessibleDescription(expect.stringContaining("already have a category with this name"));
    expect(name).toHaveFocus();
  });

  it("shows server field errors beside the management fields", async () => {
    vi.mocked(categoryService.updateCategory).mockRejectedValue(new ApiError("Validation failed.", 400, {
      name: "Category name contains unsupported characters",
      iconKey: "Icon must be one of the approved category icons",
    }));
    const user = await openManager();
    await user.click(screen.getByRole("button", { name: "Edit Pet Care" }));
    await user.click(screen.getByRole("button", { name: "Save category" }));

    expect(await screen.findByLabelText("Category name"))
      .toHaveAccessibleDescription("Category name contains unsupported characters");
    expect(screen.getByRole("radiogroup", { name: "Icon" }))
      .toHaveAccessibleDescription("Icon must be one of the approved category icons");
    expect(screen.getByRole("alert")).toHaveTextContent("Please check the highlighted fields.");
  });

  it.each([
    ["CATEGORY_NOT_FOUND", 404, "This category no longer exists. The list has been refreshed."],
    ["CATEGORY_BUILT_IN", 403, "Built-in categories cannot be changed or deleted."],
  ])("handles %s on save by closing the editor and refreshing", async (code, status, message) => {
    vi.mocked(categoryService.updateCategory).mockRejectedValue(
      new ApiError("Built-in categories cannot be changed or deleted.", status, undefined, code));
    const user = await openManager();
    await user.click(screen.getByRole("button", { name: "Edit Pet Care" }));
    await user.click(screen.getByRole("button", { name: "Save category" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(screen.queryByRole("form")).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole("button", { name: "Hide categories" })).toHaveFocus());
  });

  it("reports an unexpected save failure and keeps editing", async () => {
    vi.mocked(categoryService.updateCategory).mockRejectedValue(new Error("network"));
    const user = await openManager();
    await user.click(screen.getByRole("button", { name: "Edit Pet Care" }));
    await user.click(screen.getByRole("button", { name: "Save category" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Unable to save the category. Please try again.");
    expect(screen.getByRole("form", { name: "Edit Pet Care" })).toBeInTheDocument();
  });

  it("cancels editing and returns focus to the edit button", async () => {
    const user = await openManager();
    await user.click(screen.getByRole("button", { name: "Edit Pet Care" }));
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    await waitFor(() => expect(screen.getByRole("button", { name: "Edit Pet Care" })).toHaveFocus());
  });

  it("confirms before deleting an unused category with the keyboard", async () => {
    vi.mocked(categoryService.deleteCategory).mockResolvedValue(undefined);
    const user = await openManager();
    vi.mocked(categoryService.getCategories).mockResolvedValue([groceries, housing, other]);

    screen.getByRole("button", { name: "Delete Pet Care" }).focus();
    await user.keyboard("{Enter}");
    const confirm = screen.getByRole("group", { name: "Delete “Pet Care”? This cannot be undone." });
    expect(within(confirm).getByRole("button", { name: "Delete category" })).toHaveFocus();
    expect(categoryService.deleteCategory).not.toHaveBeenCalled();

    await user.keyboard("{Enter}");

    expect(categoryService.deleteCategory).toHaveBeenCalledWith(petCare.id);
    expect(await screen.findByText("Deleted “Pet Care”.")).toHaveAttribute("role", "status");
    expect(screen.queryByText("Pet Care")).not.toBeInTheDocument();
    expect(onChange).toHaveBeenCalledWith({ type: "deleted", categoryId: petCare.id });
    await waitFor(() => expect(screen.getByRole("button", { name: "Hide categories" })).toHaveFocus());
  });

  it("keeps the category when the delete is cancelled", async () => {
    const user = await openManager();
    await user.click(screen.getByRole("button", { name: "Delete Pet Care" }));
    await user.click(screen.getByRole("button", { name: "Keep category" }));

    await waitFor(() => expect(screen.getByRole("button", { name: "Delete Pet Care" })).toHaveFocus());
    expect(categoryService.deleteCategory).not.toHaveBeenCalled();
  });

  it("refuses to delete a category in use and keeps it listed", async () => {
    const message = "This category is used by transactions or budgets and cannot be deleted.";
    vi.mocked(categoryService.deleteCategory).mockRejectedValue(
      new ApiError(message, 409, undefined, "CATEGORY_IN_USE"));
    const user = await openManager();
    await user.click(screen.getByRole("button", { name: "Delete Pet Care" }));
    await user.click(screen.getByRole("button", { name: "Delete category" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(screen.getByText("Pet Care")).toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.getByRole("button", { name: "Delete Pet Care" })).toHaveFocus());
  });

  it.each([
    [new ApiError("Category not found", 404, undefined, "CATEGORY_NOT_FOUND"),
      "This category no longer exists. The list has been refreshed."],
    [new ApiError("Built-in categories cannot be changed or deleted.", 403, undefined, "CATEGORY_BUILT_IN"),
      "Built-in categories cannot be changed or deleted."],
    [new Error("network"), "Unable to delete the category. Please try again."],
  ])("handles a failed delete: %s", async (error, message) => {
    vi.mocked(categoryService.deleteCategory).mockRejectedValue(error);
    const user = await openManager();
    await user.click(screen.getByRole("button", { name: "Delete Pet Care" }));
    await user.click(screen.getByRole("button", { name: "Delete category" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
  });

  it("shows loading and retry when the list cannot load", async () => {
    const user = userEvent.setup();
    vi.mocked(categoryService.getCategories).mockRejectedValueOnce(new Error("network"));
    render(<CategoryProvider><CategoryManager /></CategoryProvider>);
    await user.click(screen.getByRole("button", { name: "Manage categories" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Categories could not be loaded.");
    await user.click(screen.getByRole("button", { name: "Retry loading categories" }));
    expect(await screen.findByText("Pet Care")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Hide categories" }));
    expect(screen.queryByText("Pet Care")).not.toBeInTheDocument();
  });
});

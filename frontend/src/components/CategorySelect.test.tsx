vi.mock("../context/AuthContext", () => ({ useAuth: vi.fn() }));
vi.mock("../services/categoryService", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../services/categoryService")>()),
  getCategories: vi.fn(),
}));

import { useEffect, useState } from "react";
import { useCategories } from "../context/CategoryContext";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../context/AuthContext";
import { CategoryProvider } from "../context/CategoryProvider";
import * as categoryService from "../services/categoryService";
import { accountContext, deferred } from "../test/accountFixtures";
import { legacy, other, petCare, sampleCategories } from "../test/categoryFixtures";
import type { CategoryResponse } from "../types/category";
import { CREATE_CATEGORY_VALUE, EMPTY_CATEGORY_DRAFT, type CategoryDraft } from "../utils/categoryForm";
import { CategorySelect, type CategorySelectErrors } from "./CategorySelect";
import { CategoryIcon, CategoryLabel } from "./CategoryIcon";
import { CategoryRefreshNotice } from "./CategoryRefreshNotice";

let lastValue = "";
let lastDraft: CategoryDraft = EMPTY_CATEGORY_DRAFT;

function capture(value: string, draft: CategoryDraft) {
  lastValue = value;
  lastDraft = draft;
}

function Harness({ initial = "", errors = {}, disabled = false }: {
  initial?: string;
  errors?: CategorySelectErrors;
  disabled?: boolean;
}) {
  const [value, setValue] = useState(initial);
  const [draft, setDraft] = useState<CategoryDraft>(EMPTY_CATEGORY_DRAFT);
  const [blurs, setBlurs] = useState(0);
  useEffect(() => capture(value, draft));
  return (
    <>
      <CategorySelect
        id="test-category"
        value={value}
        onChange={setValue}
        draft={draft}
        onNameChange={(name) => setDraft({ ...draft, name })}
        onNameBlur={() => setBlurs(blurs + 1)}
        onIconChange={(iconKey) => setDraft({ ...draft, iconKey })}
        errors={errors}
        disabled={disabled}
        existingMatch={petCare}
        onUseExisting={(category) => setValue(String(category.id))}
      />
      <output data-testid="blurs">{blurs}</output>
    </>
  );
}

function renderSelect(props: Parameters<typeof Harness>[0] = {}) {
  return render(<CategoryProvider><Harness {...props} /></CategoryProvider>);
}

// Full user-event flows are slow under coverage instrumentation.
vi.setConfig({ testTimeout: 20_000 });

describe("CategorySelect", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useAuth).mockReturnValue(accountContext());
    vi.mocked(categoryService.getCategories).mockResolvedValue(sampleCategories);
  });

  it("lists built-in and custom categories, the persisted Other, and a separate create option", async () => {
    renderSelect();
    const select = await screen.findByLabelText("Category");
    await waitFor(() => expect(within(select).getAllByRole("option")).toHaveLength(6));

    const options = within(select).getAllByRole("option").map((option) => [option.textContent, (option as HTMLOptionElement).value]);
    expect(options).toEqual([
      ["Select a category", ""],
      ["Groceries", "1"],
      ["Housing", "2"],
      ["Other", "13"],
      ["Pet Care", "40"],
      ["Create a custom category…", CREATE_CATEGORY_VALUE],
    ]);
    expect(select).toBeRequired();
  });

  it("selects the persisted Other like any other category, without creation fields", async () => {
    const user = userEvent.setup();
    renderSelect();
    await user.selectOptions(await screen.findByLabelText("Category"), "13");

    expect(lastValue).toBe(String(other.id));
    expect(screen.queryByRole("group", { name: "New category" })).not.toBeInTheDocument();
  });

  it("reveals name and icon fields in create mode and hides them again", async () => {
    const user = userEvent.setup();
    renderSelect();
    const select = await screen.findByLabelText("Category");
    await user.selectOptions(select, CREATE_CATEGORY_VALUE);

    const group = screen.getByRole("group", { name: "New category" });
    const name = within(group).getByLabelText("Category name");
    expect(name).toHaveAccessibleDescription(
      "This category is saved with this entry and is then available everywhere you choose a category.",
    );
    expect(name).not.toHaveAttribute("aria-invalid");
    expect(within(group).getByRole("radiogroup", { name: "Icon" })).toBeInTheDocument();
    expect(within(group).getByRole("radio", { name: "Tag" })).toBeChecked();

    await user.type(name, "Café ☕");
    expect(lastDraft.name).toBe("Café ☕");
    await user.tab();
    expect(screen.getByTestId("blurs")).toHaveTextContent("1");

    await user.selectOptions(select, "1");
    expect(screen.queryByRole("group", { name: "New category" })).not.toBeInTheDocument();
    expect(lastValue).toBe("1");
  });

  it("shows a muted generic icon until a category is chosen", async () => {
    const { container } = renderSelect();
    await screen.findByRole("option", { name: "Pet Care" });

    const icon = container.querySelector(".category-select__control svg");
    expect(icon).toHaveClass("lucide-tags", "category-icon--placeholder");
    expect(icon).toHaveAttribute("aria-hidden", "true");
  });

  it("chooses icons with the keyboard and shows the choice beside the select", async () => {
    const user = userEvent.setup();
    const { container } = renderSelect({ initial: CREATE_CATEGORY_VALUE });
    await screen.findByRole("radiogroup", { name: "Icon" });

    await user.click(screen.getByRole("radio", { name: "Paw print" }));
    expect(lastDraft.iconKey).toBe("paw-print");
    await user.keyboard("{ArrowRight}");
    expect(screen.getByRole("radio", { name: "Gift" })).toBeChecked();
    expect(screen.getByRole("radio", { name: "Gift" })).toHaveFocus();
    expect(lastDraft.iconKey).toBe("gift");

    const icon = container.querySelector(".category-select__control svg");
    expect(icon).toHaveAttribute("aria-hidden", "true");
    expect(icon).toHaveClass("lucide-gift");
  });

  it("associates every error with its control", async () => {
    renderSelect({
      initial: CREATE_CATEGORY_VALUE,
      errors: { selection: "Choose one", name: "Name is required", iconKey: "Pick an approved icon" },
    });

    const select = await screen.findByLabelText("Category");
    expect(select).toHaveAttribute("aria-invalid", "true");
    expect(select).toHaveAccessibleDescription("Choose one");
    const name = screen.getByLabelText("Category name");
    expect(name).toHaveAttribute("aria-invalid", "true");
    expect(name).toHaveAccessibleDescription(expect.stringContaining("Name is required"));
    const icons = screen.getByRole("radiogroup", { name: "Icon" });
    expect(icons).toHaveAttribute("aria-invalid", "true");
    expect(icons).toHaveAccessibleDescription("Pick an approved icon");
  });

  it("offers the existing category after a duplicate name error", async () => {
    const user = userEvent.setup();
    renderSelect({ initial: CREATE_CATEGORY_VALUE, errors: { name: "Duplicate" } });

    await user.click(await screen.findByRole("button", { name: "Use existing category “Pet Care”" }));
    expect(lastValue).toBe("40");
  });

  it("keeps an edit's stored category while the list loads", async () => {
    const pending = deferred<CategoryResponse[]>();
    vi.mocked(categoryService.getCategories).mockReturnValue(pending.promise);
    renderSelect({ initial: "40" });

    const select = screen.getByLabelText("Category");
    expect(select).toHaveValue("40");
    expect(within(select).getByRole("option", { name: "Loading category…" })).toBeInTheDocument();
    expect(screen.getByText("Loading categories…")).toHaveAttribute("role", "status");
    expect(select).toHaveAccessibleDescription("Loading categories…");

    pending.resolve(sampleCategories);
    await waitFor(() => expect(within(select).queryByText("Loading category…")).not.toBeInTheDocument());
    expect(select).toHaveValue("40");
  });

  it("shows a deleted stored category as unselected once the list loads", async () => {
    renderSelect({ initial: "999" });
    const select = await screen.findByLabelText("Category");

    await waitFor(() => expect(select).toHaveValue(""));
  });

  it("reports a load failure with retry, keeping custom creation available", async () => {
    const user = userEvent.setup();
    vi.mocked(categoryService.getCategories).mockRejectedValueOnce(new Error("network"));
    renderSelect();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Categories could not be loaded. You can still create a custom category.",
    );
    expect(within(screen.getByLabelText("Category")).getByRole("option", { name: "Create a custom category…" }))
      .toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Retry loading categories" }));
    await waitFor(() => expect(screen.queryByRole("alert")).not.toBeInTheDocument());
    expect(within(screen.getByLabelText("Category")).getByRole("option", { name: "Pet Care" })).toBeInTheDocument();
  });

  it("disables every control while saving", async () => {
    renderSelect({ initial: CREATE_CATEGORY_VALUE, disabled: true });

    expect(await screen.findByLabelText("Category")).toBeDisabled();
    expect(screen.getByLabelText("Category name")).toBeDisabled();
    expect(screen.getByRole("radio", { name: "Tag" })).toBeDisabled();
  });
});

describe("CategoryIcon and CategoryLabel", () => {
  it("renders decorative icons next to the visible name, falling back to tag", () => {
    const { container } = render(
      <>
        <CategoryLabel name={legacy.name} iconKey={legacy.iconKey} />
        <CategoryIcon iconKey={null} />
      </>,
    );

    expect(screen.getByText("Legacy")).toBeVisible();
    const icons = container.querySelectorAll("svg");
    expect(icons).toHaveLength(2);
    icons.forEach((icon) => {
      expect(icon).toHaveAttribute("aria-hidden", "true");
      expect(icon).toHaveClass("lucide-tag");
    });
  });
});

describe("CategoryRefreshNotice", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useAuth).mockReturnValue(accountContext());
  });

  it("warns without blocking after a failed refresh and retries", async () => {
    const user = userEvent.setup();
    vi.mocked(categoryService.getCategories)
      .mockResolvedValueOnce(sampleCategories)
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce(sampleCategories);

    function Refresher() {
      const { reload, categories } = useCategories();
      return (
        <>
          <p>{categories.length} categories</p>
          <button type="button" onClick={() => void reload()}>Refresh</button>
        </>
      );
    }
    render(<CategoryProvider><Refresher /><CategoryRefreshNotice /></CategoryProvider>);
    await screen.findByText("4 categories");
    expect(screen.queryByText(/could not be refreshed/)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Refresh" }));
    expect(await screen.findByRole("status")).toHaveTextContent(
      "Category options could not be refreshed. Your changes were saved.",
    );
    expect(screen.getByText("4 categories")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Retry loading categories" }));
    await waitFor(() => expect(screen.queryByText(/could not be refreshed/)).not.toBeInTheDocument());
    expect(categoryService.getCategories).toHaveBeenCalledTimes(3);
  });
});

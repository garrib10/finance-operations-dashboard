import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ApiError } from "../services/api";
import { deferred } from "../test/accountFixtures";
import { CategoryForm } from "./CategoryForm";

function renderForm(overrides: Partial<Parameters<typeof CategoryForm>[0]> = {}) {
  const props = {
    label: "Edit Pet Care",
    initial: { name: "Pet Care", iconKey: "paw-print" as const },
    submitLabel: "Save category",
    pendingLabel: "Saving…",
    failureMessage: "Unable to save the category. Please try again.",
    onSubmit: vi.fn().mockResolvedValue(undefined),
    onCancel: vi.fn(),
    ...overrides,
  };
  const user = userEvent.setup();
  render(<CategoryForm {...props} />);
  return { user, props, name: () => screen.getByLabelText("Category name") };
}

// Full user-event flows are slow under coverage instrumentation and on a busy machine.
vi.setConfig({ testTimeout: 20_000 });

describe("CategoryForm", () => {
  it("starts from the given name and icon in a labelled form", () => {
    const { name } = renderForm();

    expect(screen.getByRole("form", { name: "Edit Pet Care" })).toBeInTheDocument();
    expect(name()).toHaveValue("Pet Care");
    expect(screen.getByRole("radio", { name: "Paw print" })).toBeChecked();
  });

  it.each([
    ["the name", "Pets", null, { name: "Pets", iconKey: "paw-print" }],
    ["the icon", null, "Heart with pulse", { name: "Pet Care", iconKey: "heart-pulse" }],
    ["both", "Pets", "Heart with pulse", { name: "Pets", iconKey: "heart-pulse" }],
  ])("submits a change to %s", async (_change, newName, icon, expected) => {
    const { user, props, name } = renderForm();
    if (newName) {
      await user.clear(name());
      await user.type(name(), newName);
    }
    if (icon) await user.click(screen.getByRole("radio", { name: icon }));

    await user.click(screen.getByRole("button", { name: "Save category" }));

    expect(props.onSubmit).toHaveBeenCalledExactlyOnceWith(expected);
  });

  it("validates the name before submitting and focuses it", async () => {
    const { user, props, name } = renderForm();
    await user.clear(name());
    await user.click(screen.getByRole("button", { name: "Save category" }));

    expect(name()).toHaveAttribute("aria-invalid", "true");
    expect(name()).toHaveAccessibleDescription("Category name is required");
    expect(name()).toHaveFocus();
    expect(props.onSubmit).not.toHaveBeenCalled();

    await user.type(name(), "x");
    expect(name()).not.toHaveAttribute("aria-invalid");
  });

  it("validates the name when it loses focus", async () => {
    const { user, name } = renderForm();
    await user.clear(name());
    await user.tab();

    expect(name()).toHaveAccessibleDescription("Category name is required");
  });

  it("puts a duplicate-name conflict on the name field and keeps the draft", async () => {
    const { user, name } = renderForm({
      onSubmit: vi.fn().mockRejectedValue(new ApiError("Category already exists", 409, undefined, "CATEGORY_DUPLICATE")),
    });
    await user.clear(name());
    await user.type(name(), "groceries");
    await user.click(screen.getByRole("button", { name: "Save category" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("That name is already used by another of your categories.");
    expect(name()).toHaveValue("groceries");
    expect(name()).toHaveAttribute("aria-invalid", "true");
    expect(name()).toHaveAccessibleDescription(expect.stringContaining("already have a category with this name"));
    expect(name()).toHaveFocus();
  });

  it("shows server field errors beside the name and icon", async () => {
    const { user, name } = renderForm({
      onSubmit: vi.fn().mockRejectedValue(new ApiError("Validation failed.", 400, {
        name: "Category name contains unsupported characters",
        iconKey: "Icon must be one of the approved category icons",
      })),
    });
    await user.click(screen.getByRole("button", { name: "Save category" }));

    await waitFor(() => expect(name()).toHaveAccessibleDescription("Category name contains unsupported characters"));
    expect(screen.getByRole("radiogroup", { name: "Icon" }))
      .toHaveAccessibleDescription("Icon must be one of the approved category icons");
    expect(screen.getByRole("alert")).toHaveTextContent("Please check the highlighted fields.");
  });

  it("shows other server messages in the form summary", async () => {
    const { user } = renderForm({
      onSubmit: vi.fn().mockRejectedValue(new ApiError("Validation failed.", 400, { budgetEnabled: "Must be a boolean" })),
    });
    await user.click(screen.getByRole("button", { name: "Save category" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Must be a boolean");
  });

  it("reports an unexpected failure and keeps the draft for another try", async () => {
    const { user, name } = renderForm({ onSubmit: vi.fn().mockRejectedValue(new Error("network")) });
    await user.click(screen.getByRole("button", { name: "Save category" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Unable to save the category. Please try again.");
    // No field to fix, so focus moves to the message rather than being lost.
    await waitFor(() => expect(alert).toHaveFocus());
    expect(screen.getAllByRole("alert")).toHaveLength(1);
    expect(name()).toHaveValue("Pet Care");
  });

  it("submits once while a save is in progress", async () => {
    const pending = deferred<void>();
    const { user, props, name } = renderForm({ onSubmit: vi.fn(() => pending.promise) });
    await user.click(screen.getByRole("button", { name: "Save category" }));

    expect(screen.getByRole("button", { name: "Saving…" })).toBeDisabled();
    expect(name()).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "Saving…" }));
    expect(props.onSubmit).toHaveBeenCalledOnce();
    pending.resolve();
  });

  it("reports each draft change so a parent can restore it", async () => {
    const onDraftChange = vi.fn();
    const { user, name } = renderForm({ onDraftChange });
    await user.type(name(), "s");
    await user.click(screen.getByRole("radio", { name: "Gift" }));

    expect(onDraftChange).toHaveBeenCalledWith({ name: "Pet Cares", iconKey: "paw-print" });
    expect(onDraftChange).toHaveBeenLastCalledWith({ name: "Pet Cares", iconKey: "gift" });
  });

  it("cancels without saving", async () => {
    const { user, props } = renderForm();
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(props.onCancel).toHaveBeenCalledOnce();
    expect(props.onSubmit).not.toHaveBeenCalled();
  });

  it("can focus the name field when it opens", () => {
    const { name } = renderForm({ focusNameOnOpen: true, initial: { name: "", iconKey: "tag" as const } });

    expect(name()).toHaveFocus();
  });
});

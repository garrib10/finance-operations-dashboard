import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { deferred } from "../test/accountFixtures";
import { CategoryDeleteConfirm } from "./CategoryDeleteConfirm";

describe("CategoryDeleteConfirm", () => {
  function renderConfirm(onConfirm = vi.fn().mockResolvedValue(undefined)) {
    const onCancel = vi.fn();
    render(<CategoryDeleteConfirm categoryName="Pet Care" onConfirm={onConfirm} onCancel={onCancel} />);
    return { user: userEvent.setup(), onConfirm, onCancel };
  }

  it("names the category, warns it cannot be undone, and focuses the confirm button", () => {
    renderConfirm();

    expect(screen.getByRole("group", { name: "Delete “Pet Care”? This cannot be undone." })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Delete category" })).toHaveFocus();
  });

  it("confirms with the keyboard", async () => {
    const { user, onConfirm } = renderConfirm();
    await user.keyboard("{Enter}");

    expect(onConfirm).toHaveBeenCalledOnce();
  });

  it("confirms only once while the delete is in progress", async () => {
    const pending = deferred<void>();
    const { user, onConfirm } = renderConfirm(vi.fn(() => pending.promise));
    await user.click(screen.getByRole("button", { name: "Delete category" }));

    expect(screen.getByRole("button", { name: "Deleting…" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Keep category" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "Deleting…" }));
    expect(onConfirm).toHaveBeenCalledOnce();
    pending.resolve();
  });

  it("re-enables the buttons when the caller keeps the confirmation open", async () => {
    // The page handles failures itself and resolves; the confirmation just becomes usable again.
    const { user, onConfirm } = renderConfirm(vi.fn().mockResolvedValue(undefined));
    await user.click(screen.getByRole("button", { name: "Delete category" }));

    expect(await screen.findByRole("button", { name: "Delete category" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Keep category" })).toBeEnabled();
    expect(onConfirm).toHaveBeenCalledOnce();
  });

  it("keeps the category on cancel", async () => {
    const { user, onConfirm, onCancel } = renderConfirm();
    await user.click(screen.getByRole("button", { name: "Keep category" }));

    expect(onCancel).toHaveBeenCalledOnce();
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it("puts focus back on Delete category if it was lost while the delete failed", async () => {
    const pending = deferred<void>();
    const { user } = renderConfirm(vi.fn(() => pending.promise));
    await user.click(screen.getByRole("button", { name: "Delete category" }));
    // Browsers drop focus from a button that becomes disabled; jsdom does not, so move
    // focus to a temporary element and remove it, leaving focus on the body.
    const elsewhere = document.body.appendChild(document.createElement("button"));
    elsewhere.focus();
    elsewhere.remove();
    expect(document.activeElement).toBe(document.body);

    // The page handles the failure itself and resolves, leaving the confirmation open.
    pending.resolve();
    await screen.findByRole("button", { name: "Delete category" });
    expect(screen.getByRole("button", { name: "Delete category" })).toHaveFocus();
  });
});

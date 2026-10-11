import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { CategoryActionsMenu } from "./CategoryActionsMenu";

const REASON = "Used by 2 transactions. Change or remove those first to delete this category.";

/** Two cards sharing one open ID, as the page does. */
function Harness({ blocked = false, onEdit = vi.fn(), onDelete = vi.fn(), second = false }) {
  const [openId, setOpenId] = useState<number | null>(null);
  const menu = (id: number, name: string) => (
    <CategoryActionsMenu
      categoryId={id}
      categoryName={name}
      open={openId === id}
      onOpenChange={(open) => setOpenId((current) => (open ? id : current === id ? null : current))}
      onEdit={onEdit}
      onDelete={onDelete}
      deleteBlockedReason={blocked ? REASON : null}
    />
  );
  return (
    <>
      <button type="button">Outside</button>
      {menu(7, "Pet Care")}
      {second && menu(9, "Hobbies")}
    </>
  );
}

function setup(props: Parameters<typeof Harness>[0] = {}) {
  const user = userEvent.setup();
  const view = render(<Harness {...props} />);
  const trigger = (name = "Pet Care") => screen.getByRole("button", { name: `More actions for ${name}` });
  return { user, trigger, ...view };
}

describe("CategoryActionsMenu", () => {
  it("is a closed disclosure named for its category, with a decorative icon", () => {
    const { trigger, container } = setup();

    expect(trigger()).toHaveAttribute("aria-expanded", "false");
    expect(trigger()).toHaveAttribute("aria-controls", "category-7-actions-panel");
    expect(trigger()).toHaveAttribute("id", "category-7-actions");
    expect(trigger()).not.toHaveAttribute("aria-haspopup");
    expect(container.querySelector("#category-7-actions-panel")).not.toBeVisible();
    expect(trigger().querySelector("svg")).toHaveAttribute("aria-hidden", "true");
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit Pet Care" })).not.toBeInTheDocument();
  });

  it("opens and closes with the trigger, leaving focus on it", async () => {
    const { user, trigger } = setup();
    await user.click(trigger());

    expect(trigger()).toHaveAttribute("aria-expanded", "true");
    expect(trigger()).toHaveFocus();
    expect(screen.getByRole("button", { name: "Edit Pet Care" })).toBeVisible();

    await user.click(trigger());
    expect(trigger()).toHaveAttribute("aria-expanded", "false");
  });

  it.each([["Enter", "{Enter}"], ["Space", " "]])("opens with %s, and Tab reaches the actions in order", async (_key, keys) => {
    const { user, trigger } = setup();
    trigger().focus();
    await user.keyboard(keys);

    expect(trigger()).toHaveAttribute("aria-expanded", "true");
    await user.tab();
    expect(screen.getByRole("button", { name: "Edit Pet Care" })).toHaveFocus();
    await user.tab();
    expect(screen.getByRole("button", { name: "Delete Pet Care" })).toHaveFocus();
  });

  it("closes with Escape from inside and returns focus to the trigger", async () => {
    const { user, trigger } = setup();
    await user.click(trigger());
    await user.tab();
    await user.keyboard("{Escape}");

    expect(trigger()).toHaveAttribute("aria-expanded", "false");
    expect(trigger()).toHaveFocus();
  });

  it("closes on a click outside without moving focus there unexpectedly", async () => {
    const { user, trigger } = setup();
    await user.click(trigger());
    await user.click(document.body);

    expect(trigger()).toHaveAttribute("aria-expanded", "false");
  });

  it("closes when focus moves elsewhere on the page", async () => {
    const { user, trigger } = setup();
    await user.click(trigger());
    await user.tab(); // Edit
    await user.tab({ shift: true }); // Back to the trigger: still inside, still open.
    expect(trigger()).toHaveAttribute("aria-expanded", "true");

    await user.tab({ shift: true }); // The "Outside" button before the disclosure.
    expect(screen.getByRole("button", { name: "Outside" })).toHaveFocus();
    expect(trigger()).toHaveAttribute("aria-expanded", "false");
  });

  it("keeps only one disclosure open", async () => {
    const { user, trigger } = setup({ second: true });
    await user.click(trigger("Pet Care"));
    await user.click(trigger("Hobbies"));

    expect(trigger("Hobbies")).toHaveAttribute("aria-expanded", "true");
    expect(trigger("Pet Care")).toHaveAttribute("aria-expanded", "false");
  });

  it.each([["Edit"], ["Delete"]])("closes and starts the workflow when %s is chosen", async (action) => {
    const onEdit = vi.fn();
    const onDelete = vi.fn();
    const { user, trigger } = setup({ onEdit, onDelete });
    await user.click(trigger());
    await user.click(screen.getByRole("button", { name: `${action} Pet Care` }));

    expect(trigger()).toHaveAttribute("aria-expanded", "false");
    expect(action === "Edit" ? onEdit : onDelete).toHaveBeenCalledOnce();
  });

  it("keeps an unavailable Delete focusable, explained, and inert to click, Enter, and Space", async () => {
    const onDelete = vi.fn();
    const { user, trigger } = setup({ blocked: true, onDelete });
    await user.click(trigger());
    const remove = screen.getByRole("button", { name: "Delete Pet Care" });

    expect(remove).toHaveAttribute("aria-disabled", "true");
    expect(remove).not.toBeDisabled();
    expect(remove).toHaveAccessibleDescription(REASON);
    expect(screen.getByText(REASON)).toBeVisible();

    await user.click(remove);
    remove.focus();
    await user.keyboard("{Enter}");
    await user.keyboard(" ");
    expect(onDelete).not.toHaveBeenCalled();
    expect(trigger()).toHaveAttribute("aria-expanded", "true");
  });

  it("removes its outside-click listener when it unmounts while open", async () => {
    const remove = vi.spyOn(document, "removeEventListener");
    const { user, trigger, unmount } = setup();
    await user.click(trigger());
    unmount();

    expect(remove).toHaveBeenCalledWith("pointerdown", expect.any(Function));
    remove.mockRestore();
  });
});

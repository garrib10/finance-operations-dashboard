import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { InlineNotice } from "./InlineNotice";

describe("InlineNotice", () => {
  it.each([
    ["error", "alert", "Error:"],
    ["warning", "status", "Warning:"],
    ["info", "status", "Note:"],
  ] as const)("shows %s with role %s and a visible label, not colour alone", (variant, role, label) => {
    render(<InlineNotice variant={variant}>Something happened.</InlineNotice>);

    const notice = screen.getByRole(role);
    expect(notice).toHaveTextContent(`${label} Something happened.`);
    expect(screen.getByText(label)).toBeVisible();
    // One live region per notice: never both roles.
    expect(screen.getAllByRole(role === "alert" ? "alert" : "status")).toHaveLength(1);
    expect(screen.queryByRole(role === "alert" ? "status" : "alert")).not.toBeInTheDocument();
  });

  it("hides its icon from assistive technology", () => {
    const { container } = render(<InlineNotice variant="warning">Careful.</InlineNotice>);

    expect(container.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
  });

  it("has no buttons unless asked for", () => {
    render(<InlineNotice variant="info">Just so you know.</InlineNotice>);

    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("offers a named dismiss button that calls back", async () => {
    const user = userEvent.setup();
    const onDismiss = vi.fn();
    render(<InlineNotice variant="error" onDismiss={onDismiss}>It failed.</InlineNotice>);

    await user.click(screen.getByRole("button", { name: "Dismiss error" }));
    expect(onDismiss).toHaveBeenCalledOnce();
  });

  it("offers a recovery action", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<InlineNotice variant="warning" action={{ label: "Try again", onClick }} onDismiss={vi.fn()}>Stale.</InlineNotice>);

    await user.click(screen.getByRole("button", { name: "Try again" }));
    expect(onClick).toHaveBeenCalledOnce();
    expect(screen.getByRole("button", { name: "Dismiss warning" })).toBeInTheDocument();
  });

  it("can take focus when given an ID, and keeps long messages whole", () => {
    const long = "A very long explanation ".repeat(30).trim();
    render(<InlineNotice variant="error" id="page-notice">{long}</InlineNotice>);

    const notice = screen.getByRole("alert");
    expect(notice).toHaveAttribute("id", "page-notice");
    notice.focus();
    expect(notice).toHaveFocus();
    expect(notice).toHaveTextContent(long);
  });

  it("is not focusable without an ID", () => {
    render(<InlineNotice variant="info">Note.</InlineNotice>);

    expect(screen.getByRole("status")).not.toHaveAttribute("tabindex");
  });
});

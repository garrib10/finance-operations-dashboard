import { render, screen } from "@testing-library/react";
import { Wallet } from "lucide-react";
import { describe, expect, it } from "vitest";
import { IconField, IconLabel } from "./IconField";

describe("IconField", () => {
  it("places a decorative icon beside the control without changing its label", () => {
    const { container } = render(
      <>
        <label htmlFor="limit">Monthly Limit</label>
        <IconField icon={Wallet}><input id="limit" /></IconField>
      </>,
    );
    const icon = container.querySelector(".icon-field svg");

    expect(screen.getByRole("textbox", { name: "Monthly Limit" })).toBeInTheDocument();
    expect(icon).toHaveClass("field-icon");
    expect(icon).toHaveAttribute("aria-hidden", "true");
  });

  it("can hide the icon until it is wanted", () => {
    const { container } = render(<IconField icon={Wallet} showIcon={false}><input /></IconField>);

    expect(container.querySelector("svg")).not.toBeInTheDocument();
    expect(container.querySelector(".icon-field")).toHaveClass("icon-field--empty");
  });

  it("accepts a custom icon class", () => {
    const { container } = render(<IconField icon={Wallet} iconClassName="custom"><input /></IconField>);

    expect(container.querySelector("svg")).toHaveClass("custom");
  });

  it("labels text with a decorative icon", () => {
    const { container } = render(<IconLabel icon={Wallet}>Oct 2, 2026</IconLabel>);

    expect(screen.getByText("Oct 2, 2026")).toBeInTheDocument();
    expect(container.querySelector(".icon-label svg")).toHaveAttribute("aria-hidden", "true");
  });
});

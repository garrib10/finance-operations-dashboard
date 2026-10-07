import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { CategoryPeriodControls } from "./CategoryPeriodControls";

const OCTOBER_2026 = { month: 10, year: 2026 };

function renderControls(props: Partial<Parameters<typeof CategoryPeriodControls>[0]> = {}) {
  const onChange = vi.fn();
  const onReset = vi.fn();
  render(
    <CategoryPeriodControls selected={OCTOBER_2026} current={OCTOBER_2026} onChange={onChange} onReset={onReset} {...props} />,
  );
  return { user: userEvent.setup(), onChange, onReset };
}

const month = () => screen.getByLabelText("Month");
const year = () => screen.getByLabelText("Year");
const optionLabels = (select: HTMLElement) => within(select).getAllByRole("option").map((option) => option.textContent);

describe("CategoryPeriodControls", () => {
  it("labels both selects, shows the selected month, and is not a form", () => {
    const { container } = render(
      <CategoryPeriodControls selected={{ month: 8, year: 2026 }} current={OCTOBER_2026} onChange={vi.fn()} onReset={vi.fn()} />,
    );

    expect(screen.getByRole("group", { name: "Reporting month" })).toBeInTheDocument();
    expect(month()).toHaveValue("8");
    expect(year()).toHaveValue("2026");
    expect(container.querySelector("form")).toBeNull();
  });

  it("offers 2000 to the current year, and no future months this year", () => {
    renderControls();

    expect(optionLabels(year())[0]).toBe("2000");
    expect(optionLabels(year()).at(-1)).toBe("2026");
    expect(optionLabels(month()).at(-1)).toBe("October");
    expect(optionLabels(month())).toHaveLength(10);
  });

  it("offers all twelve months for an earlier year", () => {
    renderControls({ selected: { month: 3, year: 2025 } });

    expect(optionLabels(month())).toHaveLength(12);
  });

  it("reports a chosen month and year", async () => {
    const { user, onChange } = renderControls({ selected: { month: 3, year: 2025 } });
    await user.selectOptions(month(), "8");
    expect(onChange).toHaveBeenLastCalledWith({ month: 8, year: 2025 });

    await user.selectOptions(year(), "2024");
    expect(onChange).toHaveBeenLastCalledWith({ month: 3, year: 2024 });
  });

  it("moves the month back to the current one when a year change would make it future", async () => {
    const { user, onChange } = renderControls({ selected: { month: 12, year: 2025 } });
    await user.selectOptions(year(), "2026");

    expect(onChange).toHaveBeenLastCalledWith(OCTOBER_2026);
  });

  it("offers Back to current month only for an earlier month", async () => {
    renderControls();
    expect(screen.queryByRole("button", { name: "Back to current month" })).not.toBeInTheDocument();
  });

  it("goes back to the current month with a plain button", async () => {
    const { user, onReset } = renderControls({ selected: { month: 8, year: 2026 } });
    const back = screen.getByRole("button", { name: "Back to current month" });

    expect(back).toHaveAttribute("type", "button");
    await user.click(back);
    expect(onReset).toHaveBeenCalledOnce();
  });

  it("disables everything with a visible reason while a form is open", () => {
    renderControls({ selected: { month: 8, year: 2026 }, disabledReason: "Finish the open form first." });

    for (const control of [month(), year(), screen.getByRole("button", { name: "Back to current month" })]) {
      expect(control).toBeDisabled();
      expect(control).toHaveAccessibleDescription("Finish the open form first.");
    }
    expect(screen.getByText("Finish the open form first.")).toBeVisible();
  });
});

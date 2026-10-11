import { describe, expect, it } from "vitest";
import { categoryAxisProps, CATEGORY_AXIS_LABEL_MAX, shortCategoryLabel } from "./budgetChartAxis";

describe("budget chart category axis (#119)", () => {
  it("shows every tick instead of letting Recharts hide overlapping ones", () => {
    expect(categoryAxisProps.interval).toBe(0);
    expect(categoryAxisProps.dataKey).toBe("category");
  });

  it("angles the labels and reserves room for them below the axis", () => {
    expect(categoryAxisProps.angle).toBeLessThan(0);
    expect(categoryAxisProps.textAnchor).toBe("end");
    expect(categoryAxisProps.height).toBeGreaterThanOrEqual(60);
  });

  it.each(["Dining", "Pet Care", "Transportation"])("keeps %s whole", (name) => {
    expect(shortCategoryLabel(name)).toBe(name);
  });

  it("shortens a long custom name with an ellipsis", () => {
    const label = shortCategoryLabel("Kids Activities and Lessons");

    expect(label).toBe("Kids Activiti…");
    expect(label).toHaveLength(CATEGORY_AXIS_LABEL_MAX);
  });

  it("does not leave a space before the ellipsis", () => {
    expect(shortCategoryLabel("Home Improvement")).toBe("Home Improvem…");
    expect(shortCategoryLabel("Weekend Getaway Trips")).toBe("Weekend Getaw…");
    expect(shortCategoryLabel("Charity Giving Fund")).toBe("Charity Givin…");
    expect(shortCategoryLabel("Car Wash and Detail")).toBe("Car Wash and…");
  });

  it("is the formatter the axis uses", () => {
    expect(categoryAxisProps.tickFormatter).toBe(shortCategoryLabel);
  });
});

import type { XAxisProps } from "recharts";

/** Longest axis label shown in full; longer names end in "…" (the tooltip shows them whole). */
export const CATEGORY_AXIS_LABEL_MAX = 14;

/** Shortens a category name for a chart axis tick. */
export function shortCategoryLabel(name: string): string {
  const label = String(name ?? "");
  return label.length > CATEGORY_AXIS_LABEL_MAX
    ? `${label.slice(0, CATEGORY_AXIS_LABEL_MAX - 1).trimEnd()}…`
    : label;
}

/**
 * Category axis shared by both Budgets page charts (issue #119). By default Recharts hides
 * ticks that would overlap, which left bars unlabelled once a month had about five budgets;
 * every tick is shown instead, angled so they fit at any width.
 */
export const categoryAxisProps = {
  dataKey: "category",
  interval: 0,
  angle: -45,
  textAnchor: "end",
  height: 80,
  tickFormatter: shortCategoryLabel,
} satisfies XAxisProps;

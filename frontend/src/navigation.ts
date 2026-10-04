import { ArrowLeftRight, LayoutDashboard, PiggyBank, Tags, type LucideIcon } from "lucide-react";

/** Above this width the sidebar is shown; at 1100px and below, the drawer. Matches App.css. */
export const DESKTOP_NAV_QUERY = "(width > 1100px)";

export interface NavItem {
  label: string;
  path: string;
  /** Decorative: the visible label is always the link's accessible name. */
  icon: LucideIcon;
  /** Match the path exactly, so "/" is not marked current on every page. */
  end?: boolean;
}

/**
 * The primary destinations shown in every navigation surface (desktop sidebar and mobile
 * drawer). Profile and Account Settings stay in the account menu.
 */
export const PRIMARY_NAV: readonly NavItem[] = [
  { label: "Dashboard", path: "/", icon: LayoutDashboard, end: true },
  { label: "Transactions", path: "/transactions", icon: ArrowLeftRight },
  { label: "Budgets", path: "/budgets", icon: PiggyBank },
  { label: "Categories", path: "/categories", icon: Tags },
];

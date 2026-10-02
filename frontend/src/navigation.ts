import { ArrowLeftRight, LayoutDashboard, PiggyBank, type LucideIcon } from "lucide-react";

export interface NavItem {
  label: string;
  path: string;
  /** Decorative: the visible label is always the link's accessible name. */
  icon: LucideIcon;
  /** Match the path exactly, so "/" is not marked current on every page. */
  end?: boolean;
}

/**
 * The primary destinations shown in every navigation surface (sidebar and, from
 * phase 3, the mobile drawer). Profile and Account Settings stay in the account menu.
 */
export const PRIMARY_NAV: readonly NavItem[] = [
  { label: "Dashboard", path: "/", icon: LayoutDashboard, end: true },
  { label: "Transactions", path: "/transactions", icon: ArrowLeftRight },
  { label: "Budgets", path: "/budgets", icon: PiggyBank },
];

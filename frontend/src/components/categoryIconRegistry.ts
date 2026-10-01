import {
  Baby,
  Briefcase,
  Bus,
  Car,
  CircleDollarSign,
  Clapperboard,
  Coffee,
  CreditCard,
  Dumbbell,
  Fuel,
  Gamepad2,
  Gift,
  GraduationCap,
  HandHeart,
  HeartPulse,
  House,
  Lightbulb,
  Music,
  Package,
  PawPrint,
  PiggyBank,
  Pill,
  Plane,
  Receipt,
  Shield,
  Shirt,
  ShoppingBag,
  ShoppingCart,
  Smartphone,
  Sofa,
  Sparkles,
  Sprout,
  Tag,
  Ticket,
  Tv,
  Utensils,
  Wallet,
  Wine,
  Wrench,
  type LucideIcon,
} from "lucide-react";

interface IconDefinition {
  /** Accessible text name, used by the icon picker. */
  label: string;
  Icon: LucideIcon;
}

/**
 * The approved backend icon catalog (CategoryIcon.java), mapped explicitly to imported
 * components. Server strings are only ever used as keys into this map, never to load or
 * build a component; anything not listed renders the generic "tag" icon.
 */
const CATEGORY_ICONS = {
  house: { label: "House", Icon: House },
  "shopping-cart": { label: "Shopping cart", Icon: ShoppingCart },
  utensils: { label: "Utensils", Icon: Utensils },
  car: { label: "Car", Icon: Car },
  lightbulb: { label: "Light bulb", Icon: Lightbulb },
  shield: { label: "Shield", Icon: Shield },
  "heart-pulse": { label: "Heart with pulse", Icon: HeartPulse },
  clapperboard: { label: "Clapperboard", Icon: Clapperboard },
  "shopping-bag": { label: "Shopping bag", Icon: ShoppingBag },
  plane: { label: "Plane", Icon: Plane },
  "circle-dollar-sign": { label: "Dollar sign", Icon: CircleDollarSign },
  "piggy-bank": { label: "Piggy bank", Icon: PiggyBank },
  "paw-print": { label: "Paw print", Icon: PawPrint },
  gift: { label: "Gift", Icon: Gift },
  dumbbell: { label: "Dumbbell", Icon: Dumbbell },
  "graduation-cap": { label: "Graduation cap", Icon: GraduationCap },
  baby: { label: "Baby", Icon: Baby },
  wrench: { label: "Wrench", Icon: Wrench },
  smartphone: { label: "Smartphone", Icon: Smartphone },
  tv: { label: "Television", Icon: Tv },
  music: { label: "Music", Icon: Music },
  coffee: { label: "Coffee", Icon: Coffee },
  wine: { label: "Wine", Icon: Wine },
  fuel: { label: "Fuel", Icon: Fuel },
  bus: { label: "Bus", Icon: Bus },
  shirt: { label: "Shirt", Icon: Shirt },
  sparkles: { label: "Sparkles", Icon: Sparkles },
  pill: { label: "Pill", Icon: Pill },
  briefcase: { label: "Briefcase", Icon: Briefcase },
  "credit-card": { label: "Credit card", Icon: CreditCard },
  receipt: { label: "Receipt", Icon: Receipt },
  "hand-heart": { label: "Hand with heart", Icon: HandHeart },
  sofa: { label: "Sofa", Icon: Sofa },
  sprout: { label: "Sprout", Icon: Sprout },
  "gamepad-2": { label: "Game controller", Icon: Gamepad2 },
  ticket: { label: "Ticket", Icon: Ticket },
  package: { label: "Package", Icon: Package },
  wallet: { label: "Wallet", Icon: Wallet },
  tag: { label: "Tag", Icon: Tag },
} satisfies Record<string, IconDefinition>;

export type ApprovedIconKey = keyof typeof CATEGORY_ICONS;

export const DEFAULT_ICON_KEY: ApprovedIconKey = "tag";

/** Every approved key, in catalog order, for the icon picker. */
export const APPROVED_ICON_KEYS = Object.keys(CATEGORY_ICONS) as ApprovedIconKey[];

export function isApprovedIconKey(key: unknown): key is ApprovedIconKey {
  return typeof key === "string" && Object.hasOwn(CATEGORY_ICONS, key);
}

/** The approved key to show for any value from the server; unknown values become "tag". */
export function resolveIconKey(key: unknown): ApprovedIconKey {
  return isApprovedIconKey(key) ? key : DEFAULT_ICON_KEY;
}

export function iconLabel(key: unknown): string {
  return CATEGORY_ICONS[resolveIconKey(key)].label;
}

export function iconComponent(key: unknown): LucideIcon {
  return CATEGORY_ICONS[resolveIconKey(key)].Icon;
}

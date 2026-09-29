import type { DateFormatPreference } from "../types/account";

export function formatCurrency(value: number): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
  }).format(value);
}

export function formatDate(value: string, preference: DateFormatPreference = "MEDIUM"): string {
  if (preference === "ISO") return value;

  const [year, month, day] = value.split("-").map(Number);

  const localDate = new Date(year, month - 1, day);

  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(localDate);
}

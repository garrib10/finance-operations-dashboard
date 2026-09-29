import type { UserResponse } from "../types/auth";

export function getAccountName(user: UserResponse | null): string {
  return user?.displayName?.trim()
    || [user?.firstName, user?.lastName].filter(Boolean).join(" ").trim()
    || "Account";
}

export function getInitials(name: string): string {
  const nameParts = name.trim().split(/\s+/);
  return (Array.from(nameParts[0])[0]
    + (nameParts.length > 1 ? Array.from(nameParts[nameParts.length - 1])[0] : ""))
    .toUpperCase();
}

/** Only backend-issued absolute HTTPS URLs are rendered; anything else falls back to initials. */
export function getSafePhotoUrl(value: string | null | undefined): string | null {
  if (!value) return null;
  try {
    return new URL(value).protocol === "https:" ? value : null;
  } catch {
    return null;
  }
}

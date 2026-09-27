import { describe, expect, it } from "vitest";
import { getAccountName, getInitials, getSafePhotoUrl } from "./accountIdentity";
import { accountUser } from "../test/accountFixtures";

describe("account identity", () => {
  it("prefers the display name and falls back to legal names, then Account", () => {
    expect(getAccountName(accountUser)).toBe("River Walker");
    expect(getAccountName({ ...accountUser, displayName: " " })).toBe("Demo User");
    expect(getAccountName({ ...accountUser, displayName: "", firstName: "", lastName: "" })).toBe("Account");
    expect(getAccountName(null)).toBe("Account");
  });

  it.each([["River Quiet Walker", "RW"], ["solo", "S"], ["  émile  zola ", "ÉZ"], ["😀 Smile", "😀S"]])("derives initials for %s", (name, initials) => {
    expect(getInitials(name)).toBe(initials);
  });

  it.each([
    ["https://res.cloudinary.com/demo/image/upload/v1/a.jpg", "https://res.cloudinary.com/demo/image/upload/v1/a.jpg"],
    ["http://res.cloudinary.com/demo/a.jpg", null],
    ["javascript:alert(1)", null],
    ["data:image/png;base64,AAAA", null],
    ["blob:https://app.example/123", null],
    ["profile-photos/key", null],
    ["", null],
    [null, null],
    [undefined, null],
  ])("accepts only absolute HTTPS photo URLs: %s", (value, expected) => {
    expect(getSafePhotoUrl(value)).toBe(expected);
  });
});

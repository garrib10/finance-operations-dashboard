import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../context/AuthProvider";
import { AuthContext, useAuth } from "../context/AuthContext";
import AppHeader from "../components/AppHeader";
import * as accountService from "../services/accountService";
import * as authService from "../services/authService";
import { ApiError } from "../services/api";
import { setAuthToken } from "../utils/authToken";
import { accountContext, accountUser, deferred } from "../test/accountFixtures";
import type { UserResponse } from "../types/auth";
import ProfilePage from "./ProfilePage";
import AccountSettingsPage from "./AccountSettingsPage";

vi.mock("../services/accountService");
vi.mock("../services/authService");

function UserProbe() {
  const { user } = useAuth();
  return <output data-testid="canonical">{JSON.stringify(user)}</output>;
}
async function setup(page: "profile" | "settings") {
  setAuthToken("test-session");
  vi.mocked(authService.getCurrentUser).mockResolvedValue(accountUser);
  render(<MemoryRouter><AuthProvider><AppHeader /><UserProbe />{page === "profile" ? <ProfilePage /> : <AccountSettingsPage />}</AuthProvider></MemoryRouter>);
  await screen.findByRole("button", { name: /account menu for River Walker/i });
  return userEvent.setup();
}
function setField(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label, { exact: true }), { target: { value } });
}
function passwords(current = "old password value", next = "new password value", confirmation = next) {
  setField("Current password", current);
  setField("New password", next);
  setField("Confirm new password", confirmation);
}
beforeEach(() => { vi.resetAllMocks(); localStorage.clear(); });

describe("Profile form", () => {
  it("prefills canonical values, trims only approved fields, updates header, and focuses success", async () => {
    const user = await setup("profile");
    expect(screen.getByLabelText("Display name")).toHaveValue("River Walker");
    expect(screen.getByLabelText("First name")).toHaveValue("Demo");
    expect(screen.getByLabelText("Email")).toHaveAttribute("readonly");
    expect(screen.getByRole("button", { name: "Save profile" })).toBeDisabled();
    setField("Display name", "  New Identity  ");
    setField("First name", "  New  ");
    vi.mocked(accountService.updateProfile).mockResolvedValue({ ...accountUser, displayName: "New Identity", firstName: "New" });
    await user.click(screen.getByRole("button", { name: "Save profile" }));
    expect(accountService.updateProfile).toHaveBeenCalledExactlyOnceWith({ displayName: "New Identity", firstName: "New", lastName: "User" });
    expect(await screen.findByRole("button", { name: /account menu for New Identity/i })).toBeInTheDocument();
    expect(within(screen.getByRole("form", { name: "Profile" })).getByRole("status")).toHaveTextContent("Profile saved.");
    expect(within(screen.getByRole("form", { name: "Profile" })).getByRole("status")).toHaveFocus();
    setField("Display name", "Another");
    expect(within(screen.getByRole("form", { name: "Profile" })).getByRole("status")).toBeEmptyDOMElement();
    await user.click(screen.getByRole("button", { name: "Reset profile" }));
    expect(screen.getByLabelText("Display name")).toHaveValue("New Identity");
  });

  it("preserves draft and canonical identity on failure and links/focuses backend field errors", async () => {
    const user = await setup("profile");
    setField("First name", "Changed");
    vi.mocked(accountService.updateProfile).mockRejectedValue(new ApiError("private detail", 400, { firstName: "First name is invalid" }));
    await user.click(screen.getByRole("button", { name: "Save profile" }));
    const input = screen.getByLabelText("First name");
    expect(input).toHaveValue("Changed");
    expect(input).toHaveFocus();
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).toHaveAccessibleDescription(/First name is invalid/);
    expect(screen.getByTestId("canonical")).toHaveTextContent('"firstName":"Demo"');
    setField("First name", "Corrected");
    expect(input).toHaveAttribute("aria-invalid", "false");
    await user.click(screen.getByRole("button", { name: "Reset profile" }));
    expect(input).toHaveValue("Demo");
  });

  it.each(["", " ", "a".repeat(101)])("validates required and maximum length before requesting: %s", async value => {
    const user = await setup("profile");
    setField("Display name", value);
    await user.click(screen.getByRole("button", { name: "Save profile" }));
    expect(accountService.updateProfile).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Display name")).toHaveFocus();
    expect(screen.getByLabelText("Display name")).toHaveAttribute("aria-invalid", "true");
  });

  it("blocks duplicate submits and shows only safe unexpected error feedback", async () => {
    const user = await setup("profile");
    const pending = deferred<UserResponse>();
    vi.mocked(accountService.updateProfile).mockReturnValue(pending.promise);
    setField("Display name", "Changed");
    await user.click(screen.getByRole("button", { name: "Save profile" }));
    expect(screen.getByRole("button", { name: "Saving profile..." })).toBeDisabled();
    fireEvent.submit(screen.getByRole("form", { name: "Profile" }));
    expect(accountService.updateProfile).toHaveBeenCalledTimes(1);
    await act(async () => pending.reject(new Error("SECRET DATABASE DETAILS")));
    expect(screen.getByRole("alert")).toHaveTextContent("Unable to save changes. Please try again.");
    expect(screen.getByRole("alert")).toHaveFocus();
    expect(screen.queryByText(/SECRET/)).not.toBeInTheDocument();
  });

  it("adopts new canonical values when pristine and preserves an active edit until reset", async () => {
    const context = accountContext();
    const { rerender } = render(<AuthContext.Provider value={context}><ProfilePage /></AuthContext.Provider>);
    const newer = { ...context, user: { ...accountUser, displayName: "External update" } };
    rerender(<AuthContext.Provider value={newer}><ProfilePage /></AuthContext.Provider>);
    expect(screen.getByLabelText("Display name")).toHaveValue("External update");
    setField("Display name", "Unsaved edit");
    const newest = { ...context, user: { ...accountUser, displayName: "Latest update" } };
    rerender(<AuthContext.Provider value={newest}><ProfilePage /></AuthContext.Provider>);
    expect(screen.getByLabelText("Display name")).toHaveValue("Unsaved edit");
    await userEvent.click(screen.getByRole("button", { name: "Reset profile" }));
    expect(screen.getByLabelText("Display name")).toHaveValue("Latest update");
  });
});

describe("Account preferences", () => {
  it("offers only supported options, saves numeric page size and updates canonical state", async () => {
    const user = await setup("settings");
    expect(screen.getByLabelText("Date format")).toHaveValue("MEDIUM");
    expect(screen.getByLabelText("Transactions per page")).toHaveValue("10");
    expect(within(screen.getByLabelText("Date format")).getAllByRole("option").map(option => option.getAttribute("value"))).toEqual(["MEDIUM", "ISO"]);
    expect(within(screen.getByLabelText("Transactions per page")).getAllByRole("option").map(option => option.getAttribute("value"))).toEqual(["10", "25", "50"]);
    await user.selectOptions(screen.getByLabelText("Date format"), "ISO");
    await user.selectOptions(screen.getByLabelText("Transactions per page"), "25");
    vi.mocked(accountService.updatePreferences).mockResolvedValue({ ...accountUser, preferences: { dateFormat: "ISO", transactionPageSize: 25 } });
    await user.click(screen.getByRole("button", { name: "Save preferences" }));
    expect(accountService.updatePreferences).toHaveBeenCalledExactlyOnceWith({ dateFormat: "ISO", transactionPageSize: 25 });
    expect(screen.getByTestId("canonical")).toHaveTextContent('"transactionPageSize":25');
    expect(screen.getByText("Preferences saved.")).toHaveFocus();
  });

  it("preserves selections and old context on failure; reset restores canonical preferences", async () => {
    const user = await setup("settings");
    await user.selectOptions(screen.getByLabelText("Date format"), "ISO");
    await user.selectOptions(screen.getByLabelText("Transactions per page"), "50");
    vi.mocked(accountService.updatePreferences).mockRejectedValue(new ApiError("bad", 400, { transactionPageSize: "Choose a supported size" }));
    await user.click(screen.getByRole("button", { name: "Save preferences" }));
    expect(screen.getByLabelText("Transactions per page")).toHaveValue("50");
    expect(screen.getByLabelText("Transactions per page")).toHaveFocus();
    expect(screen.getByLabelText("Transactions per page")).toHaveAccessibleDescription("Choose a supported size");
    expect(screen.getByTestId("canonical")).toHaveTextContent('"transactionPageSize":10');
    await user.click(screen.getByRole("button", { name: "Reset preferences" }));
    expect(screen.getByLabelText("Date format")).toHaveValue("MEDIUM");
    expect(screen.getByLabelText("Transactions per page")).toHaveValue("10");
  });

  it("prevents duplicate saves without blocking the separate password form", async () => {
    const user = await setup("settings");
    const pending = deferred<UserResponse>();
    vi.mocked(accountService.updatePreferences).mockReturnValue(pending.promise);
    await user.selectOptions(screen.getByLabelText("Date format"), "ISO");
    await user.click(screen.getByRole("button", { name: "Save preferences" }));
    expect(screen.getByRole("button", { name: "Saving preferences..." })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Change password" })).toBeEnabled();
    fireEvent.submit(screen.getByRole("form", { name: "Account preferences" }));
    expect(accountService.updatePreferences).toHaveBeenCalledTimes(1);
    await act(async () => pending.resolve({ ...accountUser, preferences: { dateFormat: "ISO", transactionPageSize: 10 } }));
  });
});

describe("Password form", () => {
  it.each([
    ["too short", "short", "short", "New password"],
    ["too many UTF-8 bytes", "界".repeat(25), "界".repeat(25), "New password"],
    ["mismatch", "a sufficiently long password", "different", "Confirm new password"],
    ["same password", "old password value", "old password value", "New password"],
    ["blank", " ".repeat(15), " ".repeat(15), "New password"],
    ["Unicode code points", "😀".repeat(8), "😀".repeat(8), "New password"],
  ])("rejects %s without a request", async (_reason, next, confirm, invalid) => {
    const user = await setup("settings");
    passwords("old password value", next, confirm);
    await user.click(screen.getByRole("button", { name: "Change password" }));
    expect(accountService.changePassword).not.toHaveBeenCalled();
    expect(screen.getByLabelText(invalid, { exact: true })).toHaveFocus();
    expect(screen.getByLabelText(invalid, { exact: true })).toHaveAttribute("aria-invalid", "true");
  });

  it.each(["  a new passphrase  ", "界".repeat(24), "😀".repeat(15)])("submits exact valid bytes, excludes confirmation, clears fields and keeps session", async next => {
    const user = await setup("settings");
    vi.mocked(accountService.changePassword).mockResolvedValue(undefined);
    passwords(" old password ", next);
    await user.click(screen.getByRole("button", { name: "Change password" }));
    expect(accountService.changePassword).toHaveBeenCalledExactlyOnceWith({ currentPassword: " old password ", newPassword: next });
    expect(screen.getByLabelText("Current password")).toHaveValue("");
    expect(screen.getByLabelText("New password", { exact: true })).toHaveValue("");
    expect(screen.getByLabelText("Confirm new password")).toHaveValue("");
    expect(screen.getByText("Password changed. You are still signed in.")).toHaveFocus();
    expect(screen.getByRole("button", { name: /account menu for River Walker/i })).toBeInTheDocument();
    expect(localStorage.getItem("fintrack_access_token")).toBe("test-session");
    expect(JSON.stringify(localStorage)).not.toContain(next);
    expect(sessionStorage.length).toBe(0);
  });

  it.each(["currentPassword", "newPassword"])("maps backend %s errors without signing out", async field => {
    const user = await setup("settings");
    passwords();
    vi.mocked(accountService.changePassword).mockRejectedValue(new ApiError("Validation", 400, { [field]: "Please choose another value" }));
    await user.click(screen.getByRole("button", { name: "Change password" }));
    const input = screen.getByLabelText(field === "currentPassword" ? "Current password" : "New password", { exact: true });
    expect(input).toHaveAccessibleDescription(/Please choose another value/);
    expect(input).toHaveFocus();
    expect(screen.getByRole("button", { name: /account menu for River Walker/i })).toBeInTheDocument();
  });

  it("toggles visibility with keyboard, preserving focus and input values", async () => {
    const user = await setup("settings");
    passwords();
    for (const label of ["current password", "new password", "confirm new password"]) {
      const toggle = screen.getByRole("button", { name: `Show ${label}` });
      toggle.focus();
      await user.keyboard(" ");
      expect(toggle).toHaveFocus();
      expect(toggle).toHaveAttribute("aria-pressed", "true");
      expect(toggle).toHaveAccessibleName(`Hide ${label}`);
      await user.keyboard(" ");
      expect(toggle).toHaveAttribute("aria-pressed", "false");
    }
    expect(screen.getByLabelText("Current password")).toHaveValue("old password value");
    expect(accountService.changePassword).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Current password")).toHaveAttribute("autocomplete", "current-password");
    expect(screen.getByLabelText("New password", { exact: true })).toHaveAttribute("autocomplete", "new-password");
  });

  it("requires current password and prevents repeated pending requests", async () => {
    const user = await setup("settings");
    passwords("");
    await user.click(screen.getByRole("button", { name: "Change password" }));
    expect(screen.getByLabelText("Current password")).toHaveFocus();
    expect(accountService.changePassword).not.toHaveBeenCalled();
    setField("Current password", "old password value");
    const pending = deferred<void>();
    vi.mocked(accountService.changePassword).mockReturnValue(pending.promise);
    await user.click(screen.getByRole("button", { name: "Change password" }));
    expect(screen.getByRole("button", { name: "Changing password..." })).toBeDisabled();
    fireEvent.submit(screen.getByRole("form", { name: "Change password" }));
    expect(accountService.changePassword).toHaveBeenCalledTimes(1);
    await act(async () => pending.reject(new Error("secret")));
    await waitFor(() => expect(screen.getByText("Unable to save changes. Please try again.")).toHaveFocus());
    expect(screen.queryByText("secret")).not.toBeInTheDocument();
  });
});

it("maps date-format errors and clears them on correction without clearing password entries", async () => {
  const user = await setup("settings");
  passwords();
  await user.selectOptions(screen.getByLabelText("Date format"), "ISO");
  vi.mocked(accountService.updatePreferences).mockRejectedValue(new ApiError("Validation", 400, { dateFormat: "Choose a supported date format" }));
  await user.click(screen.getByRole("button", { name: "Save preferences" }));
  expect(screen.getByLabelText("Date format")).toHaveAccessibleDescription("Choose a supported date format");
  expect(screen.getByLabelText("Date format")).toHaveFocus();
  await user.selectOptions(screen.getByLabelText("Date format"), "MEDIUM");
  expect(screen.getByLabelText("Date format")).toHaveAttribute("aria-invalid", "false");
  expect(screen.getByLabelText("Current password")).toHaveValue("old password value");
});

it("preserves a preference draft across canonical changes and resets to the newest values", async () => {
  const original = accountContext();
  const { rerender } = render(<AuthContext.Provider value={original}><AccountSettingsPage /></AuthContext.Provider>);
  await userEvent.selectOptions(screen.getByLabelText("Transactions per page"), "25");
  const updated = accountContext({ ...accountUser, preferences: { dateFormat: "ISO", transactionPageSize: 50 } });
  rerender(<AuthContext.Provider value={updated}><AccountSettingsPage /></AuthContext.Provider>);
  expect(screen.getByLabelText("Transactions per page")).toHaveValue("25");
  await userEvent.click(screen.getByRole("button", { name: "Reset preferences" }));
  expect(screen.getByLabelText("Transactions per page")).toHaveValue("50");
  expect(screen.getByLabelText("Date format")).toHaveValue("ISO");
});

it("updates password requirement checks using Unicode characters and UTF-8 bytes", async () => {
  await setup("settings");
  const rules = screen.getByRole("list", { name: "New password requirements" });
  const [minimum, maximum] = within(rules).getAllByRole("listitem");
  expect(minimum).toHaveTextContent("Not met");
  expect(maximum).toHaveTextContent("Not met");
  setField("New password", "😀".repeat(14));
  expect(minimum).not.toHaveClass("password-rule--met");
  expect(maximum).toHaveClass("password-rule--met");
  setField("New password", "😀".repeat(15));
  expect(minimum).toHaveClass("password-rule--met");
  expect(minimum).toHaveTextContent("✓");
  expect(minimum).toHaveTextContent("— Met");
  setField("New password", "界".repeat(25));
  expect(maximum).not.toHaveClass("password-rule--met");
  setField("New password", "界".repeat(24));
  expect(maximum).toHaveClass("password-rule--met");
  setField("New password", "");
  expect(minimum).not.toHaveClass("password-rule--met");
  expect(maximum).not.toHaveClass("password-rule--met");
  expect(screen.getByLabelText("New password", { exact: true })).toHaveAttribute("minlength", "15");
  expect(screen.getByLabelText("Confirm new password")).toHaveAttribute("minlength", "15");
  expect(screen.getByLabelText("Current password")).not.toHaveAttribute("minlength");
  expect(screen.queryByText(/Common passwords are rejected/)).not.toBeInTheDocument();
});

it("checks and unchecks circle indicators as requirements change", async () => {
  await setup("settings");
  const rules = screen.getByRole("list", { name: "New password requirements" });
  const [minimum, maximum] = within(rules).getAllByRole("listitem");
  const minimumCircle = minimum.querySelector(".password-rule__indicator");
  const maximumCircle = maximum.querySelector(".password-rule__indicator");
  expect(minimumCircle).toBeEmptyDOMElement();
  expect(maximumCircle).toBeEmptyDOMElement();
  expect(minimumCircle).toHaveAttribute("aria-hidden", "true");
  expect(maximumCircle).toHaveAttribute("aria-hidden", "true");
  expect(rules).toHaveAttribute("aria-live", "polite");

  setField("New password", "a".repeat(14));
  expect(minimumCircle).toBeEmptyDOMElement();
  expect(maximumCircle).toHaveTextContent("✓");
  setField("New password", "a".repeat(15));
  for (const rule of [minimum, maximum]) {
    expect(rule).toHaveClass("password-rule--met");
    expect(rule.querySelector(".password-rule__indicator")).toHaveTextContent("✓");
    expect(rule).toHaveTextContent("— Met");
  }
  setField("New password", "a".repeat(73));
  expect(minimumCircle).toHaveTextContent("✓");
  expect(maximumCircle).toBeEmptyDOMElement();
  expect(maximum).not.toHaveClass("password-rule--met");
  expect(maximum).toHaveTextContent("— Not met");
  setField("New password", "");
  for (const rule of [minimum, maximum]) {
    expect(rule.querySelector(".password-rule__indicator")).toBeEmptyDOMElement();
    expect(rule).not.toHaveClass("password-rule--met");
    expect(rule).toHaveTextContent("— Not met");
  }
  expect(accountService.changePassword).not.toHaveBeenCalled();
});

it("resets checked circles after a successful password change", async () => {
  const user = await setup("settings");
  vi.mocked(accountService.changePassword).mockResolvedValue(undefined);
  passwords();
  const rules = screen.getByRole("list", { name: "New password requirements" });
  expect(within(rules).getAllByText("✓")).toHaveLength(2);
  await user.click(screen.getByRole("button", { name: "Change password" }));
  expect(screen.getByText("Password changed. You are still signed in.")).toBeInTheDocument();
  for (const rule of within(rules).getAllByRole("listitem")) {
    expect(rule.querySelector(".password-rule__indicator")).toBeEmptyDOMElement();
    expect(rule).not.toHaveClass("password-rule--met");
    expect(rule).toHaveTextContent("— Not met");
  }
});

it("shows the read-only email hint on hover or keyboard focus and hides it when both leave", async () => {
  const user = await setup("profile");
  const email = screen.getByLabelText("Email");
  const hint = screen.getByText("Email cannot be changed here.");
  expect(hint).not.toBeVisible();
  expect(email).toHaveAttribute("readonly");
  expect(email).toHaveAccessibleDescription("Email cannot be changed here.");

  await user.hover(email);
  expect(hint).toBeVisible();
  await user.unhover(email);
  expect(hint).not.toBeVisible();

  screen.getByLabelText("Last name").focus();
  await user.tab();
  expect(email).toHaveFocus();
  expect(hint).toBeVisible();
  await user.keyboard("cannot edit");
  expect(email).toHaveValue(accountUser.email);

  await user.hover(email);
  await user.unhover(email);
  expect(hint).toBeVisible();
  await user.hover(email);
  await user.tab();
  expect(email).not.toHaveFocus();
  expect(hint).toBeVisible();
  await user.unhover(email);
  expect(hint).not.toBeVisible();
  expect(accountService.updateProfile).not.toHaveBeenCalled();
});

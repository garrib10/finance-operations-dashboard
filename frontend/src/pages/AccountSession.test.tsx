import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { AuthProvider } from "../context/AuthProvider";
import { useAuth } from "../context/AuthContext";
import { getCurrentUser, logoutSession, refreshSession } from "../services/authService";
import { clearAccessToken, getAccessToken } from "../utils/authToken";
import { accountUser } from "../test/accountFixtures";
import AccountSettingsPage from "./AccountSettingsPage";

vi.mock("../services/authService");

function Session() {
  const { user, sessionNotice } = useAuth();
  return user ? <AccountSettingsPage /> : <p>Signed out{sessionNotice ? `: ${sessionNotice}` : ""}</p>;
}

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { "Content-Type": "application/json" },
});

async function changePasswordWith(fetch: ReturnType<typeof vi.fn>) {
  vi.stubGlobal("fetch", fetch);
  render(<MemoryRouter><AuthProvider><Session /></AuthProvider></MemoryRouter>);
  await screen.findByLabelText("Current password");
  for (const [label, value] of [["Current password", "old password value"], ["New password", "new password value"], ["Confirm new password", "new password value"]]) {
    fireEvent.change(screen.getByLabelText(label, { exact: true }), { target: { value } });
  }
  await userEvent.click(screen.getByRole("button", { name: "Change password" }));
}

beforeEach(() => {
  vi.clearAllMocks();
  clearAccessToken();
  vi.mocked(refreshSession).mockResolvedValue({ accessToken: "restored-session", tokenType: "Bearer", expiresIn: 300 });
  vi.mocked(getCurrentUser).mockResolvedValue(accountUser);
});
afterEach(() => { vi.unstubAllGlobals(); localStorage.clear(); });

it("preserves centralized session invalidation for a genuine password endpoint 401", async () => {
  const fetch = vi.fn().mockResolvedValue(json({ message: "Unauthorized", code: "AUTHENTICATION_REQUIRED" }, 401));

  await changePasswordWith(fetch);

  await waitFor(() => expect(screen.getByText("Signed out")).toBeInTheDocument());
  expect(getAccessToken()).toBeNull();
  expect(fetch).toHaveBeenCalledOnce();
  expect(fetch).toHaveBeenCalledWith(expect.stringContaining("/api/account/password"), expect.objectContaining({
    method: "POST", body: JSON.stringify({ currentPassword: "old password value", newPassword: "new password value" }),
  }));
});

it("refreshes an expired token, retries the password change once, then signs out everywhere", async () => {
  vi.mocked(refreshSession)
    .mockResolvedValueOnce({ accessToken: "restored-session", tokenType: "Bearer", expiresIn: 300 })
    .mockResolvedValueOnce({ accessToken: "renewed-session", tokenType: "Bearer", expiresIn: 300 });
  const fetch = vi.fn()
    .mockResolvedValueOnce(json({ message: "Access token has expired", code: "ACCESS_TOKEN_EXPIRED" }, 401))
    .mockResolvedValueOnce(new Response(null, { status: 204 }));

  await changePasswordWith(fetch);

  expect(await screen.findByText("Signed out: Your password was changed. Please sign in again.")).toBeInTheDocument();
  expect(fetch).toHaveBeenCalledTimes(2);
  const [first, retry] = fetch.mock.calls.map(call => call[1] as RequestInit);
  expect(retry.body).toBe(first.body);
  expect((retry.headers as Record<string, string>).Authorization).toBe("Bearer renewed-session");
  expect(refreshSession).toHaveBeenCalledTimes(2);
  expect(logoutSession).not.toHaveBeenCalled();
  expect(getAccessToken()).toBeNull();
  expect(localStorage.length).toBe(0);
});

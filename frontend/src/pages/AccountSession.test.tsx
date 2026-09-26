import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, expect, it, vi } from "vitest";
import { AuthProvider } from "../context/AuthProvider";
import { useAuth } from "../context/AuthContext";
import { getCurrentUser } from "../services/authService";
import { getAuthToken, setAuthToken } from "../utils/authToken";
import { accountUser } from "../test/accountFixtures";
import AccountSettingsPage from "./AccountSettingsPage";

vi.mock("../services/authService");
function Session() {
  const { user } = useAuth();
  return user ? <AccountSettingsPage /> : <p>Signed out</p>;
}
afterEach(() => { vi.unstubAllGlobals(); localStorage.clear(); });

it("preserves centralized session invalidation for a genuine password endpoint 401", async () => {
  setAuthToken("expired-session");
  vi.mocked(getCurrentUser).mockResolvedValue(accountUser);
  const fetch = vi.fn().mockResolvedValue({ ok: false, status: 401, json: async () => ({ message: "Unauthorized" }) });
  vi.stubGlobal("fetch", fetch);
  render(<MemoryRouter><AuthProvider><Session /></AuthProvider></MemoryRouter>);
  await screen.findByLabelText("Current password");
  for (const [label, value] of [["Current password", "old password value"], ["New password", "new password value"], ["Confirm new password", "new password value"]]) {
    fireEvent.change(screen.getByLabelText(label, { exact: true }), { target: { value } });
  }
  await userEvent.click(screen.getByRole("button", { name: "Change password" }));
  await waitFor(() => expect(screen.getByText("Signed out")).toBeInTheDocument());
  expect(getAuthToken()).toBeNull();
  expect(fetch).toHaveBeenCalledWith(expect.stringContaining("/api/account/password"), expect.objectContaining({
    method: "POST", body: JSON.stringify({ currentPassword: "old password value", newPassword: "new password value" }),
  }));
});

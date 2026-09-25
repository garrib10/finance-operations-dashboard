import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { updateProfile, updatePreferences, changePassword } from "./accountService";
import { API_BASE_URL } from "./apiConfig";
import { accountUser } from "../test/accountFixtures";
import { getAuthToken, setAuthToken } from "../utils/authToken";

const fetchMock = vi.fn();
beforeEach(() => {
  fetchMock.mockReset();
  window.localStorage.clear();
  setAuthToken("account-token");
  vi.stubGlobal("fetch", fetchMock);
});
afterEach(() => vi.unstubAllGlobals());

describe("accountService", () => {
  it("updates a profile with only editable fields and parses the canonical user", async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify(accountUser)));
    const request = { firstName: "Demo", lastName: "User", displayName: "River Walker", id: 2, email: "other@example.com", role: "ADMIN" };
    await expect(updateProfile(request)).resolves.toEqual(accountUser);
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE_URL}/api/account/profile`, {
      method: "PUT",
      headers: { "Content-Type": "application/json", Authorization: "Bearer account-token" },
      body: JSON.stringify({ firstName: "Demo", lastName: "User", displayName: "River Walker" }),
    });
  });

  it("updates preferences without sending ownership fields", async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify(accountUser)));
    const request = { dateFormat: "ISO" as const, transactionPageSize: 25 as const, id: 2, createdAt: "unexpected" };
    await expect(updatePreferences(request)).resolves.toEqual(accountUser);
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE_URL}/api/account/preferences`, expect.objectContaining({
      method: "PUT", body: JSON.stringify({ dateFormat: "ISO", transactionPageSize: 25 }),
      headers: { "Content-Type": "application/json", Authorization: "Bearer account-token" },
    }));
  });

  it("preserves exact passwords and accepts an empty 204 without parsing JSON", async () => {
    const response = new Response(null, { status: 204 });
    const json = vi.spyOn(response, "json");
    fetchMock.mockResolvedValue(response);
    await expect(changePassword({ currentPassword: " old password ", newPassword: " new long password " })).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE_URL}/api/account/password`, expect.objectContaining({
      method: "POST", body: JSON.stringify({ currentPassword: " old password ", newPassword: " new long password " }),
    }));
    expect(json).not.toHaveBeenCalled();
    expect(getAuthToken()).toBe("account-token");
    expect(window.localStorage.length).toBe(1);
    expect(window.sessionStorage.length).toBe(0);
  });

  it.each([400, 401, 500])("propagates status %s through the existing API client", async (status) => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ fields: { currentPassword: "Current password is incorrect" } }), { status }));
    await expect(changePassword({ currentPassword: "wrong", newPassword: "new long password" })).rejects.toMatchObject({
      status, validationErrors: { currentPassword: "Current password is incorrect" },
    });
    expect(getAuthToken()).toBe(status === 401 ? null : "account-token");
  });
});

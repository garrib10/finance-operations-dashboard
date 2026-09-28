import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { updateProfile, updatePreferences, changePassword, uploadProfilePhoto, removeProfilePhoto } from "./accountService";
import { API_BASE_URL } from "./apiConfig";
import { accountUser, photoUrl } from "../test/accountFixtures";
import { clearAccessToken, getAccessToken, setAccessToken } from "../utils/authToken";
import * as authService from "./authService";

const fetchMock = vi.fn();
beforeEach(() => {
  fetchMock.mockReset();
  window.localStorage.clear();
  clearAccessToken();
  setAccessToken("account-token");
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
    expect(getAccessToken()).toBe("account-token");
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });

  it.each([400, 401, 500])("propagates status %s through the existing API client", async (status) => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ fields: { currentPassword: "Current password is incorrect" } }), { status }));
    await expect(changePassword({ currentPassword: "wrong", newPassword: "new long password" })).rejects.toMatchObject({
      status, validationErrors: { currentPassword: "Current password is incorrect" },
    });
    expect(getAccessToken()).toBe(status === 401 ? null : "account-token");
  });

  it("uploads a photo as FormData with only the photo part and lets the browser set the boundary", async () => {
    const photoUser = { ...accountUser, profilePhotoUrl: photoUrl };
    fetchMock.mockResolvedValue(new Response(JSON.stringify(photoUser)));
    const photo = new File(["jpeg"], "me.jpg", { type: "image/jpeg" });
    await expect(uploadProfilePhoto(photo)).resolves.toEqual(photoUser);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`${API_BASE_URL}/api/account/photo`);
    expect(init.method).toBe("PUT");
    expect(init.headers).toEqual({ Authorization: "Bearer account-token" });
    expect(init.body).toBeInstanceOf(FormData);
    const body = init.body as FormData;
    expect([...body.keys()]).toEqual(["photo"]);
    expect(body.get("photo")).toBe(photo);
    expect(JSON.stringify(init)).not.toMatch(/profilePhotoKey|cloudinary|api_key|api_secret/i);
  });

  it("removes a photo without sending a URL or key and parses the canonical user", async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify(accountUser)));
    await expect(removeProfilePhoto()).resolves.toEqual(accountUser);
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE_URL}/api/account/photo`, {
      method: "DELETE",
      headers: { "Content-Type": "application/json", Authorization: "Bearer account-token" },
    });
  });

  it.each([
    [400, "Animated and multiple-image files are not supported."],
    [413, "The photo exceeds the allowed file size."],
    [415, "Choose a static JPEG or PNG image."],
    [503, "Profile photos are temporarily unavailable. Please try again later."],
  ])("propagates photo status %s through the existing ApiError flow", async (status, message) => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ timestamp: "t", status, error: "Error", message }), { status }));
    await expect(uploadProfilePhoto(new File(["x"], "x.png", { type: "image/png" }))).rejects.toMatchObject({ name: "ApiError", status, message });
    expect(getAccessToken()).toBe("account-token");
  });

  it("invalidates the session through the existing 401 handling on photo removal", async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ message: "Authentication is required" }), { status: 401 }));
    await expect(removeProfilePhoto()).rejects.toMatchObject({ status: 401 });
    expect(getAccessToken()).toBeNull();
  });

  it("turns a proxy's non-JSON photo error into a safe ApiError without the HTML", async () => {
    fetchMock.mockResolvedValue(new Response("<html>413 Request Entity Too Large</html>", { status: 413 }));
    const error = await uploadProfilePhoto(new File(["x"], "x.jpg", { type: "image/jpeg" })).catch((caught: unknown) => caught);
    expect(error).toMatchObject({ name: "ApiError", status: 413, message: "An unexpected error occurred." });
    expect(String((error as Error).message)).not.toContain("<html>");
    expect(getAccessToken()).toBe("account-token");
  });

  it("replays the same FormData photo once after an expired access token is refreshed", async () => {
    const refresh = vi.spyOn(authService, "refreshSession")
      .mockResolvedValue({ accessToken: "renewed-token", tokenType: "Bearer", expiresIn: 300 });
    const photoUser = { ...accountUser, profilePhotoUrl: photoUrl };
    fetchMock
      .mockResolvedValueOnce(new Response(JSON.stringify({ message: "Access token has expired", code: "ACCESS_TOKEN_EXPIRED" }), { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(photoUser)));
    const photo = new File(["jpeg"], "me.jpg", { type: "image/jpeg" });

    await expect(uploadProfilePhoto(photo)).resolves.toEqual(photoUser);

    expect(refresh).toHaveBeenCalledOnce();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    const [first, retry] = fetchMock.mock.calls.map(call => call[1] as RequestInit);
    expect(first.headers).toEqual({ Authorization: "Bearer account-token" });
    expect(retry.headers).toEqual({ Authorization: "Bearer renewed-token" });
    expect(retry.body).toBe(first.body);
    expect((retry.body as FormData).get("photo")).toBe(photo);
    expect(retry.headers).not.toHaveProperty("Content-Type");
    expect(getAccessToken()).toBe("renewed-token");
    refresh.mockRestore();
  });
});

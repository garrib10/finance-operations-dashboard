import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../context/AuthProvider";
import AppHeader from "../components/AppHeader";
import * as accountService from "../services/accountService";
import * as authService from "../services/authService";
import { ApiError } from "../services/api";
import { invalidateAuthSession } from "../services/authSession";
import { setAuthToken } from "../utils/authToken";
import { MAX_PHOTO_BYTES } from "../utils/profilePhoto";
import { accountUser, deferred, photoUrl, replacementPhotoUrl } from "../test/accountFixtures";
import type { UserResponse } from "../types/auth";
import AccountSettingsPage from "./AccountSettingsPage";

vi.mock("../services/accountService");
vi.mock("../services/authService");

const upload = vi.mocked(accountService.uploadProfilePhoto);
const remove = vi.mocked(accountService.removeProfilePhoto);
const photoUser: UserResponse = { ...accountUser, profilePhotoUrl: photoUrl };
const jpeg = () => new File(["jpeg-bytes"], "me.jpg", { type: "image/jpeg" });
const png = () => new File(["png-bytes"], "me.png", { type: "image/png" });

let objectUrlCount = 0;
const createObjectURL = vi.fn((file: Blob) => `blob:preview-${++objectUrlCount}-${(file as File).name}`);
const revokeObjectURL = vi.fn();

beforeEach(() => {
  vi.resetAllMocks();
  localStorage.clear();
  objectUrlCount = 0;
  createObjectURL.mockImplementation((file: Blob) => `blob:preview-${++objectUrlCount}-${(file as File).name}`);
  Object.assign(URL, { createObjectURL, revokeObjectURL });
});
afterEach(() => {
  Reflect.deleteProperty(URL, "createObjectURL");
  Reflect.deleteProperty(URL, "revokeObjectURL");
});

async function setup(user: UserResponse = accountUser) {
  setAuthToken("test-session");
  vi.mocked(authService.getCurrentUser).mockResolvedValue(user);
  const view = render(<MemoryRouter><AuthProvider><AppHeader /><AccountSettingsPage /></AuthProvider></MemoryRouter>);
  await screen.findByRole("button", { name: /account menu for River Walker/i });
  return { ...view, events: userEvent.setup() };
}

const section = () => screen.getByRole("form", { name: "Profile photo" });
const input = () => within(section()).getByLabelText("Choose a photo") as HTMLInputElement;
const status = () => within(section()).getByRole("status");
const alert = () => within(section()).getByRole("alert");
const headerImage = () => document.querySelector(".account-menu__avatar img");
const preview = () => screen.queryByRole("img", { name: "Preview of selected photo" });
const choose = (...files: File[]) => fireEvent.change(input(), { target: { files } });

describe("Account settings profile photo", () => {
  it("shows initials, guidance, and an upload action when no photo is stored", async () => {
    await setup();
    expect(within(section()).getByRole("heading", { name: "Profile photo" })).toBeInTheDocument();
    expect(within(section()).getByRole("img", { name: "Initials for River Walker" })).toHaveTextContent("RW");
    expect(input()).toHaveAttribute("type", "file");
    expect(input()).toHaveAttribute("accept", "image/jpeg,image/png");
    expect(input()).toHaveAccessibleDescription("Upload a JPEG or PNG image up to 2 MB.");
    expect(within(section()).getByRole("button", { name: "Upload photo" })).toBeEnabled();
    expect(within(section()).queryByRole("button", { name: "Remove photo" })).not.toBeInTheDocument();
    expect(section()).not.toHaveTextContent(/cloudinary/i);
  });

  it("shows the stored photo with replace and remove actions", async () => {
    await setup(photoUser);
    expect(within(section()).getByRole("img", { name: "Profile photo for River Walker" })).toHaveAttribute("src", photoUrl);
    expect(headerImage()).toHaveAttribute("src", photoUrl);
    expect(within(section()).getByRole("button", { name: "Replace photo" })).toBeInTheDocument();
    expect(within(section()).getByRole("button", { name: "Remove photo" })).toHaveClass("button--danger");
    expect(section()).not.toHaveTextContent(photoUrl);
  });

  it("falls back to initials when the stored photo fails to load", async () => {
    await setup(photoUser);
    fireEvent.error(within(section()).getByRole("img", { name: "Profile photo for River Walker" }));
    expect(within(section()).getByRole("img", { name: "Initials for River Walker" })).toBeInTheDocument();
    expect(within(section()).getByRole("button", { name: "Remove photo" })).toBeInTheDocument();
  });

  it.each([["JPEG", jpeg], ["PNG", png]])("previews a selected %s image without uploading it", async (_type, file) => {
    await setup();
    const selected = file();
    choose(selected);
    expect(createObjectURL).toHaveBeenCalledExactlyOnceWith(selected);
    expect(preview()).toHaveAttribute("src", `blob:preview-1-${selected.name}`);
    expect(within(section()).getByRole("img", { name: "Initials for River Walker" })).toBeInTheDocument();
    expect(upload).not.toHaveBeenCalled();
  });

  it.each([
    ["an unsupported type", new File(["gif"], "a.gif", { type: "image/gif" }), "Choose a JPEG or PNG image."],
    ["a file over 2 MiB", new File([new Uint8Array(MAX_PHOTO_BYTES + 1)], "big.jpg", { type: "image/jpeg" }), "The photo is too large. Choose an image up to 2 MB."],
  ])("rejects %s before any request", async (_label, file, message) => {
    const { events } = await setup();
    choose(file);
    expect(alert()).toHaveTextContent(message);
    expect(alert()).toHaveFocus();
    expect(input()).toHaveAttribute("aria-invalid", "true");
    expect(input()).toHaveAccessibleDescription(`Upload a JPEG or PNG image up to 2 MB. ${message}`);
    expect(preview()).toBeNull();
    expect(createObjectURL).not.toHaveBeenCalled();
    await events.click(within(section()).getByRole("button", { name: "Upload photo" }));
    expect(upload).not.toHaveBeenCalled();
  });

  it("accepts a file of exactly 2 MiB", async () => {
    await setup();
    choose(new File([new Uint8Array(MAX_PHOTO_BYTES)], "limit.png", { type: "image/png" }));
    expect(preview()).toBeInTheDocument();
    expect(alert()).toBeEmptyDOMElement();
  });

  it("asks for a selection instead of submitting an empty upload", async () => {
    const { events } = await setup();
    await events.click(within(section()).getByRole("button", { name: "Upload photo" }));
    expect(alert()).toHaveTextContent("Choose a JPEG or PNG image to upload.");
    expect(upload).not.toHaveBeenCalled();
  });

  it("replaces an earlier selection, revokes its preview, and clears stale validation", async () => {
    await setup();
    choose(new File(["gif"], "a.gif", { type: "image/gif" }));
    expect(alert()).not.toBeEmptyDOMElement();
    choose(jpeg());
    expect(alert()).toBeEmptyDOMElement();
    expect(input()).toHaveAttribute("aria-invalid", "false");
    choose(png());
    expect(revokeObjectURL).toHaveBeenCalledExactlyOnceWith("blob:preview-1-me.jpg");
    expect(preview()).toHaveAttribute("src", "blob:preview-2-me.png");
  });

  it("clears the preview without a request when file selection is cancelled", async () => {
    await setup(photoUser);
    choose(jpeg());
    choose();
    expect(preview()).toBeNull();
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:preview-1-me.jpg");
    expect(within(section()).getByRole("img", { name: "Profile photo for River Walker" })).toHaveAttribute("src", photoUrl);
    expect(upload).not.toHaveBeenCalled();
  });

  it("revokes the active preview on unmount", async () => {
    const { unmount } = await setup();
    choose(jpeg());
    unmount();
    expect(revokeObjectURL).toHaveBeenCalledExactlyOnceWith("blob:preview-1-me.jpg");
  });

  it("keeps the stored photo when a preview cannot be displayed", async () => {
    await setup(photoUser);
    choose(jpeg());
    fireEvent.error(preview()!);
    expect(within(section()).getByText("Preview unavailable")).toBeInTheDocument();
    expect(within(section()).getByRole("img", { name: "Profile photo for River Walker" })).toHaveAttribute("src", photoUrl);
  });

  it("uploads once, announces progress, and synchronizes the header and settings", async () => {
    const { events } = await setup();
    const pending = deferred<UserResponse>();
    upload.mockReturnValue(pending.promise);
    const selected = jpeg();
    choose(selected);
    await events.click(within(section()).getByRole("button", { name: "Upload photo" }));
    const busyButton = within(section()).getByRole("button", { name: "Uploading…" });
    expect(busyButton).toBeDisabled();
    expect(input()).toBeDisabled();
    expect(status()).toHaveTextContent("Uploading photo…");
    fireEvent.submit(section());
    expect(upload).toHaveBeenCalledExactlyOnceWith(selected);
    expect(screen.getByLabelText("Date format")).toBeEnabled();
    expect(headerImage()).toBeNull();

    await act(async () => pending.resolve(photoUser));
    expect(headerImage()).toHaveAttribute("src", photoUrl);
    expect(within(section()).getByRole("img", { name: "Profile photo for River Walker" })).toHaveAttribute("src", photoUrl);
    expect(status()).toHaveTextContent("Profile photo uploaded.");
    expect(status()).toHaveFocus();
    expect(preview()).toBeNull();
    expect(input().value).toBe("");
    expect(revokeObjectURL).toHaveBeenCalledExactlyOnceWith("blob:preview-1-me.jpg");
    expect(within(section()).getByRole("button", { name: "Replace photo" })).toBeEnabled();
    expect(within(section()).getByRole("button", { name: "Remove photo" })).toBeEnabled();
  });

  it("replaces a stored photo everywhere after success", async () => {
    const { events } = await setup(photoUser);
    const pending = deferred<UserResponse>();
    upload.mockReturnValue(pending.promise);
    choose(png());
    await events.click(within(section()).getByRole("button", { name: "Replace photo" }));
    expect(within(section()).getByRole("button", { name: "Replacing…" })).toBeDisabled();
    expect(within(section()).getByRole("button", { name: "Remove photo" })).toBeDisabled();
    expect(status()).toHaveTextContent("Replacing photo…");
    expect(headerImage()).toHaveAttribute("src", photoUrl);
    await act(async () => pending.resolve({ ...accountUser, profilePhotoUrl: replacementPhotoUrl }));
    expect(headerImage()).toHaveAttribute("src", replacementPhotoUrl);
    expect(within(section()).getByRole("img", { name: "Profile photo for River Walker" })).toHaveAttribute("src", replacementPhotoUrl);
    expect(status()).toHaveTextContent("Profile photo replaced.");
  });

  it("preserves the stored photo and the retryable preview when upload fails", async () => {
    const { events } = await setup(photoUser);
    upload.mockRejectedValueOnce(new ApiError("Profile photos are temporarily unavailable.", 503));
    choose(jpeg());
    await events.click(within(section()).getByRole("button", { name: "Replace photo" }));
    expect(alert()).toHaveTextContent("Profile photos are temporarily unavailable. Please try again later.");
    expect(alert()).toHaveFocus();
    expect(status()).toBeEmptyDOMElement();
    expect(input()).toHaveAttribute("aria-invalid", "false");
    expect(headerImage()).toHaveAttribute("src", photoUrl);
    expect(within(section()).getByRole("img", { name: "Profile photo for River Walker" })).toHaveAttribute("src", photoUrl);
    expect(preview()).toHaveAttribute("src", "blob:preview-1-me.jpg");
    expect(revokeObjectURL).not.toHaveBeenCalled();

    upload.mockResolvedValueOnce({ ...accountUser, profilePhotoUrl: replacementPhotoUrl });
    await events.click(within(section()).getByRole("button", { name: "Replace photo" }));
    expect(alert()).toBeEmptyDOMElement();
    expect(headerImage()).toHaveAttribute("src", replacementPhotoUrl);
  });

  it("shows the sanitized backend message for an invalid image", async () => {
    const { events } = await setup();
    upload.mockRejectedValue(new ApiError("Animated and multiple-image files are not supported.", 400));
    choose(png());
    await events.click(within(section()).getByRole("button", { name: "Upload photo" }));
    expect(alert()).toHaveTextContent("Animated and multiple-image files are not supported.");
  });

  it("follows the existing session-expiration flow on 401", async () => {
    const { events } = await setup();
    upload.mockImplementation(async () => {
      invalidateAuthSession();
      throw new ApiError("Authentication is required", 401);
    });
    choose(jpeg());
    await events.click(within(section()).getByRole("button", { name: "Upload photo" }));
    expect(await screen.findByRole("link", { name: "Login" })).toBeInTheDocument();
    expect(screen.queryByRole("form", { name: "Profile photo" })).not.toBeInTheDocument();
  });

  it("tolerates an upload that completes after the page unmounts", async () => {
    const { events, unmount } = await setup();
    const pending = deferred<UserResponse>();
    upload.mockReturnValue(pending.promise);
    choose(jpeg());
    await events.click(within(section()).getByRole("button", { name: "Upload photo" }));
    unmount();
    await act(async () => pending.resolve(photoUser));
    expect(revokeObjectURL).toHaveBeenCalledExactlyOnceWith("blob:preview-1-me.jpg");
  });

  it("does not remove the photo when confirmation is cancelled", async () => {
    const { events } = await setup(photoUser);
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    await events.click(within(section()).getByRole("button", { name: "Remove photo" }));
    expect(confirm).toHaveBeenCalledWith("Remove your profile photo?");
    expect(remove).not.toHaveBeenCalled();
    expect(headerImage()).toHaveAttribute("src", photoUrl);
    confirm.mockRestore();
  });

  it("removes once after keyboard confirmation and restores initials everywhere", async () => {
    const { events } = await setup(photoUser);
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const pending = deferred<UserResponse>();
    remove.mockReturnValue(pending.promise);
    choose(jpeg());
    within(section()).getByRole("button", { name: "Remove photo" }).focus();
    await events.keyboard("{Enter}");
    const busyButton = within(section()).getByRole("button", { name: "Removing…" });
    expect(busyButton).toBeDisabled();
    expect(within(section()).getByRole("button", { name: "Replace photo" })).toBeDisabled();
    expect(input()).toBeDisabled();
    expect(status()).toHaveTextContent("Removing photo…");
    fireEvent.click(busyButton);
    fireEvent.submit(section());
    expect(remove).toHaveBeenCalledExactlyOnceWith();
    expect(upload).not.toHaveBeenCalled();

    await act(async () => pending.resolve(accountUser));
    expect(headerImage()).toBeNull();
    expect(screen.getByRole("button", { name: /account menu for River Walker/ })).toHaveTextContent("RW");
    expect(within(section()).getByRole("img", { name: "Initials for River Walker" })).toBeInTheDocument();
    expect(within(section()).queryByRole("button", { name: "Remove photo" })).not.toBeInTheDocument();
    expect(within(section()).getByRole("button", { name: "Upload photo" })).toBeEnabled();
    expect(status()).toHaveTextContent("Profile photo removed.");
    expect(status()).toHaveFocus();
    expect(preview()).toBeNull();
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:preview-1-me.jpg");
  });

  it("preserves the current photo when removal fails", async () => {
    const { events } = await setup(photoUser);
    vi.spyOn(window, "confirm").mockReturnValue(true);
    remove.mockRejectedValue(new TypeError("Failed to fetch"));
    await events.click(within(section()).getByRole("button", { name: "Remove photo" }));
    expect(alert()).toHaveTextContent("Unable to remove your photo. Please try again.");
    expect(headerImage()).toHaveAttribute("src", photoUrl);
    expect(within(section()).getByRole("button", { name: "Remove photo" })).toBeEnabled();
  });

  it("clears a stale success message when a new photo operation starts", async () => {
    const { events } = await setup();
    upload.mockResolvedValueOnce(photoUser);
    choose(jpeg());
    await events.click(within(section()).getByRole("button", { name: "Upload photo" }));
    expect(status()).toHaveTextContent("Profile photo uploaded.");
    choose(png());
    expect(status()).toBeEmptyDOMElement();
  });

  it("keeps photo and preference feedback independent", async () => {
    const { events } = await setup(photoUser);
    vi.mocked(accountService.updatePreferences).mockRejectedValue(new Error("offline"));
    await events.selectOptions(screen.getByLabelText("Date format"), "ISO");
    await events.click(screen.getByRole("button", { name: "Save preferences" }));
    const preferences = screen.getByRole("form", { name: "Account preferences" });
    await waitFor(() => expect(within(preferences).getByRole("alert")).toHaveTextContent("Unable to save changes."));
    expect(alert()).toBeEmptyDOMElement();
    expect(headerImage()).toHaveAttribute("src", photoUrl);
  });
});

describe("photo preservation across other account changes", () => {
  it("keeps the photo after preference and password saves", async () => {
    const { events } = await setup(photoUser);
    vi.mocked(accountService.updatePreferences).mockResolvedValue({ ...photoUser, preferences: { dateFormat: "ISO", transactionPageSize: 10 } });
    vi.mocked(accountService.changePassword).mockResolvedValue(undefined);
    await events.selectOptions(screen.getByLabelText("Date format"), "ISO");
    await events.click(screen.getByRole("button", { name: "Save preferences" }));
    expect(await screen.findByText("Preferences saved.")).toBeInTheDocument();
    expect(headerImage()).toHaveAttribute("src", photoUrl);

    fireEvent.change(screen.getByLabelText("Current password", { exact: true }), { target: { value: "old password value" } });
    fireEvent.change(screen.getByLabelText("New password", { exact: true }), { target: { value: "new password value" } });
    fireEvent.change(screen.getByLabelText("Confirm new password", { exact: true }), { target: { value: "new password value" } });
    await events.click(screen.getByRole("button", { name: "Change password" }));
    expect(await screen.findByText("Password changed. You are still signed in.")).toBeInTheDocument();
    expect(headerImage()).toHaveAttribute("src", photoUrl);
    expect(within(section()).getByRole("img", { name: "Profile photo for River Walker" })).toBeInTheDocument();
  });
});

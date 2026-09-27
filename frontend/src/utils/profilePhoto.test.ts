import { describe, expect, it } from "vitest";
import { ApiError } from "../services/api";
import { MAX_PHOTO_BYTES, photoErrorMessage } from "./profilePhoto";

describe("photoErrorMessage", () => {
  it("limits client uploads to 2 MiB", () => {
    expect(MAX_PHOTO_BYTES).toBe(2_097_152);
  });

  it.each([
    [new ApiError("The image exceeds the allowed dimensions or pixel count.", 400), "The image exceeds the allowed dimensions or pixel count."],
    [new ApiError("Validation failed.", 400, { photo: "bad" }), "The image could not be processed. Choose another JPEG or PNG image."],
    [new ApiError("", 400), "The image could not be processed. Choose another JPEG or PNG image."],
    [new ApiError("Unauthorized", 401), "Your session has expired. Please sign in again."],
    [new ApiError("The photo or multipart request exceeds the allowed size.", 413), "The photo is too large. Choose a JPEG or PNG image up to 2 MB."],
    [new ApiError("Use the supported content type", 415), "Only JPEG and PNG images are supported."],
    [new ApiError("Profile photos are temporarily unavailable.", 503), "Profile photos are temporarily unavailable. Please try again later."],
    [new ApiError("Internal detail", 500), "Unable to upload your photo. Please try again."],
    [new TypeError("Failed to fetch"), "Unable to upload your photo. Please try again."],
  ])("maps %s to a safe message", (error, message) => {
    expect(photoErrorMessage(error, "upload")).toBe(message);
  });

  it("names the failed removal in the generic message", () => {
    expect(photoErrorMessage(new Error("boom"), "remove")).toBe("Unable to remove your photo. Please try again.");
  });
});

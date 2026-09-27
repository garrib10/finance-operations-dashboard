import { ApiError } from "../services/api";

export const MAX_PHOTO_BYTES = 2 * 1024 * 1024;
export const ACCEPTED_PHOTO_TYPES = ["image/jpeg", "image/png"];

export function photoErrorMessage(error: unknown, action: "upload" | "remove"): string {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 400:
        // Photo 400s carry sanitized, user-facing messages; field-validation bodies do not.
        return error.validationErrors || !error.message
          ? "The image could not be processed. Choose another JPEG or PNG image."
          : error.message;
      case 401: return "Your session has expired. Please sign in again.";
      case 413: return "The photo is too large. Choose a JPEG or PNG image up to 2 MB.";
      case 415: return "Only JPEG and PNG images are supported.";
      case 503: return "Profile photos are temporarily unavailable. Please try again later.";
    }
  }
  return `Unable to ${action} your photo. Please try again.`;
}

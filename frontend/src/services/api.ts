import { API_BASE_URL } from "./apiConfig";
import { getAuthToken } from "../utils/authToken";
import type { ApiErrorResponse, ValidationErrorResponse } from "../types/api";

export class ApiError extends Error {
  status: number;
  validationErrors?: Record<string, string>;

  constructor(
    message: string,
    status: number,
    validationErrors?: Record<string, string>,
  ) {
    super(message);

    this.name = "ApiError";
    this.status = status;
    this.validationErrors = validationErrors;
  }
}

export async function apiRequest<T>(
  endpoint: string,
  options: RequestInit = {},
): Promise<T> {
  const token = getAuthToken();

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token
        ? {
            Authorization: `Bearer ${token}`,
          }
        : {}),
      ...options.headers,
    },
  });

  if (!response.ok) {
    const errorBody = (await response.json()) as
      | ApiErrorResponse
      | ValidationErrorResponse;

    if ("fields" in errorBody) {
      throw new ApiError(
        "Validation failed.",
        response.status,
        errorBody.fields,
      );
    }

    throw new ApiError(
      errorBody.message ?? "An unexpected error occurred.",
      response.status,
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

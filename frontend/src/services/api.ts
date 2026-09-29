import { API_BASE_URL } from "./apiConfig";
import { getSessionGeneration, invalidateAuthSession } from "./authSession";
import { getAccessToken } from "../utils/authToken";
import { renewAccessToken } from "./sessionRefresh";

export class ApiError extends Error {
  status: number;
  validationErrors?: Record<string, string>;
  /** Stable machine-readable backend code, such as ACCESS_TOKEN_EXPIRED. */
  code?: string;

  constructor(
    message: string,
    status: number,
    validationErrors?: Record<string, string>,
    code?: string,
  ) {
    super(message);

    this.name = "ApiError";
    this.status = status;
    this.validationErrors = validationErrors;
    this.code = code;
  }
}

export interface ApiRequestOptions extends RequestInit {
  /** Attach the in-memory bearer token (default). Public endpoints pass false. */
  authenticated?: boolean;
  /** Refresh and retry once on ACCESS_TOKEN_EXPIRED (default: authenticated). */
  retryOnExpiredToken?: boolean;
}

export const ACCESS_TOKEN_EXPIRED = "ACCESS_TOKEN_EXPIRED";
export const CSRF_HEADER = "X-FinTrack-CSRF";

const DEFAULT_ERROR_MESSAGE = "An unexpected error occurred.";
const MALFORMED_RESPONSE_MESSAGE = "The server returned an unexpected response.";

/**
 * Reads an error body as JSON when it is JSON; never throws. HTML and plain-text
 * bodies (proxy pages, gateway errors) are ignored rather than shown to users.
 */
async function readJsonBody(response: Response): Promise<unknown> {
  let text: string;
  try {
    text = await response.text();
  } catch {
    return undefined;
  }
  try {
    return text ? JSON.parse(text) : undefined;
  } catch {
    return undefined;
  }
}

/** Builds an ApiError from trusted JSON fields only; HTML or text bodies are never shown. */
async function toApiError(response: Response): Promise<ApiError> {
  const body = await readJsonBody(response);
  const fields = body !== null && typeof body === "object" ? body as Record<string, unknown> : {};
  const code = typeof fields.code === "string" ? fields.code : undefined;

  if (fields.fields !== null && typeof fields.fields === "object") {
    return new ApiError("Validation failed.", response.status, fields.fields as Record<string, string>, code);
  }

  const message = typeof fields.message === "string" && fields.message.trim()
    ? fields.message
    : DEFAULT_ERROR_MESSAGE;
  return new ApiError(message, response.status, undefined, code);
}

async function readSuccess<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  if (!text) {
    return undefined as T;
  }
  try {
    return JSON.parse(text) as T;
  } catch {
    throw new ApiError(MALFORMED_RESPONSE_MESSAGE, response.status);
  }
}

/** Bodies the browser can send again unchanged. Streams are consumed by the first send. */
function isReplayable(body: RequestInit["body"]): boolean {
  return body === undefined
    || body === null
    || typeof body === "string"
    || body instanceof FormData
    || body instanceof URLSearchParams
    || body instanceof Blob
    || body instanceof ArrayBuffer
    || ArrayBuffer.isView(body);
}

function send(endpoint: string, init: RequestInit, token: string | null): Promise<Response> {
  return fetch(`${API_BASE_URL}${endpoint}`, {
    ...init,
    headers: {
      // Browsers must generate the multipart boundary for FormData bodies.
      ...(init.body instanceof FormData
        ? {}
        : { "Content-Type": "application/json" }),
      ...(token
        ? {
            Authorization: `Bearer ${token}`,
          }
        : {}),
      ...init.headers,
    },
  });
}

/**
 * Login, refresh, and logout: credentialed (the HttpOnly refresh cookie travels
 * with them), protected by the custom header, never bearer-authenticated, and never
 * refreshed or retried automatically.
 */
export async function authEndpointRequest<T>(endpoint: string, init: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...init,
    credentials: "include",
    headers: {
      ...(init.body ? { "Content-Type": "application/json" } : {}),
      [CSRF_HEADER]: "1",
    },
  });

  if (!response.ok) {
    throw await toApiError(response);
  }
  return readSuccess<T>(response);
}

export async function apiRequest<T>(
  endpoint: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  const { authenticated = true, retryOnExpiredToken = authenticated, ...init } = options;

  const token = authenticated ? getAccessToken() : null;
  const generation = getSessionGeneration();
  let response = await send(endpoint, init, token);

  if (response.status === 401 && authenticated) {
    const error = await toApiError(response);
    const canRetry = error.code === ACCESS_TOKEN_EXPIRED
      && token !== null
      && retryOnExpiredToken
      && isReplayable(init.body)
      && !init.signal?.aborted;

    if (!canRetry) {
      // An expired token that cannot be retried (aborted, opted out, streamed body)
      // is not proof the session is invalid; the next eligible request refreshes it.
      if (error.code !== ACCESS_TOKEN_EXPIRED && token === getAccessToken()) {
        invalidateAuthSession("INVALID");
      }
      throw error;
    }

    // The request was rejected by authentication before any handler ran, so it is
    // safe to send once more with a renewed token. The retry never refreshes again.
    const renewed = await renewAccessToken(token, generation);
    response = await send(endpoint, init, renewed);

    if (response.status === 401) {
      const retryError = await toApiError(response);
      if (renewed === getAccessToken()) {
        invalidateAuthSession("INVALID");
      }
      throw retryError;
    }
  }

  if (!response.ok) {
    throw await toApiError(response);
  }

  return readSuccess<T>(response);
}

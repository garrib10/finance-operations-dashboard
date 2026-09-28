export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  /** Stable machine-readable code, e.g. ACCESS_TOKEN_EXPIRED or SESSION_EXPIRED. */
  code?: string;
}

export interface ValidationErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  fields: Record<string, string>;
}

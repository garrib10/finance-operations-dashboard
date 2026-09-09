export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
}

export interface ValidationErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  fields: Record<string, string>;
}

import { apiRequest } from "./api";

export interface HealthResponse {
  status: string;
  application: string;
}

export function getHealth(): Promise<HealthResponse> {
  return apiRequest<HealthResponse>("/api/health");
}

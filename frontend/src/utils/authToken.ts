const TOKEN_KEY = "fintrack_access_token";

export function getAuthToken(): string | null {
  return window.localStorage.getItem(TOKEN_KEY);
}

export function setAuthToken(token: string): void {
  window.localStorage.setItem(TOKEN_KEY, token);
}

export function removeAuthToken(): void {
  window.localStorage.removeItem(TOKEN_KEY);
}

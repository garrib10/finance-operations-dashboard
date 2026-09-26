export type DateFormatPreference = "MEDIUM" | "ISO";
export type TransactionPageSize = 10 | 25 | 50;

export interface AccountPreferences {
  dateFormat: DateFormatPreference;
  transactionPageSize: TransactionPageSize;
}

export interface UpdateProfileRequest {
  firstName: string;
  lastName: string;
  displayName: string;
}

export type UpdatePreferencesRequest = AccountPreferences;

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

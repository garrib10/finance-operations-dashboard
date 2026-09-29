package dev.portfolio.finance.entity;

/** Stored as VARCHAR(24); V5 restricts the column to exactly these values. */
public enum RefreshSessionRevocationReason {
    LOGOUT,
    PASSWORD_CHANGE,
    REUSE_DETECTED
}

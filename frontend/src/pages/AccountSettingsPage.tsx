import { useState } from "react";
import { useAuth } from "../context/AuthContext";
import { useAccountForm } from "../hooks/useAccountForm";
import { AccountFeedback } from "../components/AccountFeedback";
import { changePassword } from "../services/accountService";
import type { AccountPreferences, DateFormatPreference, TransactionPageSize } from "../types/account";

function PreferencesForm() {
  const { user, updatePreferences } = useAuth();
  const [draft, setDraft] = useState<AccountPreferences | null>(null);
  const { formRef, statusRef, alertRef, ...form } = useAccountForm();
  if (!user) return null;
  const values = draft ?? user.preferences;
  const unchanged = values.dateFormat === user.preferences.dateFormat && values.transactionPageSize === user.preferences.transactionPageSize;
  return <form className="account-section account-form" aria-labelledby="preferences-heading" ref={formRef} noValidate onSubmit={event => {
    event.preventDefault();
    void form.submit({}, async () => {
      await updatePreferences(values);
      setDraft(null);
    }, "Preferences saved.", ["dateFormat", "transactionPageSize"]);
  }}>
    <h2 id="preferences-heading">Account preferences</h2>
    <div className="form-field"><label htmlFor="preferences-dateFormat">Date format</label>
      <select id="preferences-dateFormat" value={values.dateFormat} disabled={form.pending}
        aria-invalid={Boolean(form.errors.dateFormat)} aria-describedby={form.errors.dateFormat ? "preferences-dateFormat-error" : undefined}
        onChange={event => { setDraft({ ...values, dateFormat: event.target.value as DateFormatPreference }); form.clear("dateFormat"); }}>
        <option value="MEDIUM">Sep 25, 2026 (readable)</option><option value="ISO">2026-09-25 (ISO)</option>
      </select>
      {form.errors.dateFormat && <p className="field-error" id="preferences-dateFormat-error">{form.errors.dateFormat}</p>}
    </div>
    <div className="form-field"><label htmlFor="preferences-transactionPageSize">Transactions per page</label>
      <select id="preferences-transactionPageSize" value={values.transactionPageSize} disabled={form.pending}
        aria-invalid={Boolean(form.errors.transactionPageSize)} aria-describedby={form.errors.transactionPageSize ? "preferences-transactionPageSize-error" : undefined}
        onChange={event => { setDraft({ ...values, transactionPageSize: Number(event.target.value) as TransactionPageSize }); form.clear("transactionPageSize"); }}>
        {[10, 25, 50].map(size => <option key={size} value={size}>{size}</option>)}
      </select>
      {form.errors.transactionPageSize && <p className="field-error" id="preferences-transactionPageSize-error">{form.errors.transactionPageSize}</p>}
    </div>
    <AccountFeedback failure={form.failure} success={form.success} statusRef={statusRef} alertRef={alertRef} />
    <div className="account-actions">
      <button className="button button--primary" disabled={form.pending || unchanged}>{form.pending ? "Saving preferences..." : "Save preferences"}</button>
      <button type="button" className="button button--secondary" disabled={form.pending} onClick={() => { setDraft(null); form.clear(); }}>Reset preferences</button>
    </div>
  </form>;
}

const passwordFields = [
  ["currentPassword", "Current password", "current-password"],
  ["newPassword", "New password", "new-password"],
  ["confirmation", "Confirm new password", "new-password"],
] as const;
const emptyPasswords = { currentPassword: "", newPassword: "", confirmation: "" };

function PasswordForm() {
  const [passwords, setPasswords] = useState(emptyPasswords);
  const passwordRules = [
    { label: "At least 15 characters", met: Array.from(passwords.newPassword).length >= 15 },
    { label: "No more than 72 UTF-8 bytes", met: passwords.newPassword.length > 0 && new TextEncoder().encode(passwords.newPassword).length <= 72 },
  ];
  const [visible, setVisible] = useState({ currentPassword: false, newPassword: false, confirmation: false });
  const { formRef, statusRef, alertRef, ...form } = useAccountForm();
  return <form className="account-section account-form" aria-labelledby="password-heading" ref={formRef} noValidate onSubmit={event => {
    event.preventDefault();
    const errors: Record<string, string> = {};
    if (!passwords.currentPassword) errors.currentPassword = "Current password is required.";
    if (Array.from(passwords.newPassword).length < 15) errors.newPassword = "New password must contain at least 15 characters.";
    else if (new TextEncoder().encode(passwords.newPassword).length > 72) errors.newPassword = "New password must be 72 UTF-8 bytes or fewer.";
    else if (!passwords.newPassword.trim()) errors.newPassword = "Choose a password that is not only whitespace.";
    else if (passwords.newPassword === passwords.currentPassword) errors.newPassword = "New password must differ from the current password.";
    if (passwords.confirmation !== passwords.newPassword) errors.confirmation = "Passwords must match.";
    void form.submit(errors, async () => {
      await changePassword({ currentPassword: passwords.currentPassword, newPassword: passwords.newPassword });
      setPasswords(emptyPasswords);
      setVisible({ currentPassword: false, newPassword: false, confirmation: false });
    }, "Password changed. You are still signed in.", ["currentPassword", "newPassword"]);
  }}>
    <h2 id="password-heading">Change password</h2>
    <div id="password-policy">
      <p>New password requirements:</p>
      <ul className="password-rules" aria-live="polite" aria-label="New password requirements">
        {passwordRules.map(rule => (
          <li key={rule.label} className={rule.met ? "password-rule password-rule--met" : "password-rule"}>
            <span className="password-rule__indicator" aria-hidden="true">{rule.met ? "✓" : ""}</span>
            <span>{rule.label}<span className="password-rule__state"> — {rule.met ? "Met" : "Not met"}</span></span>
          </li>
        ))}
      </ul>
    </div>
    {passwordFields.map(([key, label, autocomplete]) => <div className="form-field" key={key}>
      <label htmlFor={`password-${key}`}>{label}</label>
      <div className="account-password">
        <input id={`password-${key}`} name={key} type={visible[key] ? "text" : "password"} autoComplete={autocomplete}
          value={passwords[key]} minLength={key === "currentPassword" ? undefined : 15} required disabled={form.pending} aria-invalid={Boolean(form.errors[key])}
          aria-describedby={`password-policy${form.errors[key] ? ` password-${key}-error` : ""}`}
          onChange={event => { setPasswords({ ...passwords, [key]: event.target.value }); form.clear(key); }} />
        <button type="button" className="button button--secondary" aria-label={`${visible[key] ? "Hide" : "Show"} ${label.toLowerCase()}`}
          aria-pressed={visible[key]} onClick={() => setVisible({ ...visible, [key]: !visible[key] })}>{visible[key] ? "Hide" : "Show"}</button>
      </div>
      {form.errors[key] && <p className="field-error" id={`password-${key}-error`}>{form.errors[key]}</p>}
    </div>)}
    <AccountFeedback failure={form.failure} success={form.success} statusRef={statusRef} alertRef={alertRef} />
    <button className="button button--primary" disabled={form.pending}>{form.pending ? "Changing password..." : "Change password"}</button>
  </form>;
}

function AccountSettingsPage() {
  return <section className="account-page" aria-labelledby="settings-heading">
    <div className="page-header"><h1 id="settings-heading">Account Settings</h1><p>Your account preferences and password settings.</p></div>
    <PreferencesForm /><PasswordForm />
  </section>;
}
export default AccountSettingsPage;

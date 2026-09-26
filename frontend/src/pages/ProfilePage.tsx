import { useState } from "react";
import { useAuth } from "../context/AuthContext";
import { useAccountForm } from "../hooks/useAccountForm";
import { AccountFeedback } from "../components/AccountFeedback";
import type { UpdateProfileRequest } from "../types/account";

const fields = [
  ["displayName", "Display name", "nickname"],
  ["firstName", "First name", "given-name"],
  ["lastName", "Last name", "family-name"],
] as const;

function ProfilePage() {
  const { user, updateProfile } = useAuth();
  const [emailHovered, setEmailHovered] = useState(false);
  const [emailFocused, setEmailFocused] = useState(false);
  const [draft, setDraft] = useState<UpdateProfileRequest | null>(null);
  const { formRef, statusRef, alertRef, ...form } = useAccountForm();
  if (!user) return null;
  const canonical = { displayName: user.displayName, firstName: user.firstName, lastName: user.lastName };
  const values = draft ?? canonical;
  const unchanged = fields.every(([key]) => values[key].trim() === canonical[key]);

  return <section className="account-page" aria-labelledby="profile-heading">
    <div className="page-header"><h1 id="profile-heading">Profile</h1>
      <p>Your personal details and the name shown on your account.</p></div>
    <form ref={formRef} className="account-section account-form" noValidate aria-label="Profile" onSubmit={event => {
      event.preventDefault();
      const request = { displayName: values.displayName.trim(), firstName: values.firstName.trim(), lastName: values.lastName.trim() };
      const errors: Record<string, string> = {};
      fields.forEach(([key, label]) => {
        if (!request[key]) errors[key] = `${label} is required.`;
        else if (request[key].length > 100) errors[key] = `${label} must be 100 characters or fewer.`;
      });
      void form.submit(errors, async () => {
        await updateProfile(request);
        setDraft(null);
      }, "Profile saved.", fields.map(([key]) => key));
    }}>
      <p id="profile-help">All names are required, up to 100 characters each.</p>
      {fields.map(([key, label, autocomplete]) => <div className="form-field" key={key}>
        <label htmlFor={`profile-${key}`}>{label}</label>
        <input id={`profile-${key}`} name={key} autoComplete={autocomplete} required
          value={values[key]} disabled={form.pending} aria-invalid={Boolean(form.errors[key])}
          aria-describedby={`profile-help${form.errors[key] ? ` profile-${key}-error` : ""}`}
          onChange={event => { setDraft({ ...values, [key]: event.target.value }); form.clear(key); }} />
        {form.errors[key] && <p className="field-error" id={`profile-${key}-error`}>{form.errors[key]}</p>}
      </div>)}
      <div className="form-field"><label htmlFor="profile-email">Email</label>
        <div className="account-email-hint"
          onMouseEnter={() => setEmailHovered(true)} onMouseLeave={() => setEmailHovered(false)}
          onFocus={() => setEmailFocused(true)} onBlur={() => setEmailFocused(false)}>
          <input id="profile-email" type="email" autoComplete="email" value={user.email} readOnly aria-describedby="profile-email-help" />
          <p id="profile-email-help" className="account-email-hint__bubble" hidden={!emailHovered && !emailFocused}>Email cannot be changed here.</p>
        </div>
      </div>
      <AccountFeedback failure={form.failure} success={form.success} statusRef={statusRef} alertRef={alertRef} />
      <div className="account-actions">
        <button className="button button--primary" disabled={form.pending || unchanged}>{form.pending ? "Saving profile..." : "Save profile"}</button>
        <button type="button" className="button button--secondary" disabled={form.pending} onClick={() => { setDraft(null); form.clear(); }}>Reset profile</button>
      </div>
    </form>
  </section>;
}
export default ProfilePage;

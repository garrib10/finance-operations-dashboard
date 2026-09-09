import { useState, type SubmitEvent } from "react";

import { useNavigate } from "react-router-dom";
import { ApiError } from "../services/api";
import { register } from "../services/authService";

function RegisterPage() {
  const navigate = useNavigate();

  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const [errorMessage, setErrorMessage] = useState("");
  const [validationErrors, setValidationErrors] = useState<
    Record<string, string>
  >({});

  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();

    setErrorMessage("");
    setValidationErrors({});
    setIsSubmitting(true);

    try {
      await register({
        firstName,
        lastName,
        email,
        password,
      });

      navigate("/login");
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.validationErrors) {
          setValidationErrors(error.validationErrors);
        } else {
          setErrorMessage(error.message);
        }
      } else {
        setErrorMessage("Unable to register. Please try again.");
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <section className="auth-card">
      <div className="auth-card__header">
        <h1>Create Account</h1>
        <p>Create a FinTrack account to start managing your finances.</p>
      </div>

      <form className="auth-form" onSubmit={handleSubmit}>
        <div className="form-field">
          <label htmlFor="firstName">First Name</label>

          <input
            id="firstName"
            name="firstName"
            type="text"
            value={firstName}
            onChange={(event) => setFirstName(event.target.value)}
            autoComplete="given-name"
            required
          />

          {validationErrors.firstName && (
            <p className="field-error">{validationErrors.firstName}</p>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="lastName">Last Name</label>

          <input
            id="lastName"
            name="lastName"
            type="text"
            value={lastName}
            onChange={(event) => setLastName(event.target.value)}
            autoComplete="family-name"
            required
          />

          {validationErrors.lastName && (
            <p className="field-error">{validationErrors.lastName}</p>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="email">Email</label>

          <input
            id="email"
            name="email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoComplete="email"
            required
          />

          {validationErrors.email && (
            <p className="field-error">{validationErrors.email}</p>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="password">Password</label>

          <input
            id="password"
            name="password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="new-password"
            required
          />

          {validationErrors.password && (
            <p className="field-error">{validationErrors.password}</p>
          )}
        </div>

        {errorMessage && (
          <p className="form-error" role="alert">
            {errorMessage}
          </p>
        )}

        <button
          className="button button--primary"
          type="submit"
          disabled={isSubmitting}
        >
          {isSubmitting ? "Creating account..." : "Create Account"}
        </button>
      </form>
    </section>
  );
}

export default RegisterPage;

import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../services/api";
import * as authService from "../services/authService";
import type { UserResponse } from "../types/auth";
import RegisterPage from "./RegisterPage";

vi.mock("../services/authService");

const mockRegister = vi.mocked(authService.register);

const registeredUser: UserResponse = {
  id: 1,
  firstName: "Demo",
  lastName: "User",
  email: "demo@fintrack.dev",
  createdAt: "2026-09-23T00:00:00",
};

async function completeRegistrationForm(): Promise<void> {
  const user = userEvent.setup();

  await user.type(screen.getByLabelText("First Name"), "Demo");
  await user.type(screen.getByLabelText("Last Name"), "User");
  await user.type(screen.getByLabelText("Email"), "demo@fintrack.dev");
  await user.type(screen.getByLabelText("Password"), "FinTrackDemo123!");
}

function renderRegisterPage(): void {
  render(
    <MemoryRouter initialEntries={["/register"]}>
      <Routes>
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/login" element={<p>Login Page</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("RegisterPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("submits registration details and navigates to login", async () => {
    let resolveRegistration!: (user: UserResponse) => void;
    mockRegister.mockReturnValue(
      new Promise((resolve) => {
        resolveRegistration = resolve;
      }),
    );

    const user = userEvent.setup();
    renderRegisterPage();
    await completeRegistrationForm();

    await user.click(screen.getByRole("button", { name: "Create Account" }));

    expect(mockRegister).toHaveBeenCalledWith({
      firstName: "Demo",
      lastName: "User",
      email: "demo@fintrack.dev",
      password: "FinTrackDemo123!",
    });

    const submittingButton = screen.getByRole("button", {
      name: "Creating account...",
    });
    expect(submittingButton).toBeDisabled();

    resolveRegistration(registeredUser);

    expect(await screen.findByText("Login Page")).toBeInTheDocument();
  });

  it("displays field-level validation errors returned by the API", async () => {
    mockRegister.mockRejectedValue(
      new ApiError("Validation failed.", 400, {
        firstName: "First name is required.",
        lastName: "Last name is required.",
        email: "Email must be valid.",
        password: "Password does not meet the requirements.",
      }),
    );

    const user = userEvent.setup();
    renderRegisterPage();
    await completeRegistrationForm();
    await user.click(screen.getByRole("button", { name: "Create Account" }));

    expect(
      await screen.findByText("First name is required."),
    ).toBeInTheDocument();
    expect(screen.getByText("Last name is required.")).toBeInTheDocument();
    expect(screen.getByText("Email must be valid.")).toBeInTheDocument();
    expect(
      screen.getByText("Password does not meet the requirements."),
    ).toBeInTheDocument();
  });

  it("displays a non-validation API error", async () => {
    mockRegister.mockRejectedValue(
      new ApiError("An account already exists for this email.", 409),
    );

    const user = userEvent.setup();
    renderRegisterPage();
    await completeRegistrationForm();
    await user.click(screen.getByRole("button", { name: "Create Account" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "An account already exists for this email.",
    );
  });

  it("displays a fallback error for an unexpected failure", async () => {
    mockRegister.mockRejectedValue(new Error("Network unavailable"));

    const user = userEvent.setup();
    renderRegisterPage();
    await completeRegistrationForm();
    await user.click(screen.getByRole("button", { name: "Create Account" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Unable to register. Please try again.",
    );
  });
});

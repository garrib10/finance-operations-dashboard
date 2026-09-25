import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { useAuth } from "./AuthContext";

function AuthConsumer() {
  useAuth();

  return null;
}

describe("useAuth", () => {
  it("throws when used outside AuthProvider", () => {
    expect(() => render(<AuthConsumer/>)).toThrow(
      "useAuth must be used within an AuthProvider",
    );
  });
});

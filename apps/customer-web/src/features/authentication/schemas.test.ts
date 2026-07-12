import { describe, expect, it } from "vitest";
import { loginSchema, registerSchema } from "./schemas";

describe("loginSchema", () => {
  it("accepts a valid email and any non-empty password", () => {
    // Login intentionally does not reapply registration complexity rules to existing passwords.
    const result = loginSchema.safeParse({ email: "customer@example.com", password: "x" });

    expect(result.success).toBe(true);
  });

  it("returns field-specific errors for malformed credentials", () => {
    // Validate both fields together so the form can present all actionable feedback in one pass.
    const result = loginSchema.safeParse({ email: "not-an-email", password: "" });

    expect(result.success).toBe(false);
    if (result.success) return;

    const fieldErrors = result.error.flatten().fieldErrors;
    expect(fieldErrors.email).toContain("Enter a valid email");
    expect(fieldErrors.password).toContain("Password is required");
  });
});

describe("registerSchema", () => {
  it("accepts values at the minimum password and display-name boundaries", () => {
    // Boundary coverage protects the frontend contract from drifting stricter than the backend rules.
    const result = registerSchema.safeParse({
      email: "new.customer@example.com",
      password: "p".repeat(12),
      displayName: "AB",
    });

    expect(result.success).toBe(true);
  });

  it("rejects values outside the documented registration limits", () => {
    // Exercise independent limits in one payload and assert that each field remains diagnosable.
    const result = registerSchema.safeParse({
      email: "new.customer@example.com",
      password: "p".repeat(129),
      displayName: "A",
    });

    expect(result.success).toBe(false);
    if (result.success) return;

    const fieldErrors = result.error.flatten().fieldErrors;
    expect(fieldErrors.password).toBeDefined();
    expect(fieldErrors.displayName).toContain("Too short");
  });
});

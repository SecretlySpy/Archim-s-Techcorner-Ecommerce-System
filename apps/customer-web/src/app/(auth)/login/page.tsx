"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useState } from "react";
import Link from "next/link";
import { loginSchema, mfaChallengeSchema, type LoginInput, type MfaChallengeInput } from "@/features/authentication/schemas";
import { api, ApiError } from "@/lib/api";
import { Button } from "@archim/ui-components";

interface LoginSuccessResponse {
  id: string;
  email: string;
  emailVerified: boolean;
}

interface MfaChallengeResponse {
  challengeToken: string;
}

/** Sign-in form with step-up MFA challenge support (G07/G21/S06/S07). */
export default function LoginPage() {
  const router = useRouter();
  const [serverError, setServerError] = useState<string | null>(null);
  const [mfaChallengeToken, setMfaChallengeToken] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginInput>({ resolver: zodResolver(loginSchema) });

  const {
    register: registerMfa,
    handleSubmit: handleSubmitMfa,
    formState: { errors: mfaErrors, isSubmitting: isMfaSubmitting },
  } = useForm<MfaChallengeInput>({ resolver: zodResolver(mfaChallengeSchema) });

  async function onSubmit(values: LoginInput) {
    setServerError(null);
    try {
      const res = await api.post<LoginSuccessResponse | MfaChallengeResponse>("/api/v1/auth/login", values);
      if ("challengeToken" in res && res.challengeToken) {
        setMfaChallengeToken(res.challengeToken);
        return;
      }
      router.push("/account");
    } catch (error) {
      // Uniform, non-enumerating message comes straight from the backend.
      setServerError(error instanceof ApiError ? error.message : "Something went wrong. Please try again.");
    }
  }

  async function onMfaSubmit(values: MfaChallengeInput) {
    if (!mfaChallengeToken) return;
    setServerError(null);
    try {
      await api.post("/api/v1/auth/login/mfa", {
        mfaChallengeToken,
        code: values.code,
      });
      router.push("/account");
    } catch (error) {
      setServerError(error instanceof ApiError ? error.message : "Invalid verification code.");
    }
  }

  if (mfaChallengeToken) {
    return (
      <section className="card">
        <h1>Two-Factor Authentication</h1>
        <p className="subtitle">Enter the 6-digit authentication code or backup recovery code to continue.</p>

        {serverError && <div className="alert alert-error">{serverError}</div>}

        <form onSubmit={handleSubmitMfa(onMfaSubmit)} noValidate>
          <label htmlFor="mfa-code">Authentication or Recovery Code</label>
          <input
            id="mfa-code"
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            placeholder="123456"
            autoFocus
            {...registerMfa("code")}
          />
          {mfaErrors.code && <p className="field-error">{mfaErrors.code.message}</p>}

          <Button type="submit" disabled={isMfaSubmitting}>
            {isMfaSubmitting ? "Verifying…" : "Verify and Sign In"}
          </Button>
        </form>

        <p className="links">
          <button
            type="button"
            className="btn btn-sm btn-ghost"
            onClick={() => {
              setMfaChallengeToken(null);
              setServerError(null);
            }}
          >
            ← Back to password sign-in
          </button>
        </p>
      </section>
    );
  }

  return (
    <section className="card">
      <h1>Sign in</h1>
      <p className="subtitle">Welcome back. Enter your details to continue.</p>

      {serverError && <div className="alert alert-error">{serverError}</div>}

      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <label htmlFor="email">Email</label>
        <input id="email" type="email" autoComplete="email" {...register("email")} />
        {errors.email && <p className="field-error">{errors.email.message}</p>}

        <label htmlFor="password">Password</label>
        <input id="password" type="password" autoComplete="current-password" {...register("password")} />
        {errors.password && <p className="field-error">{errors.password.message}</p>}

        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Signing in…" : "Sign in"}
        </Button>
      </form>

      <p className="links">
        <Link href="/register">Create an account</Link>
      </p>
    </section>
  );
}

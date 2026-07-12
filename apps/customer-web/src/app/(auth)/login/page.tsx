"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useState } from "react";
import Link from "next/link";
import { loginSchema, type LoginInput } from "@/features/authentication/schemas";
import { api, ApiError } from "@/lib/api";

/** Sign-in form. On success the backend sets auth cookies and we route to the account page. */
export default function LoginPage() {
  const router = useRouter();
  const [serverError, setServerError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginInput>({ resolver: zodResolver(loginSchema) });

  async function onSubmit(values: LoginInput) {
    setServerError(null);
    try {
      await api.post("/api/v1/auth/login", values);
      router.push("/account");
    } catch (error) {
      // Uniform, non-enumerating message comes straight from the backend.
      setServerError(error instanceof ApiError ? error.message : "Something went wrong. Please try again.");
    }
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

        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Signing in…" : "Sign in"}
        </button>
      </form>

      <p className="links">
        <Link href="/register">Create an account</Link>
      </p>
    </section>
  );
}

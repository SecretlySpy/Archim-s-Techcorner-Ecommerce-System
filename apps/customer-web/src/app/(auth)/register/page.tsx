"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import Link from "next/link";
import { registerSchema, type RegisterInput } from "@/features/authentication/schemas";
import { api, ApiError } from "@/lib/api";
import { Button } from "@archim/ui-components";

/** Customer registration. The backend responds 202 for any email (anti-enumeration), so on success
 *  we always show the same "check your email" confirmation. */
export default function RegisterPage() {
  const [serverError, setServerError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterInput>({ resolver: zodResolver(registerSchema) });

  async function onSubmit(values: RegisterInput) {
    setServerError(null);
    try {
      await api.post("/api/v1/auth/register/customer", values);
      setDone(true);
    } catch (error) {
      setServerError(error instanceof ApiError ? error.message : "Something went wrong. Please try again.");
    }
  }

  if (done) {
    return (
      <section className="card">
        <h1>Check your email</h1>
        <div className="alert alert-ok">
          If that email can be registered, we&apos;ve sent a verification link. Follow it to activate your account.
        </div>
        <p className="links">
          <Link href="/login">Back to sign in</Link>
        </p>
      </section>
    );
  }

  return (
    <section className="card">
      <h1>Create your account</h1>
      <p className="subtitle">Join Archim&apos;s TechCorner to start shopping.</p>

      {serverError && <div className="alert alert-error">{serverError}</div>}

      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <label htmlFor="displayName">Display name</label>
        <input id="displayName" type="text" autoComplete="name" {...register("displayName")} />
        {errors.displayName && <p className="field-error">{errors.displayName.message}</p>}

        <label htmlFor="email">Email</label>
        <input id="email" type="email" autoComplete="email" {...register("email")} />
        {errors.email && <p className="field-error">{errors.email.message}</p>}

        <label htmlFor="password">Password</label>
        <input id="password" type="password" autoComplete="new-password" {...register("password")} />
        {errors.password
          ? <p className="field-error">{errors.password.message}</p>
          : <p className="subtitle" style={{ margin: "6px 0 0" }}>At least 12 characters.</p>}

        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Creating…" : "Create account"}
        </Button>
      </form>

      <p className="links">
        Already have an account? <Link href="/login">Sign in</Link>
      </p>
    </section>
  );
}

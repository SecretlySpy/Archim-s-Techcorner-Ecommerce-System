"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";

type State = "verifying" | "ok" | "error";

/** Consumes the emailed verification token. Reads the token from the URL query on mount (avoiding the
 *  useSearchParams Suspense requirement) and POSTs it to the backend. */
export default function VerifyEmailPage() {
  const [state, setState] = useState<State>("verifying");
  const [message, setMessage] = useState("");

  useEffect(() => {
    const token = new URLSearchParams(window.location.search).get("token");
    if (!token) {
      setState("error");
      setMessage("This link is missing its verification token.");
      return;
    }
    api
      .post("/api/v1/auth/email/verify", { token })
      .then(() => setState("ok"))
      .catch((error) => {
        setState("error");
        setMessage(error instanceof ApiError ? error.message : "Verification failed.");
      });
  }, []);

  return (
    <section className="card">
      <h1>Email verification</h1>
      {state === "verifying" && <p className="subtitle">Verifying your email…</p>}
      {state === "ok" && (
        <>
          <div className="alert alert-ok">Your email is verified. You can now sign in.</div>
          <Link href="/login"><button>Go to sign in</button></Link>
        </>
      )}
      {state === "error" && (
        <>
          <div className="alert alert-error">{message}</div>
          <Link href="/login"><button>Back to sign in</button></Link>
        </>
      )}
    </section>
  );
}

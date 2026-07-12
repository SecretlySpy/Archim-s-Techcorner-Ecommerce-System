"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api, ApiError, type CurrentUser } from "@/lib/api";

/** Minimal authenticated page: fetches the current principal and shows roles/permissions. Redirects
 *  to /login on 401. Demonstrates the cookie session + /users/me authorization round-trip. */
export default function AccountPage() {
  const router = useRouter();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api
      .get<CurrentUser>("/api/v1/users/me")
      .then(setUser)
      .catch((error) => {
        if (error instanceof ApiError && error.status === 401) {
          router.replace("/login");
        }
      })
      .finally(() => setLoading(false));
  }, [router]);

  async function logout() {
    await api.post("/api/v1/auth/logout", {});
    router.replace("/login");
  }

  if (loading) {
    return <section className="card"><p className="subtitle">Loading…</p></section>;
  }
  if (!user) {
    return <section className="card"><p className="subtitle">Redirecting…</p></section>;
  }

  return (
    <section className="card">
      <h1>Your account</h1>
      <p className="subtitle">{user.email}</p>

      <label>Status</label>
      <div className="chip-row">
        <span className="chip">{user.status}</span>
        <span className="chip">{user.emailVerified ? "email verified" : "email not verified"}</span>
      </div>

      <label>Roles</label>
      <div className="chip-row">
        {user.roles.length ? user.roles.map((r) => <span key={r} className="chip">{r}</span>)
          : <span className="subtitle">none</span>}
      </div>

      <button onClick={logout}>Sign out</button>
    </section>
  );
}

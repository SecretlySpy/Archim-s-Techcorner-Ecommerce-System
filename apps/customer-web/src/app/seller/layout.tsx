"use client";

import { useEffect, useState } from "react";
import { useRouter, usePathname } from "next/navigation";
import Link from "next/link";
import { api, ApiError, type CurrentUser } from "@/lib/api";
import type { ReactNode } from "react";

/**
 * Dashboard layout for the Seller Center. Provides a sidebar with navigation links
 * for seller management, products, and inventory. Redirects to /login if the user
 * is not authenticated.
 */
export default function SellerLayout({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);

  // Authenticate on mount — redirect to login if the session is expired/missing.
  useEffect(() => {
    api
      .get<CurrentUser>("/api/v1/users/me")
      .then(setUser)
      .catch((err) => {
        if (err instanceof ApiError && err.status === 401) {
          router.replace("/login");
        }
      })
      .finally(() => setLoading(false));
  }, [router]);

  if (loading) {
    return (
      <div className="loading-center">
        <div className="spinner" />
      </div>
    );
  }

  if (!user) {
    return (
      <div className="loading-center">
        <p className="subtitle">Redirecting…</p>
      </div>
    );
  }

  /** Helper: returns "active" CSS class if the link path matches the current route. */
  function navClass(href: string) {
    return pathname === href || pathname.startsWith(href + "/") ? "sidebar-link active" : "sidebar-link";
  }

  return (
    <div className="dashboard">
      {/* ---- Sidebar ---- */}
      <aside className="dashboard-sidebar">
        <div className="sidebar-brand">
          <Link href="/">
            Archim&apos;s <span>TechCorner</span>
          </Link>
        </div>

        <div className="sidebar-section">
          <div className="sidebar-section-label">Seller Center</div>
          <Link href="/seller" className={navClass("/seller")}>
            🏪 My Sellers
          </Link>
          <Link href="/seller/onboard" className={navClass("/seller/onboard")}>
            ➕ New Seller
          </Link>
        </div>

        <div className="sidebar-section">
          <div className="sidebar-section-label">Shopping</div>
          <Link href="/cart" className={navClass("/cart")}>
            🛒 Cart
          </Link>
          <Link href="/account" className={navClass("/account")}>
            👤 Account
          </Link>
        </div>

        <div className="sidebar-footer">
          Signed in as<br />
          <strong>{user.email}</strong>
        </div>
      </aside>

      {/* ---- Main content ---- */}
      <main className="dashboard-main">{children}</main>
    </div>
  );
}

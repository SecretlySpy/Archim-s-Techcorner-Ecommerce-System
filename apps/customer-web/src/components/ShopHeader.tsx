"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, type CurrentUser } from "@/lib/api";

/**
 * Sticky shop header: brand, search box, and account actions. Guest-friendly — no auth required.
 * When `onSearch` is provided (homepage) it updates the feed in place; otherwise it navigates home
 * with the query (e.g. from the product detail page).
 */
export function ShopHeader({
  defaultQuery = "",
  onSearch,
}: {
  defaultQuery?: string;
  onSearch?: (query: string) => void;
}) {
  const router = useRouter();
  const [q, setQ] = useState(defaultQuery);
  const [user, setUser] = useState<CurrentUser | null>(null);

  useEffect(() => {
    api.get<CurrentUser>("/api/v1/users/me")
      .then(setUser)
      .catch(() => setUser(null));
  }, []);

  function submit(e: React.FormEvent) {
    e.preventDefault();
    if (onSearch) onSearch(q.trim());
    else router.push(`/?q=${encodeURIComponent(q.trim())}`);
  }

  return (
    <header className="shop-header">
      <div className="shop-nav">
        <div className="brand">
          <Link href="/">
            Archim&apos;s <span>TechCorner</span>
          </Link>
        </div>
        <form className="shop-search" onSubmit={submit}>
          <input
            type="search"
            placeholder="Search for laptops, phones, cameras…"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            aria-label="Search products"
          />
        </form>
        <div className="shop-nav-actions">
          <Link className="btn btn-ghost btn-sm" href="/wishlist">Wishlist</Link>
          <Link className="btn btn-outline btn-sm" href="/cart">Cart</Link>
          {user ? (
            <Link className="btn btn-sm" href="/account">Account</Link>
          ) : (
            <Link className="btn btn-sm" href="/login">Sign in</Link>
          )}
        </div>
      </div>
    </header>
  );
}

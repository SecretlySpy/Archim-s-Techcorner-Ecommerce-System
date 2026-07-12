"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";

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
          <Link className="btn btn-outline btn-sm" href="/cart">Cart</Link>
          <Link className="btn btn-sm" href="/login">Sign in</Link>
        </div>
      </div>
    </header>
  );
}

"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, autocomplete, type CurrentUser, type FeedItem } from "@/lib/api";

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
  const [suggestions, setSuggestions] = useState<FeedItem[]>([]);
  const [suggestLoading, setSuggestLoading] = useState(false);

  useEffect(() => {
    api.get<CurrentUser>("/api/v1/users/me")
      .then(setUser)
      .catch(() => setUser(null));
  }, []);

  // T08: simple debounce autocomplete (MySQL)
  useEffect(() => {
    const term = q.trim();
    if (term.length < 2) {
      setSuggestions([]);
      return;
    }
    const t = setTimeout(() => {
      setSuggestLoading(true);
      autocomplete(term)
        .then(setSuggestions)
        .catch(() => setSuggestions([]))
        .finally(() => setSuggestLoading(false));
    }, 200);
    return () => clearTimeout(t);
  }, [q]);

  function submit(e: React.FormEvent) {
    e.preventDefault();
    setSuggestions([]);
    if (onSearch) onSearch(q.trim());
    else router.push(`/?q=${encodeURIComponent(q.trim())}`);
  }

  function pickSuggestion(s: FeedItem) {
    setQ(s.name);
    setSuggestions([]);
    if (onSearch) onSearch(s.name);
    else router.push(`/products/${s.id}`);
  }

  return (
    <header className="shop-header">
      <div className="shop-nav">
        <div className="brand">
          <Link href="/">
            Archim&apos;s <span>TechCorner</span>
          </Link>
        </div>
        <form className="shop-search" onSubmit={submit} style={{ position: 'relative' }}>
          <input
            type="search"
            placeholder="Search for laptops, phones, cameras…"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            aria-label="Search products"
            autoComplete="off"
          />
          { (suggestions.length > 0 || suggestLoading) && (
            <div className="autocomplete" style={{ position: 'absolute', top: '100%', left: 0, right: 0, background: 'var(--panel)', border: '1px solid var(--border)', zIndex: 10, maxHeight: 200, overflow: 'auto' }}>
              {suggestLoading && <div className="muted" style={{ padding: 8 }}>Loading…</div>}
              {suggestions.map(s => (
                <div key={s.id} className="autocomplete-item" style={{ padding: 8, cursor: 'pointer', borderBottom: '1px solid var(--border)' }} onClick={() => pickSuggestion(s)}>
                  {s.name} <span className="muted">· {s.sellerName}</span>
                </div>
              ))}
              {suggestions.length === 0 && !suggestLoading && <div className="muted" style={{ padding: 8 }}>No matches</div>}
            </div>
          )}
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

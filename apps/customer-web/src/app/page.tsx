"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { ShopHeader } from "@/components/ShopHeader";
import { api, type FeedPage, type CategoryResponse } from "@/lib/api";

function formatPrice(n: number | null): string {
  if (n == null) return "—";
  return "₱" + n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/**
 * Homepage = the public product feed (guest browsing). No login required — this is the default shop
 * stream, mirroring Lazada/Shopee. Search and category filters drive the feed in place; a shared
 * `/?q=` / `/?categoryId=` URL is honored on load.
 */
export default function HomePage() {
  const [query, setQuery] = useState("");
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [page, setPage] = useState(0);
  const [feed, setFeed] = useState<FeedPage | null>(null);
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Honor a bookmarked/shared query on first load.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const q = params.get("q");
    const c = params.get("categoryId");
    if (q) setQuery(q);
    if (c) setCategoryId(Number(c));
  }, []);

  // Category filter strip.
  useEffect(() => {
    api.get<CategoryResponse[]>("/api/v1/catalog/categories").then(setCategories).catch(() => {});
  }, []);

  const loadFeed = useCallback(() => {
    setLoading(true);
    setError(null);
    const params = new URLSearchParams({ page: String(page), size: "24" });
    if (categoryId != null) params.set("categoryId", String(categoryId));
    if (query) params.set("q", query);
    api
      .get<FeedPage>(`/api/v1/catalog/products/feed?${params.toString()}`)
      .then(setFeed)
      .catch(() => setError("Could not load products. Please try again."))
      .finally(() => setLoading(false));
  }, [page, categoryId, query]);

  useEffect(() => {
    loadFeed();
  }, [loadFeed]);

  function onSearch(q: string) {
    setQuery(q);
    setCategoryId(null);
    setPage(0);
  }
  function pickCategory(id: number | null) {
    setCategoryId(id);
    setQuery("");
    setPage(0);
  }

  return (
    <>
      <ShopHeader defaultQuery={query} onSearch={onSearch} />
      <div className="shop-body">
        <div className="category-strip">
          <button
            className={`category-pill ${categoryId == null ? "active" : ""}`}
            onClick={() => pickCategory(null)}
          >
            All
          </button>
          {categories.map((c) => (
            <button
              key={c.id}
              className={`category-pill ${categoryId === c.id ? "active" : ""}`}
              onClick={() => pickCategory(c.id)}
            >
              {c.name}
            </button>
          ))}
        </div>

        {query && <p className="page-subtitle">Results for &ldquo;{query}&rdquo;</p>}

        {loading ? (
          <div className="loading-center"><span className="spinner" /></div>
        ) : error ? (
          <div className="alert alert-error">{error}</div>
        ) : !feed || feed.items.length === 0 ? (
          <div className="empty-state">
            <div className="empty-state-icon">🛒</div>
            <div className="empty-state-text">
              No products yet. Approved seller listings will appear here.
            </div>
          </div>
        ) : (
          <>
            <div className="product-grid">
              {feed.items.map((p) => (
                <Link key={p.id} href={`/products/${p.id}`} className="product-card">
                  <div className="product-thumb">📦</div>
                  <div className="product-card-body">
                    <div className="product-name">{p.name}</div>
                    <div className="product-price">{formatPrice(p.minPrice)}</div>
                    <div className="product-seller">{p.sellerName}</div>
                  </div>
                </Link>
              ))}
            </div>
            {feed.totalPages > 1 && (
              <div className="pagination">
                <button className="btn btn-outline btn-sm" disabled={page <= 0} onClick={() => setPage(page - 1)}>
                  Previous
                </button>
                <span className="muted">Page {feed.page + 1} of {feed.totalPages}</span>
                <button
                  className="btn btn-outline btn-sm"
                  disabled={page >= feed.totalPages - 1}
                  onClick={() => setPage(page + 1)}
                >
                  Next
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </>
  );
}

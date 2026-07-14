"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, ApiError, type WishlistView, type ProductResponse } from "@/lib/api";
import { useToast } from "@/components/Toast";

export default function WishlistPage() {
  const router = useRouter();
  const [wishlist, setWishlist] = useState<WishlistView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [updating, setUpdating] = useState<string | null>(null);
  const { showToast } = useToast();

  function loadWishlist() {
    setLoading(true);
    api
      .get<WishlistView[]>("/api/v1/wishlist")
      .then(setWishlist)
      .catch((err) => {
        if (err instanceof ApiError && err.status === 401) {
          router.replace("/login");
          return;
        }
        setError("Failed to load wishlist.");
      })
      .finally(() => setLoading(false));
  }

  useEffect(loadWishlist, [router]);

  async function removeItem(productPublicId: string) {
    setUpdating(productPublicId);
    try {
      const updated = await api.delete<WishlistView[]>(`/api/v1/wishlist/items/${productPublicId}`);
      setWishlist(updated);
      showToast("Item removed from wishlist", "success");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to remove item.");
      showToast("Failed to remove item", "error");
    } finally {
      setUpdating(null);
    }
  }

  async function moveToCart(item: WishlistView) {
    setUpdating(item.productPublicId);
    try {
      // Fetch product to see variants
      const product = await api.get<ProductResponse>(`/api/v1/catalog/products/${item.productPublicId}`);
      
      if (product.variants.length === 1) {
        // Only one variant, move directly
        const res = await api.post<{ wishlist: WishlistView[] }>(
          `/api/v1/wishlist/items/${item.productPublicId}/move-to-cart`, 
          { variantPublicId: product.variants[0].id }
        );
        setWishlist(res.wishlist);
        showToast("Moved to cart", "success");
      } else {
        // Multiple variants, redirect to product page to choose
        showToast("Please choose a specific option to add to cart.", "info");
        router.push(`/products/${item.productPublicId}`);
      }
    } catch (err) {
      showToast("Failed to move to cart", "error");
    } finally {
      setUpdating(null);
    }
  }

  if (loading) {
    return (
      <div className="container">
        <div className="loading-center"><div className="spinner" /></div>
      </div>
    );
  }

  const isEmpty = wishlist.length === 0;

  return (
    <div className="container" style={{ maxWidth: 800 }}>
      <div className="brand">
        <Link href="/">Archim&apos;s <span>TechCorner</span></Link>
      </div>

      <div className="card-header">
        <h1 className="page-title">My Wishlist</h1>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {isEmpty ? (
        <div className="card">
          <div className="empty-state">
            <div className="empty-state-icon">❤️</div>
            <p className="empty-state-text">Your wishlist is empty.</p>
            <Link href="/" className="btn" style={{ marginTop: 16, display: "inline-flex" }}>
              Explore Products
            </Link>
          </div>
        </div>
      ) : (
        <div className="cart-group">
          {wishlist.map((item) => (
            <div className="cart-item" key={item.productPublicId}>
              <div>
                <Link href={`/products/${item.productPublicId}`} className="cart-item-name" style={{ color: "var(--text)", textDecoration: "none" }}>
                  {item.name}
                </Link>
                <div className="cart-item-meta" style={{ marginTop: "4px" }}>
                  Added on {new Date(item.addedAt).toLocaleDateString()}
                </div>
              </div>

              <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                <button
                  className="btn btn-sm btn-outline"
                  style={{ marginTop: 0 }}
                  disabled={updating === item.productPublicId}
                  onClick={() => moveToCart(item)}
                >
                  Move to Cart
                </button>
                <button
                  className="btn btn-sm btn-ghost"
                  style={{ color: "var(--danger)", marginTop: 0 }}
                  disabled={updating === item.productPublicId}
                  onClick={() => removeItem(item.productPublicId)}
                >
                  ✕
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      <p className="links" style={{ marginTop: 24 }}>
        <Link href="/cart">View Cart</Link>
        {" · "}
        <Link href="/account">Account</Link>
      </p>
    </div>
  );
}

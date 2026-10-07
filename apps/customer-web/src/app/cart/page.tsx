"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, ApiError, type CartView, type CurrentUser } from "@/lib/api";
import { Button } from "@archim/ui-components";

/**
 * Shopping Cart page. Displays the user's cart with items grouped by seller,
 * quantity controls, and a cart total. Uses the dashboard layout via the
 * seller layout (or standalone if accessed directly).
 */
export default function CartPage() {
  const router = useRouter();
  const [cart, setCart] = useState<CartView | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [updating, setUpdating] = useState<string | null>(null);

  function loadCart() {
    setLoading(true);
    api
      .get<CartView>("/api/v1/cart")
      .then(setCart)
      .catch((err) => {
        if (err instanceof ApiError && err.status === 401) {
          router.replace("/login");
          return;
        }
        setError("Failed to load cart.");
      })
      .finally(() => setLoading(false));
  }

  useEffect(loadCart, [router]);

  /** Update the quantity of a cart item. */
  async function updateQuantity(variantPublicId: string, quantity: number) {
    setUpdating(variantPublicId);
    try {
      const updated = await api.put<CartView>(
        `/api/v1/cart/items/${variantPublicId}`,
        { quantity }
      );
      setCart(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to update quantity.");
    } finally {
      setUpdating(null);
    }
  }

  /** Remove an item from the cart. */
  async function removeItem(variantPublicId: string) {
    setUpdating(variantPublicId);
    try {
      const updated = await api.delete<CartView>(`/api/v1/cart/items/${variantPublicId}`);
      setCart(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to remove item.");
    } finally {
      setUpdating(null);
    }
  }

  /** Clear the entire cart. */
  async function clearCart() {
    try {
      await api.delete("/api/v1/cart");
      setCart({ sellerGroups: [], cartTotal: 0, totalItems: 0 });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to clear cart.");
    }
  }

  if (loading) {
    return (
      <div className="container">
        <div className="loading-center"><div className="spinner" /></div>
      </div>
    );
  }

  const isEmpty = !cart || cart.totalItems === 0;

  return (
    <div className="container" style={{ maxWidth: 800 }}>
      <div className="brand">
        <Link href="/">Archim&apos;s <span>TechCorner</span></Link>
      </div>

      <div className="card-header">
        <h1 className="page-title">Shopping Cart</h1>
        {!isEmpty && (
          <Button variant="outline" onClick={clearCart} style={{ marginTop: 0 }}>
            Clear Cart
          </Button>
        )}
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {isEmpty ? (
        <div className="card">
          <div className="empty-state">
            <div className="empty-state-icon">🛒</div>
            <p className="empty-state-text">Your cart is empty.</p>
            <Link href="/" className="btn" style={{ marginTop: 16, display: "inline-flex" }}>
              Continue Shopping
            </Link>
          </div>
        </div>
      ) : (
        <>
          {cart!.sellerGroups.map((group) => (
            <div className="cart-group" key={group.sellerPublicId}>
              <div className="cart-group-header">
                🏪 <span>{group.sellerName}</span>
                <span className="cart-item-meta" style={{ marginLeft: "auto" }}>
                  Subtotal: ${group.subtotal.toFixed(2)}
                </span>
              </div>

              {group.items.map((item) => (
                <div className="cart-item" key={item.variantPublicId}>
                  <div>
                    <div className="cart-item-name">{item.productName}</div>
                    <div className="cart-item-meta">
                      {item.variantName} · SKU: {item.sku}
                    </div>
                    {item.quantity > item.availableStock && (
                      <div className="cart-item-meta" style={{ color: "var(--warning)" }}>
                        ⚠️ Only {item.availableStock} available
                      </div>
                    )}
                  </div>

                  <div className="cart-price">${item.unitPrice.toFixed(2)}</div>

                  <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
                    <button
                      className="btn btn-sm btn-outline"
                      style={{ marginTop: 0, padding: "6px 10px" }}
                      disabled={updating === item.variantPublicId || item.quantity <= 1}
                      onClick={() => updateQuantity(item.variantPublicId, item.quantity - 1)}
                    >
                      −
                    </button>
                    <span className="cart-qty">{item.quantity}</span>
                    <button
                      className="btn btn-sm btn-outline"
                      style={{ marginTop: 0, padding: "6px 10px" }}
                      disabled={updating === item.variantPublicId}
                      onClick={() => updateQuantity(item.variantPublicId, item.quantity + 1)}
                    >
                      +
                    </button>
                  </div>

                  <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <div className="cart-price">${item.lineTotal.toFixed(2)}</div>
                     <Button
                       variant="ghost"
                       style={{ color: "var(--danger)", marginTop: 0 }}
                       disabled={updating === item.variantPublicId}
                       onClick={() => removeItem(item.variantPublicId)}
                     >
                       ✕
                     </Button>
                  </div>
                </div>
              ))}
            </div>
          ))}

          <div className="cart-summary">
            <span>Total ({cart!.totalItems} items)</span>
            <span>${cart!.cartTotal.toFixed(2)}</span>
          </div>

          <Link href="/checkout" className="btn-full" style={{ marginTop: 16, display: 'block', textAlign: 'center' }}>
            Proceed to Checkout
          </Link>
          <p className="cart-item-meta" style={{ textAlign: "center", marginTop: 8 }}>
            Revalidates stock/price. Follows plan transaction model (stub for now)
          </p>
        </>
      )}

      <p className="links" style={{ marginTop: 24 }}>
        <Link href="/seller">Seller Center</Link>
        {" · "}
        <Link href="/account">Account</Link>
      </p>
    </div>
  );
}

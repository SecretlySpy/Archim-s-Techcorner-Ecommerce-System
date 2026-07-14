"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ShopHeader } from "@/components/ShopHeader";
import { api, ApiError, type ProductResponse, type CurrentUser } from "@/lib/api";
import { useToast } from "@/components/Toast";

function peso(n: number): string {
  return "₱" + n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/** Public product detail (guest browsing). The backend only serves published (ACTIVE) products here. */
export default function ProductDetailPage() {
  const params = useParams<{ id: string }>();
  const id = String(params.id);
  const [product, setProduct] = useState<ProductResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [missing, setMissing] = useState(false);
  
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [selectedVariantId, setSelectedVariantId] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);
  const { showToast } = useToast();

  useEffect(() => {
    api
      .get<ProductResponse>(`/api/v1/catalog/products/${id}`)
      .then((res) => {
        setProduct(res);
        if (res.variants.length > 0) {
          setSelectedVariantId(res.variants[0].id);
        }
      })
      .catch((e) => {
        if (e instanceof ApiError && (e.status === 404 || e.status === 400)) setMissing(true);
      })
      .finally(() => setLoading(false));

    api.get<CurrentUser>("/api/v1/users/me")
      .then(setUser)
      .catch(() => setUser(null));
  }, [id]);

  async function handleAddToCart() {
    if (!selectedVariantId) return;
    setActionLoading(true);
    try {
      await api.post("/api/v1/cart/items", { variantPublicId: selectedVariantId, quantity: 1 });
      showToast("Added to cart", "success");
    } catch (e: any) {
      showToast(e.message || "Failed to add to cart", "error");
    } finally {
      setActionLoading(false);
    }
  }

  async function handleAddToWishlist() {
    if (!product) return;
    setActionLoading(true);
    try {
      await api.post("/api/v1/wishlist/items", { productPublicId: product.id });
      showToast("Added to wishlist", "success");
    } catch (e: any) {
      showToast(e.message || "Failed to add to wishlist", "error");
    } finally {
      setActionLoading(false);
    }
  }

  const prices = product?.variants.map((v) => v.salePrice ?? v.regularPrice) ?? [];
  const from = prices.length ? Math.min(...prices) : null;

  return (
    <>
      <ShopHeader />
      <div className="shop-body">
        {loading ? (
          <div className="loading-center"><span className="spinner" /></div>
        ) : missing || !product ? (
          <div className="empty-state">
            <div className="empty-state-icon">🔍</div>
            <div className="empty-state-text">This product is not available.</div>
            <p className="links"><Link href="/">← Back to shop</Link></p>
          </div>
        ) : (
          <div className="pdp">
            <p className="links"><Link href="/">← Back to shop</Link></p>
            <div className="card">
              <h1 className="page-title">{product.name}</h1>
              {product.description && <p className="subtitle">{product.description}</p>}
              <div className="pdp-price">{from != null ? peso(from) : "—"}</div>

              <h3>Options</h3>
              {product.variants.length === 0 ? (
                <p className="subtitle">No variants available.</p>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: "8px", marginBottom: "20px" }}>
                  {product.variants.map((v) => (
                    <label 
                      key={v.id} 
                      className="variant-row" 
                      style={{ 
                        margin: 0, 
                        cursor: "pointer",
                        borderColor: selectedVariantId === v.id ? "var(--accent)" : "var(--border)",
                        background: selectedVariantId === v.id ? "var(--accent-dim)" : "transparent"
                      }}
                    >
                      <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
                        <input 
                          type="radio" 
                          name="variant" 
                          value={v.id}
                          checked={selectedVariantId === v.id}
                          onChange={() => setSelectedVariantId(v.id)}
                        />
                        <div>
                          <div style={{ fontWeight: 500 }}>{v.name}</div>
                          <div className="product-seller">
                            SKU {v.sku} · {v.stockQuantity > 0 ? `${v.stockQuantity} in stock` : "out of stock"}
                          </div>
                        </div>
                      </div>
                      <div className="cart-price">{peso(v.salePrice ?? v.regularPrice)}</div>
                    </label>
                  ))}
                </div>
              )}

              {user ? (
                <div style={{ display: "flex", gap: "12px" }}>
                  <button 
                    className="btn btn-full" 
                    disabled={!selectedVariantId || actionLoading} 
                    onClick={handleAddToCart}
                  >
                    Add to Cart
                  </button>
                  <button 
                    className="btn btn-outline btn-full" 
                    disabled={actionLoading} 
                    onClick={handleAddToWishlist}
                  >
                    Save to Wishlist
                  </button>
                </div>
              ) : (
                <Link className="btn btn-full" href="/login">Sign in to add to cart</Link>
              )}
            </div>
          </div>
        )}
      </div>
    </>
  );
}

"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ShopHeader } from "@/components/ShopHeader";
import { api, ApiError, type ProductResponse } from "@/lib/api";

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

  useEffect(() => {
    api
      .get<ProductResponse>(`/api/v1/catalog/products/${id}`)
      .then(setProduct)
      .catch((e) => {
        if (e instanceof ApiError && (e.status === 404 || e.status === 400)) setMissing(true);
      })
      .finally(() => setLoading(false));
  }, [id]);

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
                product.variants.map((v) => (
                  <div className="variant-row" key={v.id}>
                    <div>
                      <div style={{ fontWeight: 500 }}>{v.name}</div>
                      <div className="product-seller">
                        SKU {v.sku} · {v.stockQuantity > 0 ? `${v.stockQuantity} in stock` : "out of stock"}
                      </div>
                    </div>
                    <div className="cart-price">{peso(v.salePrice ?? v.regularPrice)}</div>
                  </div>
                ))
              )}

              <Link className="btn btn-full" href="/login">Sign in to add to cart</Link>
            </div>
          </div>
        )}
      </div>
    </>
  );
}

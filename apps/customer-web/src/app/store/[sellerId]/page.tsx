"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ShopHeader } from "@/components/ShopHeader";
import { api, type ProductResponse } from "@/lib/api";
import { Button } from "@archim/ui-components";

interface PublicSeller {
  sellerId: string;
  sellerName: string;
  status: string;
  stores?: { storeName: string }[];
}

function peso(n: number | null): string {
  if (n == null) return "—";
  return "₱" + n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/** T10: Public customer-facing seller storefront.
 * Shows store info + list of ACTIVE products.
 * Uses new public APIs.
 * Loading/empty/error states included.
 */
export default function SellerStorefrontPage() {
  const params = useParams<{ sellerId: string }>();
  const sellerId = params.sellerId as string;

  const [seller, setSeller] = useState<PublicSeller | null>(null);
  const [products, setProducts] = useState<ProductResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    Promise.all([
      api.get<PublicSeller>(`/api/v1/sellers/${sellerId}`).catch(() => null),
      api.get<ProductResponse[]>(`/api/v1/catalog/products/public?sellerPublicId=${sellerId}`).catch(() => [])
    ])
      .then(([s, prods]) => {
        if (!s) throw new Error("Seller not found or not public");
        setSeller(s);
        setProducts(prods || []);
      })
      .catch((e) => setError(e.message || "Failed to load storefront."))
      .finally(() => setLoading(false));
  }, [sellerId]);

  if (loading) {
    return (
      <>
        <ShopHeader />
        <div className="shop-body"><div className="loading-center"><span className="spinner" /></div></div>
      </>
    );
  }

  if (error || !seller) {
    return (
      <>
        <ShopHeader />
        <div className="shop-body">
          <div className="empty-state">
            <div className="empty-state-icon">🏪</div>
            <div className="empty-state-text">{error || "Store not found."}</div>
            <Link href="/">Back to shop</Link>
          </div>
        </div>
      </>
    );
  }

  return (
    <>
      <ShopHeader />
      <div className="shop-body">
        <div className="card">
          <h1 className="page-title">{seller.sellerName}</h1>
          {seller.stores && seller.stores.length > 0 && (
            <p className="subtitle">Store: {seller.stores[0].storeName}</p>
          )}
          <p className="muted">Active products from this seller</p>
        </div>

        {products.length === 0 ? (
          <div className="empty-state">
            <div className="empty-state-icon">📦</div>
            <div className="empty-state-text">No products listed yet.</div>
          </div>
        ) : (
          <div className="product-grid">
            {products.map((p) => (
              <Link key={p.id} href={`/products/${p.id}`} className="product-card">
                <div className="product-thumb">📦</div>
                <div className="product-card-body">
                  <div className="product-name">{p.name}</div>
                  <div className="product-price">
                    {p.variants.length ? peso(Math.min(...p.variants.map(v => (v.salePrice ?? v.regularPrice) as number))) : "—"}
                  </div>
                  <div className="product-seller">{p.status}</div>
                </div>
              </Link>
            ))}
          </div>
        )}

        <p style={{ marginTop: 24 }}>
          <Link href="/"><Button variant="outline">← Back to marketplace</Button></Link>
        </p>
      </div>
    </>
  );
}

"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { api, ApiError, type ProductResponse, type CurrentUser } from "@/lib/api";

/**
 * Product Management page for a specific seller. Lists all products (any status)
 * and allows submitting DRAFT products for approval.
 */
export default function SellerProductsPage() {
  const params = useParams();
  const sellerId = params.sellerId as string;

  const [products, setProducts] = useState<ProductResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  function loadProducts() {
    setLoading(true);
    api
      .get<ProductResponse[]>(`/api/v1/catalog/products?sellerPublicId=${sellerId}`)
      .then(setProducts)
      .catch(() => setError("Failed to load products."))
      .finally(() => setLoading(false));
  }

  useEffect(loadProducts, [sellerId]);

  /** Submit a DRAFT product for admin approval. */
  async function submitForApproval(productId: string) {
    setActionLoading(productId);
    try {
      await api.post(`/api/v1/catalog/products/${productId}/submit`, {});
      loadProducts(); // Refresh the list to show updated status
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to submit product.");
    } finally {
      setActionLoading(null);
    }
  }

  if (loading) {
    return <div className="loading-center"><div className="spinner" /></div>;
  }

  return (
    <>
      <div className="card-header" style={{ marginBottom: 20 }}>
        <div>
          <h1 className="page-title">Products</h1>
          <p className="page-subtitle" style={{ marginBottom: 0 }}>
            Manage your product catalog for this seller.
          </p>
        </div>
        <Link href={`/seller/${sellerId}/products/new`} className="btn btn-sm">
          + Add Product
        </Link>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {products.length === 0 ? (
        <div className="card">
          <div className="empty-state">
            <div className="empty-state-icon">📦</div>
            <p className="empty-state-text">No products yet. Create your first product!</p>
          </div>
        </div>
      ) : (
        <div className="card" style={{ padding: 0 }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>Product</th>
                <th>Status</th>
                <th>Variants</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr key={p.id}>
                  <td>
                    <div className="cart-item-name">{p.name}</div>
                    <div className="cart-item-meta">{p.slug}</div>
                  </td>
                  <td>
                    <span className={statusBadgeClass(p.status)}>{p.status}</span>
                  </td>
                  <td>
                    {p.variants.length > 0 ? (
                      <div className="chip-row">
                        {p.variants.map((v) => (
                          <span key={v.id} className="chip">
                            {v.name} — ${v.regularPrice}
                          </span>
                        ))}
                      </div>
                    ) : (
                      <span className="cart-item-meta">No variants</span>
                    )}
                  </td>
                  <td>
                    {p.status === "DRAFT" && (
                      <button
                        className="btn btn-sm"
                        disabled={actionLoading === p.id}
                        onClick={() => submitForApproval(p.id)}
                        style={{ marginTop: 0 }}
                      >
                        {actionLoading === p.id ? "Submitting…" : "Submit for Approval"}
                      </button>
                    )}
                    {p.status === "ACTIVE" && (
                      <span className="cart-item-meta">Published ✓</span>
                    )}
                    {p.status === "PENDING_APPROVAL" && (
                      <span className="cart-item-meta">Awaiting review…</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

function statusBadgeClass(status: string): string {
  const map: Record<string, string> = {
    DRAFT: "badge badge-draft",
    PENDING_APPROVAL: "badge badge-pending",
    ACTIVE: "badge badge-active",
    REJECTED: "badge badge-rejected",
    ARCHIVED: "badge badge-archived",
  };
  return map[status] ?? "badge badge-draft";
}

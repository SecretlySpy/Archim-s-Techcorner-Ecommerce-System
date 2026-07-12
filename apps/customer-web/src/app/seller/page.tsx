"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { api, type SellerSummary } from "@/lib/api";

/**
 * Seller Dashboard — Lists all sellers the authenticated user belongs to.
 * Shows status badges (PENDING/ACTIVE/SUSPENDED) and links to manage each seller's
 * products and inventory.
 */
export default function SellerDashboardPage() {
  const [sellers, setSellers] = useState<SellerSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .get<SellerSummary[]>("/api/v1/sellers/me")
      .then(setSellers)
      .catch(() => setError("Failed to load your sellers."))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return <div className="loading-center"><div className="spinner" /></div>;
  }

  return (
    <>
      <h1 className="page-title">Seller Dashboard</h1>
      <p className="page-subtitle">Manage your seller accounts, stores, and products.</p>

      {error && <div className="alert alert-error">{error}</div>}

      {sellers.length === 0 ? (
        <div className="card">
          <div className="empty-state">
            <div className="empty-state-icon">🏪</div>
            <p className="empty-state-text">
              You don&apos;t have any seller accounts yet.
            </p>
            <Link href="/seller/onboard" className="btn" style={{ marginTop: 16, display: "inline-flex" }}>
              Become a Seller
            </Link>
          </div>
        </div>
      ) : (
        sellers.map((seller) => (
          <div className="card" key={seller.sellerId}>
            <div className="card-header">
              <div>
                <h2>{seller.sellerName}</h2>
                <span className={statusBadgeClass(seller.status)}>
                  {seller.status}
                </span>
              </div>
            </div>

            {/* Stores */}
            {seller.stores.length > 0 && (
              <div style={{ marginBottom: 12 }}>
                <label>Stores</label>
                <div className="chip-row">
                  {seller.stores.map((s) => (
                    <span key={s.storeId} className="chip">{s.storeName}</span>
                  ))}
                </div>
              </div>
            )}

            {/* Warehouses */}
            {seller.warehouses && seller.warehouses.length > 0 && (
              <div style={{ marginBottom: 12 }}>
                <label>Warehouses</label>
                <div className="chip-row">
                  {seller.warehouses.map((w) => (
                    <span key={w.warehouseId} className="chip">{w.warehouseName}</span>
                  ))}
                </div>
              </div>
            )}

            {/* Actions — only available for ACTIVE sellers */}
            {seller.status === "ACTIVE" && (
              <div style={{ display: "flex", gap: 8, marginTop: 16 }}>
                <Link
                  href={`/seller/${seller.sellerId}/products`}
                  className="btn btn-sm"
                >
                  📦 Products
                </Link>
                <Link
                  href={`/seller/${seller.sellerId}/inventory`}
                  className="btn btn-sm btn-outline"
                >
                  📊 Inventory
                </Link>
              </div>
            )}

            {seller.status === "PENDING" && (
              <div className="alert alert-warning" style={{ marginTop: 16 }}>
                Your seller account is pending admin approval. You&apos;ll be able to manage products
                once approved.
              </div>
            )}
          </div>
        ))
      )}
    </>
  );
}

/** Maps a seller/product status to the appropriate badge CSS class. */
function statusBadgeClass(status: string): string {
  const map: Record<string, string> = {
    DRAFT: "badge badge-draft",
    PENDING: "badge badge-pending",
    PENDING_APPROVAL: "badge badge-pending",
    ACTIVE: "badge badge-active",
    REJECTED: "badge badge-rejected",
    ARCHIVED: "badge badge-archived",
    SUSPENDED: "badge badge-suspended",
  };
  return map[status] ?? "badge badge-draft";
}

"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { api, ApiError, type InventoryBucket } from "@/lib/api";

/**
 * Inventory Management page for a seller. Shows low-stock alerts and allows
 * inline stock adjustments via the inventory API.
 */
export default function InventoryPage() {
  const params = useParams();
  const sellerId = params.sellerId as string;

  const [buckets, setBuckets] = useState<InventoryBucket[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  function loadInventory() {
    setLoading(true);
    api
      .get<InventoryBucket[]>(`/api/v1/inventory/low-stock?sellerPublicId=${sellerId}`)
      .then(setBuckets)
      .catch(() => setError("Failed to load inventory."))
      .finally(() => setLoading(false));
  }

  useEffect(loadInventory, [sellerId]);

  if (loading) {
    return <div className="loading-center"><div className="spinner" /></div>;
  }

  return (
    <>
      <h1 className="page-title">Inventory</h1>
      <p className="page-subtitle">
        Low-stock alerts for your warehouses. Items below reorder threshold are shown here.
      </p>

      {error && <div className="alert alert-error">{error}</div>}

      {buckets.length === 0 ? (
        <div className="card">
          <div className="empty-state">
            <div className="empty-state-icon">✅</div>
            <p className="empty-state-text">
              All stock levels are healthy! No items below reorder threshold.
            </p>
          </div>
        </div>
      ) : (
        <div className="card" style={{ padding: 0 }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>Variant / SKU</th>
                <th>Warehouse</th>
                <th>On Hand</th>
                <th>Reserved</th>
                <th>Sellable</th>
                <th>Threshold</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {buckets.map((b, i) => {
                const critical = b.sellable <= 0;
                const low = b.sellable > 0 && b.sellable <= b.reorderThreshold;
                return (
                  <tr key={i}>
                    <td>
                      <div className="cart-item-name">{b.variantSku}</div>
                      <div className="cart-item-meta">{b.variantPublicId.slice(0, 8)}…</div>
                    </td>
                    <td>{b.warehouseName || b.warehousePublicId.slice(0, 8) + "…"}</td>
                    <td>{b.onHand}</td>
                    <td>{b.reserved}</td>
                    <td style={{ fontWeight: 600 }}>{b.sellable}</td>
                    <td>{b.reorderThreshold}</td>
                    <td>
                      {critical && <span className="badge badge-rejected">OUT OF STOCK</span>}
                      {low && <span className="badge badge-pending">LOW STOCK</span>}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <div className="alert alert-warning" style={{ marginTop: 20 }}>
        💡 To adjust stock levels, use the Inventory API endpoints directly for now.
        A full inline adjustment UI is planned for Sprint 7.
      </div>
    </>
  );
}

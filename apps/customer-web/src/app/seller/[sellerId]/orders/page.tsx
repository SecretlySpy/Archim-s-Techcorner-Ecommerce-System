"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { Button } from "@archim/ui-components";

interface SellerOrder {
  id: string;
  parentOrderId: string;
  status: string;
  subtotal: number;
  createdAt: string;
  customerEmail: string;
  recipientName: string;
  destinationCity: string;
  itemCount: number;
}

export default function SellerOrdersPage() {
  const params = useParams();
  const sellerId = params.sellerId as string;

  const [orders, setOrders] = useState<SellerOrder[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Dispatch modal / inline state
  const [shippingOrderId, setShippingOrderId] = useState<string | null>(null);
  const [courier, setCourier] = useState("J_AND_T");
  const [trackingNumber, setTrackingNumber] = useState("");
  const [shippingLoading, setShippingLoading] = useState(false);
  const [shipError, setShipError] = useState<string | null>(null);

  const fetchOrders = useCallback(async () => {
    try {
      setLoading(true);
      const data = await api.get<SellerOrder[]>(`/api/v1/sellers/${sellerId}/orders`);
      setOrders(data);
    } catch (e: any) {
      setError(e instanceof ApiError ? e.message : "Failed to load seller orders.");
    } finally {
      setLoading(false);
    }
  }, [sellerId]);

  useEffect(() => {
    if (sellerId) {
      fetchOrders();
    }
  }, [sellerId, fetchOrders]);

  const handleDispatch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!shippingOrderId || !trackingNumber.trim()) return;

    try {
      setShippingLoading(true);
      setShipError(null);
      await api.post(`/api/v1/seller-orders/${shippingOrderId}/shipments`, {
        courierCode: courier,
        trackingNumber: trackingNumber.trim(),
      });
      setShippingOrderId(null);
      setTrackingNumber("");
      await fetchOrders();
    } catch (e: any) {
      setShipError(e instanceof ApiError ? e.message : "Failed to record shipment.");
    } finally {
      setShippingLoading(false);
    }
  };

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <div>
          <h1 className="page-title" style={{ margin: 0 }}>Orders & Fulfillment</h1>
          <p style={{ margin: "4px 0 0", color: "var(--color-muted, #9fbfae)", fontSize: 14 }}>
            Review paid orders, fulfill packages, and record dispatch tracking
          </p>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <Button variant="outline" size="sm" onClick={fetchOrders} disabled={loading}>
            Refresh
          </Button>
          <Link href={`/seller/${sellerId}/inventory`}>
            <Button variant="outline" size="sm">Manage Stock</Button>
          </Link>
        </div>
      </div>

      {error && <div className="alert alert-error" style={{ marginBottom: 16 }}>{error}</div>}

      {/* Shipment Modal / Form Drawer */}
      {shippingOrderId && (
        <div
          className="card"
          style={{
            marginBottom: 20,
            border: "1px solid var(--color-accent, #b6f25c)",
            backgroundColor: "rgba(27, 45, 37, 0.95)",
            padding: 20,
          }}
        >
          <h3 style={{ margin: "0 0 12px 0", fontSize: 16 }}>
            Dispatch Shipment for Order {shippingOrderId}
          </h3>
          {shipError && <div className="alert alert-error" style={{ marginBottom: 12 }}>{shipError}</div>}
          <form onSubmit={handleDispatch} style={{ display: "flex", flexWrap: "wrap", gap: 12, alignItems: "flex-end" }}>
            <div style={{ flex: 1, minWidth: 160 }}>
              <label style={{ display: "block", fontSize: 13, marginBottom: 4, color: "var(--color-muted, #9fbfae)" }}>
                Courier Service
              </label>
              <select
                value={courier}
                onChange={(e) => setCourier(e.target.value)}
                style={{
                  width: "100%",
                  padding: "8px 12px",
                  borderRadius: 6,
                  border: "1px solid rgba(255, 255, 255, 0.15)",
                  backgroundColor: "#0c1a14",
                  color: "#f4fbf6",
                  fontSize: 14,
                }}
              >
                <option value="J_AND_T">J&T Express</option>
                <option value="LALAMOVE">Lalamove</option>
                <option value="NINJAVAN">Ninja Van</option>
                <option value="GRAB_EXPRESS">GrabExpress</option>
                <option value="STANDARD">Standard Courier</option>
              </select>
            </div>

            <div style={{ flex: 2, minWidth: 200 }}>
              <label style={{ display: "block", fontSize: 13, marginBottom: 4, color: "var(--color-muted, #9fbfae)" }}>
                Tracking Number / Waybill
              </label>
              <input
                type="text"
                placeholder="e.g. JT9876543210"
                value={trackingNumber}
                onChange={(e) => setTrackingNumber(e.target.value)}
                required
                style={{
                  width: "100%",
                  padding: "8px 12px",
                  borderRadius: 6,
                  border: "1px solid rgba(255, 255, 255, 0.15)",
                  backgroundColor: "#0c1a14",
                  color: "#f4fbf6",
                  fontSize: 14,
                }}
              />
            </div>

            <div style={{ display: "flex", gap: 8 }}>
              <Button variant="primary" type="submit" disabled={shippingLoading}>
                {shippingLoading ? "Dispatching..." : "Confirm Dispatch"}
              </Button>
              <Button variant="outline" type="button" onClick={() => setShippingOrderId(null)}>
                Cancel
              </Button>
            </div>
          </form>
        </div>
      )}

      {/* Orders List Table */}
      <div className="card" style={{ padding: 0, overflow: "hidden" }}>
        {loading ? (
          <div style={{ textAlign: "center", padding: "40px 20px" }}>
            <span className="spinner" />
          </div>
        ) : orders.length === 0 ? (
          <div style={{ padding: "32px 20px", textAlign: "center", color: "var(--color-muted, #9fbfae)" }}>
            No customer orders assigned to this seller yet.
          </div>
        ) : (
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 14 }}>
            <thead>
              <tr style={{ borderBottom: "1px solid rgba(255, 255, 255, 0.1)", textAlign: "left", color: "var(--color-muted, #9fbfae)" }}>
                <th style={{ padding: "12px 16px" }}>Order ID</th>
                <th style={{ padding: "12px 16px" }}>Recipient & Destination</th>
                <th style={{ padding: "12px 16px" }}>Items</th>
                <th style={{ padding: "12px 16px" }}>Status</th>
                <th style={{ padding: "12px 16px", textAlign: "right" }}>Subtotal</th>
                <th style={{ padding: "12px 16px", textAlign: "center" }}>Action</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((o) => {
                const canShip = o.status === "PAID" || o.status === "PROCESSING";
                const isShipped = o.status === "SHIPPED" || o.status === "DELIVERED";

                return (
                  <tr key={o.id} style={{ borderBottom: "1px solid rgba(255, 255, 255, 0.06)" }}>
                    <td style={{ padding: "12px 16px" }}>
                      <div style={{ fontWeight: 600 }}>{o.id.substring(0, 8)}...</div>
                      <div style={{ fontSize: 12, color: "var(--color-muted, #9fbfae)" }}>
                        {new Date(o.createdAt).toLocaleDateString()}
                      </div>
                    </td>
                    <td style={{ padding: "12px 16px" }}>
                      <div style={{ fontWeight: 500 }}>{o.recipientName}</div>
                      <div style={{ fontSize: 12, color: "var(--color-muted, #9fbfae)" }}>
                        {o.destinationCity} • {o.customerEmail}
                      </div>
                    </td>
                    <td style={{ padding: "12px 16px" }}>{o.itemCount} unit(s)</td>
                    <td style={{ padding: "12px 16px" }}>
                      <span
                        style={{
                          padding: "2px 8px",
                          borderRadius: 4,
                          fontSize: 12,
                          fontWeight: 600,
                          backgroundColor:
                            isShipped
                              ? "rgba(115, 220, 140, 0.15)"
                              : canShip
                              ? "rgba(182, 242, 92, 0.15)"
                              : "rgba(255, 255, 255, 0.1)",
                          color:
                            isShipped
                              ? "#73dc8c"
                              : canShip
                              ? "#b6f25c"
                              : "var(--color-muted, #9fbfae)",
                        }}
                      >
                        {o.status}
                      </span>
                    </td>
                    <td style={{ padding: "12px 16px", textAlign: "right", fontWeight: 600 }}>
                      ₱{o.subtotal.toLocaleString("en-PH", { minimumFractionDigits: 2 })}
                    </td>
                    <td style={{ padding: "12px 16px", textAlign: "center" }}>
                      {canShip ? (
                        <Button
                          variant="primary"
                          size="sm"
                          onClick={() => {
                            setShippingOrderId(o.id);
                            setShipError(null);
                          }}
                        >
                          Dispatch
                        </Button>
                      ) : isShipped ? (
                        <span style={{ fontSize: 12, color: "#73dc8c" }}>✓ Dispatched</span>
                      ) : (
                        <span style={{ fontSize: 12, color: "var(--color-muted, #9fbfae)" }}>{o.status}</span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

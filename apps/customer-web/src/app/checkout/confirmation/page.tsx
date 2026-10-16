"use client";

import { Suspense, useCallback, useEffect, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { Button } from "@archim/ui-components";

interface OrderDetail {
  id: string;
  totalAmount: number;
  currencyCode: string;
  status: string;
  createdAt: string;
  shippingAddress: {
    recipientName: string;
    phoneNumber: string;
    streetLine1: string;
    streetLine2?: string;
    city: string;
    provinceState: string;
    postalCode: string;
    countryCode: string;
  } | null;
  sellerOrders: Array<{
    id: string;
    sellerName: string;
    subtotal: number;
    status: string;
    items: Array<{
      variantId: number;
      productName: string;
      variantName: string;
      sku: string;
      unitPrice: number;
      quantity: number;
      lineTotal: number;
    }>;
  }>;
}

interface PaymentStatusInfo {
  orderId: string;
  paymentId: string;
  paymentStatus: string;
  orderStatus: string;
  amount: number;
  currency: string;
}

function ConfirmationContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const orderId = searchParams.get("order") || searchParams.get("orderId");
  const sessionId = searchParams.get("session");

  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [paymentStatus, setPaymentStatus] = useState<PaymentStatusInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [simulating, setSimulating] = useState(false);

  const fetchStatus = useCallback(async () => {
    if (!orderId) {
      setError("No order ID provided.");
      setLoading(false);
      return;
    }

    try {
      const [orderData, paymentData] = await Promise.all([
        api.get<OrderDetail>(`/api/v1/orders/${orderId}`),
        api.get<PaymentStatusInfo>(`/api/v1/orders/${orderId}/payment-status`).catch(() => null),
      ]);
      setOrder(orderData);
      setPaymentStatus(paymentData);
    } catch (e: any) {
      setError(e instanceof ApiError ? e.message : "Failed to load order details.");
    } finally {
      setLoading(false);
    }
  }, [orderId]);

  useEffect(() => {
    fetchStatus();
  }, [fetchStatus]);

  const handleSimulatePayment = async () => {
    if (!orderId) return;
    setSimulating(true);
    try {
      await api.post("/api/v1/payments/webhooks/mock", {
        orderPublicId: orderId,
        status: "SUCCEEDED",
        providerPaymentId: sessionId || "sim_pay_" + Date.now(),
      });
      await fetchStatus();
    } catch (e: any) {
      alert("Failed to simulate payment webhook: " + (e.message || "Unknown error"));
    } finally {
      setSimulating(false);
    }
  };

  if (loading) {
    return (
      <div className="container" style={{ textAlign: "center", padding: "60px 20px" }}>
        <span className="spinner" />
        <p style={{ marginTop: 16, color: "var(--color-muted, #9fbfae)" }}>Verifying payment confirmation...</p>
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className="container" style={{ maxWidth: 640, padding: "40px 20px" }}>
        <div className="alert alert-error" style={{ marginBottom: 20 }}>
          {error || "Order not found."}
        </div>
        <Link href="/" className="btn btn-secondary">
          Return to Store
        </Link>
      </div>
    );
  }

  const isPaid = order.status === "PAID" || paymentStatus?.paymentStatus === "CAPTURED";

  return (
    <div className="container" style={{ maxWidth: 800, padding: "40px 20px" }}>
      {/* Status Hero Banner */}
      <div
        className="card"
        style={{
          textAlign: "center",
          padding: "36px 24px",
          marginBottom: 28,
          border: isPaid ? "1px solid #73dc8c" : "1px solid #e6a15c",
          backgroundColor: "var(--color-panel, #1b2d25)",
        }}
      >
        <div style={{ fontSize: 48, marginBottom: 12 }}>{isPaid ? "🎉" : "⏳"}</div>
        <h1 style={{ fontSize: 26, fontWeight: 700, margin: "0 0 8px 0" }}>
          {isPaid ? "Payment Confirmed & Order Placed!" : "Order Awaiting Payment Confirmation"}
        </h1>
        <p style={{ color: "var(--color-muted, #9fbfae)", margin: "0 0 16px 0" }}>
          Order reference: <strong style={{ color: "#f4fbf6" }}>{order.id}</strong>
        </p>

        <div style={{ display: "inline-flex", gap: 8, alignItems: "center", justifyContent: "center" }}>
          <span
            style={{
              padding: "4px 12px",
              borderRadius: 6,
              fontSize: 13,
              fontWeight: 600,
              backgroundColor: isPaid ? "rgba(115, 220, 140, 0.2)" : "rgba(230, 161, 92, 0.2)",
              color: isPaid ? "#73dc8c" : "#e6a15c",
            }}
          >
            Order: {order.status}
          </span>
          {paymentStatus && (
            <span
              style={{
                padding: "4px 12px",
                borderRadius: 6,
                fontSize: 13,
                fontWeight: 600,
                backgroundColor: isPaid ? "rgba(115, 220, 140, 0.2)" : "rgba(230, 161, 92, 0.2)",
                color: isPaid ? "#73dc8c" : "#e6a15c",
              }}
            >
              Payment: {paymentStatus.paymentStatus}
            </span>
          )}
        </div>

        {!isPaid && (
          <div style={{ marginTop: 24 }}>
            <p style={{ fontSize: 14, color: "var(--color-muted, #9fbfae)", marginBottom: 12 }}>
              If you just completed a test hosted payment, simulate the incoming webhook notification:
            </p>
            <Button
              variant="primary"
              onClick={handleSimulatePayment}
              disabled={simulating}
            >
              {simulating ? "Processing Webhook..." : "Complete Mock Payment Webhook"}
            </Button>
          </div>
        )}
      </div>

      {/* Order Details & Summary */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr", gap: 20 }}>
        {/* Shipping Destination */}
        {order.shippingAddress && (
          <div className="card">
            <h3 style={{ fontSize: 18, marginBottom: 12 }}>Shipping Destination</h3>
            <p style={{ margin: 0, fontWeight: 600 }}>{order.shippingAddress.recipientName}</p>
            <p style={{ margin: "4px 0", color: "var(--color-muted, #9fbfae)" }}>
              {order.shippingAddress.phoneNumber}
            </p>
            <p style={{ margin: 0, color: "var(--color-muted, #9fbfae)" }}>
              {order.shippingAddress.streetLine1}
              {order.shippingAddress.streetLine2 && `, ${order.shippingAddress.streetLine2}`},{" "}
              {order.shippingAddress.city}, {order.shippingAddress.provinceState}{" "}
              {order.shippingAddress.postalCode}, {order.shippingAddress.countryCode}
            </p>
          </div>
        )}

        {/* Multi-Seller Breakdown */}
        <div className="card">
          <h3 style={{ fontSize: 18, marginBottom: 16 }}>Items & Fulfillment</h3>
          {order.sellerOrders.map((so) => (
            <div
              key={so.id}
              style={{
                marginBottom: 20,
                paddingBottom: 16,
                borderBottom: "1px solid rgba(255, 255, 255, 0.08)",
              }}
            >
              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 10 }}>
                <span style={{ fontWeight: 600, color: "var(--color-accent, #b6f25c)" }}>
                  Seller: {so.sellerName}
                </span>
                <span style={{ fontSize: 13, color: "var(--color-muted, #9fbfae)" }}>
                  Status: {so.status}
                </span>
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                {so.items.map((it, idx) => (
                  <div key={idx} style={{ display: "flex", justifyContent: "space-between", fontSize: 14 }}>
                    <span>
                      {it.productName} ({it.variantName}) × {it.quantity}
                    </span>
                    <span style={{ fontWeight: 600 }}>
                      ₱{it.lineTotal.toLocaleString("en-PH", { minimumFractionDigits: 2 })}
                    </span>
                  </div>
                ))}
              </div>
              <div style={{ textAlign: "right", marginTop: 8, fontSize: 14 }}>
                Subtotal: ₱{so.subtotal.toLocaleString("en-PH", { minimumFractionDigits: 2 })}
              </div>
            </div>
          ))}

          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              marginTop: 16,
              fontSize: 18,
              fontWeight: 700,
            }}
          >
            <span>Grand Total:</span>
            <span style={{ color: "var(--color-price, #e6a15c)" }}>
              ₱{order.totalAmount.toLocaleString("en-PH", { minimumFractionDigits: 2 })} {order.currencyCode}
            </span>
          </div>
        </div>

        {/* Action Controls */}
        <div style={{ display: "flex", gap: 12, marginTop: 12 }}>
          <Link href="/account" style={{ flex: 1, textDecoration: "none" }}>
            <Button variant="outline" style={{ width: "100%" }}>
              View My Orders
            </Button>
          </Link>
          <Link href="/" style={{ flex: 1, textDecoration: "none" }}>
            <Button variant="primary" style={{ width: "100%" }}>
              Continue Shopping
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
}

export default function ConfirmationPage() {
  return (
    <Suspense
      fallback={
        <div className="container" style={{ textAlign: "center", padding: "60px 20px" }}>
          <span className="spinner" />
        </div>
      }
    >
      <ConfirmationContent />
    </Suspense>
  );
}

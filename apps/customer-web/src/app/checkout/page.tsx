"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  api,
  ApiError,
  addressesApi,
  checkoutApi,
  type Address,
  type CartView,
  type CurrentUser,
  type CheckoutQuoteResponse,
} from "@/lib/api";
import { Button } from "@archim/ui-components";

export default function CheckoutPage() {
  const router = useRouter();
  const [cart, setCart] = useState<CartView | null>(null);
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [processing, setProcessing] = useState(false);

  const [addresses, setAddresses] = useState<Address[]>([]);
  const [selectedAddressId, setSelectedAddressId] = useState<string | null>(null);

  const [paymentMethod, setPaymentMethod] = useState("MOCK_HOSTED");
  const [shippingMethod, setShippingMethod] = useState("standard");

  const [quote, setQuote] = useState<CheckoutQuoteResponse | null>(null);
  const [quoteLoading, setQuoteLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      api.get<CartView>("/api/v1/cart"),
      api.get<CurrentUser>("/api/v1/users/me"),
      addressesApi.list().catch(() => []),
    ])
      .then(([c, u, addrs]) => {
        setCart(c);
        setUser(u);
        setAddresses(addrs);
        if (addrs.length > 0) {
          const def = addrs.find((a) => a.isDefault) || addrs[0];
          setSelectedAddressId(def.id);
        }
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 401) {
          router.replace("/login");
          return;
        }
        setError("Failed to load checkout.");
      })
      .finally(() => setLoading(false));
  }, [router]);

  // Request authoritative server-side quote when address or shipping method changes
  useEffect(() => {
    if (!selectedAddressId || !cart || cart.totalItems === 0) return;
    setQuoteLoading(true);
    setError(null);
    checkoutApi
      .createQuote(selectedAddressId, shippingMethod)
      .then(setQuote)
      .catch((err) => {
        setQuote(null);
        setError(err instanceof ApiError ? err.message : "Failed to calculate quote.");
      })
      .finally(() => setQuoteLoading(false));
  }, [selectedAddressId, shippingMethod, cart]);

  async function handleCheckout() {
    if (!quote) return;
    setProcessing(true);
    setError(null);

    try {
      const idempotencyKey = crypto.randomUUID();
      const result = await checkoutApi.submitCheckout(
        quote.quoteId,
        idempotencyKey,
        paymentMethod,
        window.location.origin
      );

      if (result.redirectUrl) {
        window.location.href = result.redirectUrl;
        return;
      }

      alert(`Order ${result.orderId} placed successfully! Status: ${result.status}`);
      router.push("/account");
    } catch (e: any) {
      setError(e instanceof ApiError ? e.message : "Checkout failed. Please try again.");
    } finally {
      setProcessing(false);
    }
  }

  if (loading) {
    return (
      <div className="container">
        <div className="loading-center">
          <span className="spinner" />
        </div>
      </div>
    );
  }

  if (error && !cart) {
    return (
      <div className="container">
        <div className="alert alert-error">{error || "No cart"}</div>
        <Link href="/cart">Back to cart</Link>
      </div>
    );
  }

  if (!cart || cart.totalItems === 0) {
    return (
      <div className="container">
        Cart empty. <Link href="/">Shop</Link>
      </div>
    );
  }

  return (
    <div className="container" style={{ maxWidth: 800 }}>
      <h1 className="page-title">Checkout</h1>
      <p className="links">
        <Link href="/cart">← Back to cart</Link>
      </p>

      {/* Cart summary */}
      <div className="card" style={{ marginBottom: 16 }}>
        <h3>Order Summary</h3>
        {cart.sellerGroups.map((group) => (
          <div key={group.sellerPublicId} style={{ marginBottom: 8 }}>
            <strong>{group.sellerName}</strong>
            {group.items.map((item) => (
              <div
                key={item.variantPublicId}
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  fontSize: "0.9em",
                  marginTop: 4,
                }}
              >
                <span>
                  {item.productName} ({item.variantName}) x{item.quantity}
                </span>
                <span>₱{item.lineTotal.toFixed(2)}</span>
              </div>
            ))}
            <div style={{ textAlign: "right", fontWeight: 500, marginTop: 4 }}>
              Subtotal: ₱{group.subtotal.toFixed(2)}
            </div>
          </div>
        ))}

        <div className="cart-summary" style={{ borderTop: "1px solid var(--border)", paddingTop: 8 }}>
          <span>Items Subtotal</span>
          <span>₱{(quote?.subtotal ?? cart.cartTotal).toFixed(2)}</span>
        </div>
        <div className="cart-summary">
          <span>Delivery Fee</span>
          <span>₱{(quote?.shippingFee ?? (shippingMethod === "express" ? 300 : 150)).toFixed(2)}</span>
        </div>
        <div className="cart-summary">
          <span>VAT / Taxes (Included)</span>
          <span>₱{(quote?.taxAmount ?? 0).toFixed(2)}</span>
        </div>
        <div
          className="cart-summary"
          style={{ borderTop: "1px solid var(--border)", paddingTop: 8, fontWeight: 700 }}
        >
          <span>Grand Total</span>
          <span>
            {quoteLoading ? "Calculating…" : `₱${(quote?.totalAmount ?? cart.cartTotal + 150).toFixed(2)}`}
          </span>
        </div>
      </div>

      {/* Address */}
      <div className="card" style={{ marginBottom: 16 }}>
        <h3>Shipping Address</h3>
        {addresses.length > 0 ? (
          <select
            value={selectedAddressId || ""}
            onChange={(e) => setSelectedAddressId(e.target.value)}
            style={{ width: "100%", padding: 8 }}
          >
            {addresses.map((a) => (
              <option key={a.id} value={a.id}>
                {a.recipientName} - {a.line1}, {a.city} {a.isDefault ? "(default)" : ""}
              </option>
            ))}
          </select>
        ) : (
          <p className="muted">
            No shipping addresses saved. Please add an address in your <Link href="/account">Account</Link>.
          </p>
        )}
      </div>

      {/* Shipping Method */}
      <div className="card" style={{ marginBottom: 16 }}>
        <h3>Shipping Method</h3>
        <select
          style={{ width: "100%", padding: 8 }}
          value={shippingMethod}
          onChange={(e) => setShippingMethod(e.target.value)}
        >
          <option value="standard">Standard Delivery - ₱150 (3-5 business days)</option>
          <option value="express">Express Delivery - ₱300 (1-2 business days)</option>
        </select>
      </div>

      {/* Payment */}
      <div className="card" style={{ marginBottom: 16 }}>
        <h3>Payment Method</h3>
        <select
          value={paymentMethod}
          onChange={(e) => setPaymentMethod(e.target.value)}
          style={{ width: "100%", padding: 8 }}
        >
          <option value="MOCK_HOSTED">Hosted Payment Gateway (Mock / Sandbox)</option>
        </select>
        <p className="muted" style={{ fontSize: 13, marginTop: 8 }}>
          Hosted checkout session will open for payment authorization.
        </p>
      </div>

      {error && <div className="alert alert-error" style={{ marginBottom: 16 }}>{error}</div>}

      <Button
        className="btn-full"
        disabled={processing || quoteLoading || !quote || cart.totalItems === 0 || !selectedAddressId}
        onClick={handleCheckout}
      >
        {processing
          ? "Securing stock & processing…"
          : quoteLoading
          ? "Calculating quote…"
          : `Confirm & Pay ₱${(quote?.totalAmount ?? 0).toFixed(2)}`}
      </Button>

      <p className="links" style={{ marginTop: 16, textAlign: "center" }}>
        Prices and stock are authoritatively locked for 15 minutes upon confirmation.
      </p>
    </div>
  );
}

"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, ApiError, addressesApi, type Address, type CartView, type CurrentUser } from "@/lib/api";
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

  const [paymentMethod, setPaymentMethod] = useState("mock");
  const [shippingMethod, setShippingMethod] = useState("standard");

  useEffect(() => {
    setLoading(true);
    Promise.all([
      api.get<CartView>("/api/v1/cart"),
      api.get<CurrentUser>("/api/v1/users/me"),
      addressesApi.list().catch(() => [])
    ]).then(([c, u, addrs]) => {
      setCart(c);
      setUser(u);
      setAddresses(addrs);
      if (addrs.length > 0) {
        const def = addrs.find(a => a.isDefault) || addrs[0];
        setSelectedAddressId(def.id);
      }
    }).catch((err) => {
      if (err instanceof ApiError && err.status === 401) {
        router.replace("/login");
        return;
      }
      setError("Failed to load checkout.");
    }).finally(() => setLoading(false));
  }, [router]);

  const shippingCost = shippingMethod === "express" ? 300 : 150;
  const grandTotal = (cart?.cartTotal || 0) + shippingCost;

  async function handleCheckout() {
    if (!cart || cart.totalItems === 0) return;
    setProcessing(true);
    setError(null);
    const selectedAddr = addresses.find(a => a.id === selectedAddressId);
    const addrForApi = selectedAddr ? {
      name: selectedAddr.recipientName,
      line1: selectedAddr.line1,
      city: selectedAddr.city,
      postal: selectedAddr.postalCode,
      country: selectedAddr.country
    } : { name: "Default", line1: "123 Tech St", city: "Manila", postal: "1000", country: "PH" };

    try {
      // T11 reval + T18 confirm via stub
      const result = await api.post<{orderId: string, status: string, total: number, paymentRedirect?: string}>("/api/v1/cart/checkout", {
        address: addrForApi,
        paymentMethod,
        shippingMethod
      });
      if (result.paymentRedirect) {
        window.location.href = result.paymentRedirect;
        return;
      }
      alert(`Order ${result.orderId} confirmed! Status: ${result.status}. Total with shipping ₱${grandTotal.toFixed(2)}. (Mock payment)`);
      router.push("/account");
    } catch (e: any) {
      setError(e instanceof ApiError ? e.message : "Checkout failed. Please try again.");
    } finally {
      setProcessing(false);
    }
  }

  if (loading) return <div className="container"><div className="loading-center"><span className="spinner" /></div></div>;

  if (error || !cart) return <div className="container"><div className="alert alert-error">{error || "No cart"}</div><Link href="/cart">Back to cart</Link></div>;

  if (cart.totalItems === 0) return <div className="container">Cart empty. <Link href="/">Shop</Link></div>;

  return (
    <div className="container" style={{ maxWidth: 800 }}>
      <h1 className="page-title">Checkout</h1>
      <p className="links"><Link href="/cart">← Back to cart</Link></p>

      {/* Cart summary */}
      <div className="card" style={{ marginBottom: 16 }}>
        <h3>Order Summary</h3>
        {cart.sellerGroups.map(group => (
          <div key={group.sellerPublicId} style={{ marginBottom: 8 }}>
            <strong>{group.sellerName}</strong>
            {group.items.map(item => (
              <div key={item.variantPublicId} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.9em' }}>
                <span>{item.productName} x{item.quantity}</span>
                <span>₱{item.lineTotal.toFixed(2)}</span>
              </div>
            ))}
            <div style={{ textAlign: 'right', fontWeight: 500 }}>Subtotal: ₱{group.subtotal.toFixed(2)}</div>
          </div>
        ))}
        <div className="cart-summary" style={{ borderTop: '1px solid var(--border)', paddingTop: 8 }}>
          <span>Items</span>
          <span>₱{cart.cartTotal.toFixed(2)}</span>
        </div>
        <div className="cart-summary">
          <span>Shipping</span>
          <span>₱{shippingCost}</span>
        </div>
        <div className="cart-summary" style={{ borderTop: '1px solid var(--border)', paddingTop: 8, fontWeight: 700 }}>
          <span>Total</span>
          <span>₱{grandTotal.toFixed(2)}</span>
        </div>
      </div>

      {/* Address (T12) */}
      <div className="card" style={{ marginBottom: 16 }}>
        <h3>Shipping Address</h3>
        {addresses.length > 0 ? (
          <select 
            value={selectedAddressId || ''} 
            onChange={e => setSelectedAddressId(e.target.value)}
            style={{ width: '100%', padding: 8 }}
          >
            {addresses.map(a => (
              <option key={a.id} value={a.id}>
                {a.recipientName} - {a.line1}, {a.city} {a.isDefault ? '(default)' : ''}
              </option>
            ))}
          </select>
        ) : (
          <p className="muted">No addresses saved. Add in account page. Using default for now.</p>
        )}
        <small className="muted">Select from your saved addresses (T12). Full CRUD in account.</small>
      </div>

      {/* Shipping (T12/T13 stub) */}
      <div className="card" style={{ marginBottom: 16 }}>
        <h3>Shipping Method</h3>
        <select 
          style={{ width: '100%', padding: 8 }} 
          value={shippingMethod} 
          onChange={e => setShippingMethod(e.target.value)}
        >
          <option value="standard">Standard Shipping - ₱150 (3-5 days)</option>
          <option value="express">Express - ₱300 (1-2 days)</option>
        </select>
        <small className="muted">Stub for shipping selection per plan.</small>
      </div>

      {/* Payment (T15-18 stub, mock hosted) */}
      <div className="card" style={{ marginBottom: 16 }}>
        <h3>Payment</h3>
        <select value={paymentMethod} onChange={e => setPaymentMethod(e.target.value)} style={{ width: '100%', padding: 8 }}>
          <option value="mock">Mock Hosted Payment (simulates success)</option>
        </select>
        <p className="muted">Follows mandatory two-transaction model. No lock during "provider" call. (Stub)</p>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <Button 
        className="btn-full" 
        disabled={processing || cart.totalItems === 0}
        onClick={handleCheckout}
      >
        {processing ? "Processing..." : `Pay ₱${grandTotal.toFixed(2)} (Mock)`}
      </Button>

      <p className="links" style={{ marginTop: 16, textAlign: 'center' }}>
        By checking out you agree to terms. (Stub per plan)
      </p>
    </div>
  );
}

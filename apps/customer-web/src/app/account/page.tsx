"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  api,
  ApiError,
  addressesApi,
  ordersApi,
  type Address,
  type CurrentUser,
  type OrderSummary,
  type ShipmentSummary,
} from "@/lib/api";
import { Button } from "@archim/ui-components";

export default function AccountPage() {
  const router = useRouter();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [activeTab, setActiveTab] = useState<"orders" | "addresses" | "profile">("orders");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Orders state
  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);
  const [orderDetails, setOrderDetails] = useState<any | null>(null);
  const [orderShipments, setOrderShipments] = useState<ShipmentSummary[]>([]);
  const [orderLoading, setOrderLoading] = useState(false);

  // Refund request state
  const [refundReason, setRefundReason] = useState("");
  const [refundAmount, setRefundAmount] = useState("");
  const [refundSubmitting, setRefundSubmitting] = useState(false);
  const [refundMessage, setRefundMessage] = useState<string | null>(null);

  // Addresses state
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [showAddAddress, setShowAddAddress] = useState(false);
  const [newAddr, setNewAddr] = useState({
    recipientName: "",
    phone: "",
    line1: "",
    line2: "",
    city: "",
    state: "NCR",
    postalCode: "1000",
    country: "PH",
    addressType: "SHIPPING",
    isDefault: false,
  });
  const [addrSaving, setAddrSaving] = useState(false);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      api.get<CurrentUser>("/api/v1/users/me"),
      ordersApi.list().catch(() => []),
      addressesApi.list().catch(() => []),
    ])
      .then(([u, ords, addrs]) => {
        setUser(u);
        setOrders(ords);
        setAddresses(addrs);
      })
      .catch((e: any) => {
        if (e instanceof ApiError && e.status === 401) {
          router.replace("/login");
          return;
        }
        setError("Failed to load account information.");
      })
      .finally(() => setLoading(false));
  }, [router]);

  const loadOrderDetails = async (id: string) => {
    if (selectedOrderId === id) {
      setSelectedOrderId(null);
      setOrderDetails(null);
      setOrderShipments([]);
      return;
    }

    try {
      setOrderLoading(true);
      setSelectedOrderId(id);
      setRefundMessage(null);
      const [detail, shipments] = await Promise.all([
        ordersApi.get(id),
        ordersApi.getShipments(id).catch(() => []),
      ]);
      setOrderDetails(detail);
      setOrderShipments(shipments);
      setRefundAmount(detail.totalAmount?.toString() || "");
    } catch (e: any) {
      alert("Failed to load order details: " + (e.message || "Unknown error"));
    } finally {
      setOrderLoading(false);
    }
  };

  const handleRequestRefund = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedOrderId || !refundReason.trim()) return;

    try {
      setRefundSubmitting(true);
      setRefundMessage(null);
      await ordersApi.requestRefund(selectedOrderId, {
        requestedAmount: parseFloat(refundAmount) || orderDetails.totalAmount,
        reason: refundReason.trim(),
      });
      setRefundMessage("Refund request submitted successfully. Our finance team will review it.");
      setRefundReason("");
    } catch (e: any) {
      setRefundMessage("Error submitting refund: " + (e.message || "Unknown error"));
    } finally {
      setRefundSubmitting(false);
    }
  };

  const handleAddAddress = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setAddrSaving(true);
      const created = await addressesApi.create(newAddr);
      setAddresses([...addresses, created]);
      setShowAddAddress(false);
      setNewAddr({
        recipientName: "",
        phone: "",
        line1: "",
        line2: "",
        city: "",
        state: "NCR",
        postalCode: "1000",
        country: "PH",
        addressType: "SHIPPING",
        isDefault: false,
      });
    } catch (e: any) {
      alert("Failed to save address: " + (e.message || "Unknown error"));
    } finally {
      setAddrSaving(false);
    }
  };

  const handleSetDefaultAddress = async (id: string) => {
    try {
      await addressesApi.setDefault(id);
      const refreshed = await addressesApi.list();
      setAddresses(refreshed);
    } catch (e: any) {
      alert("Failed to update default address: " + (e.message || "Unknown error"));
    }
  };

  const handleDeleteAddress = async (id: string) => {
    if (!confirm("Are you sure you want to remove this address?")) return;
    try {
      await addressesApi.delete(id);
      setAddresses(addresses.filter((a) => a.id !== id));
    } catch (e: any) {
      alert("Failed to delete address: " + (e.message || "Unknown error"));
    }
  };

  if (loading) {
    return (
      <div className="container" style={{ textAlign: "center", padding: "60px 20px" }}>
        <span className="spinner" />
      </div>
    );
  }

  if (error || !user) {
    return (
      <div className="container" style={{ padding: "40px 20px" }}>
        <div className="alert alert-error">{error || "Please log in to view account."}</div>
        <Link href="/login" style={{ textDecoration: "none" }}>
          <Button variant="primary">Sign In</Button>
        </Link>
      </div>
    );
  }

  return (
    <div className="container" style={{ maxWidth: 900, padding: "40px 20px" }}>
      {/* Header Profile Summary */}
      <div
        className="card"
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          flexWrap: "wrap",
          gap: 16,
          marginBottom: 24,
        }}
      >
        <div>
          <h1 style={{ fontSize: 24, margin: "0 0 4px 0" }}>
            {user.displayName ? user.displayName : user.email.split("@")[0]}
          </h1>
          <p style={{ margin: 0, color: "var(--color-muted, #9fbfae)", fontSize: 14 }}>
            {user.email} • Status: <span style={{ color: "var(--color-accent, #b6f25c)" }}>{user.status}</span>
          </p>
        </div>
        <div style={{ display: "flex", gap: 10 }}>
          {user.roles.includes("ROLE_SELLER") && (
            <Link href="/seller" style={{ textDecoration: "none" }}>
              <Button variant="outline" size="sm">Seller Center</Button>
            </Link>
          )}
        </div>
      </div>

      {/* Tabs Navigation */}
      <div
        style={{
          display: "flex",
          gap: 8,
          borderBottom: "1px solid rgba(255, 255, 255, 0.1)",
          marginBottom: 24,
        }}
      >
        <button
          onClick={() => setActiveTab("orders")}
          style={{
            padding: "10px 18px",
            background: "none",
            border: "none",
            borderBottom: activeTab === "orders" ? "2px solid var(--color-accent, #b6f25c)" : "none",
            color: activeTab === "orders" ? "#f4fbf6" : "var(--color-muted, #9fbfae)",
            fontWeight: activeTab === "orders" ? 700 : 500,
            cursor: "pointer",
            fontSize: 15,
          }}
        >
          My Orders ({orders.length})
        </button>
        <button
          onClick={() => setActiveTab("addresses")}
          style={{
            padding: "10px 18px",
            background: "none",
            border: "none",
            borderBottom: activeTab === "addresses" ? "2px solid var(--color-accent, #b6f25c)" : "none",
            color: activeTab === "addresses" ? "#f4fbf6" : "var(--color-muted, #9fbfae)",
            fontWeight: activeTab === "addresses" ? 700 : 500,
            cursor: "pointer",
            fontSize: 15,
          }}
        >
          Address Book ({addresses.length})
        </button>
        <button
          onClick={() => setActiveTab("profile")}
          style={{
            padding: "10px 18px",
            background: "none",
            border: "none",
            borderBottom: activeTab === "profile" ? "2px solid var(--color-accent, #b6f25c)" : "none",
            color: activeTab === "profile" ? "#f4fbf6" : "var(--color-muted, #9fbfae)",
            fontWeight: activeTab === "profile" ? 700 : 500,
            cursor: "pointer",
            fontSize: 15,
          }}
        >
          Profile & Security
        </button>
      </div>

      {/* TAB 1: Orders & Tracking */}
      {activeTab === "orders" && (
        <div>
          {orders.length === 0 ? (
            <div className="card" style={{ textAlign: "center", padding: "40px 20px" }}>
              <p style={{ color: "var(--color-muted, #9fbfae)", marginBottom: 16 }}>You have not placed any orders yet.</p>
              <Link href="/" style={{ textDecoration: "none" }}>
                <Button variant="primary">Browse Catalog</Button>
              </Link>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
              {orders.map((o) => {
                const isExpanded = selectedOrderId === o.id;
                return (
                  <div key={o.id} className="card" style={{ padding: 20 }}>
                    <div
                      style={{
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "center",
                        flexWrap: "wrap",
                        gap: 12,
                      }}
                    >
                      <div>
                        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                          <strong style={{ fontSize: 16 }}>Order #{o.id.substring(0, 8)}...</strong>
                          <span
                            style={{
                              padding: "2px 8px",
                              borderRadius: 4,
                              fontSize: 12,
                              fontWeight: 600,
                              backgroundColor:
                                o.status === "PAID" || o.status === "DELIVERED"
                                  ? "rgba(115, 220, 140, 0.2)"
                                  : "rgba(230, 161, 92, 0.2)",
                              color:
                                o.status === "PAID" || o.status === "DELIVERED"
                                  ? "#73dc8c"
                                  : "#e6a15c",
                            }}
                          >
                            {o.status}
                          </span>
                        </div>
                        <div style={{ fontSize: 13, color: "var(--color-muted, #9fbfae)", marginTop: 4 }}>
                          Placed on {new Date(o.createdAt).toLocaleDateString()} • Total:{" "}
                          <strong style={{ color: "#f4fbf6" }}>
                            ₱{o.totalAmount.toLocaleString("en-PH", { minimumFractionDigits: 2 })} {o.currencyCode}
                          </strong>
                        </div>
                      </div>

                      <div style={{ display: "flex", gap: 8 }}>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => loadOrderDetails(o.id)}
                          disabled={orderLoading && isExpanded}
                        >
                          {isExpanded ? "Hide Details" : "Track & Details"}
                        </Button>
                      </div>
                    </div>

                    {/* Expanded details */}
                    {isExpanded && (
                      <div
                        style={{
                          marginTop: 16,
                          paddingTop: 16,
                          borderTop: "1px solid rgba(255, 255, 255, 0.08)",
                        }}
                      >
                        {orderLoading ? (
                          <div style={{ textAlign: "center", padding: 20 }}>
                            <span className="spinner" />
                          </div>
                        ) : orderDetails ? (
                          <div>
                            {/* Tracking & Shipments */}
                            <div style={{ marginBottom: 16 }}>
                              <h4 style={{ margin: "0 0 8px 0", fontSize: 14, color: "var(--color-accent, #b6f25c)" }}>
                                Tracking & Fulfillment Status
                              </h4>
                              {orderShipments.length === 0 ? (
                                <p style={{ fontSize: 13, color: "var(--color-muted, #9fbfae)", margin: 0 }}>
                                  Preparing for dispatch with seller(s). Tracking details will appear once courier waybills are issued.
                                </p>
                              ) : (
                                <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                                  {orderShipments.map((s) => (
                                    <div
                                      key={s.id}
                                      style={{
                                        padding: "10px 14px",
                                        borderRadius: 6,
                                        backgroundColor: "rgba(0, 0, 0, 0.2)",
                                        border: "1px solid rgba(115, 220, 140, 0.2)",
                                        fontSize: 13,
                                      }}
                                    >
                                      <div>
                                        <strong>{s.courierCode}</strong>: Waybill #
                                        <code style={{ color: "#73dc8c", marginLeft: 4 }}>{s.trackingNumber}</code>
                                      </div>
                                      <div style={{ color: "var(--color-muted, #9fbfae)", fontSize: 12, marginTop: 2 }}>
                                        Dispatched on {new Date(s.shippedAt).toLocaleString()} • Status: {s.status}
                                      </div>
                                    </div>
                                  ))}
                                </div>
                              )}
                            </div>

                            {/* Seller Items */}
                            <div style={{ marginBottom: 16 }}>
                              <h4 style={{ margin: "0 0 8px 0", fontSize: 14 }}>Items</h4>
                              {orderDetails.sellerOrders?.map((so: any) => (
                                <div key={so.id} style={{ marginBottom: 8, fontSize: 13 }}>
                                  <div style={{ fontWeight: 600, color: "var(--color-muted, #9fbfae)" }}>
                                    Seller: {so.sellerName}
                                  </div>
                                  {so.items?.map((it: any, i: number) => (
                                    <div key={i} style={{ display: "flex", justifyContent: "space-between", padding: "2px 0" }}>
                                      <span>
                                        {it.productName} ({it.variantName}) × {it.quantity}
                                      </span>
                                      <span>₱{it.lineTotal?.toLocaleString("en-PH", { minimumFractionDigits: 2 })}</span>
                                    </div>
                                  ))}
                                </div>
                              ))}
                            </div>

                            {/* Request Refund Form */}
                            <div
                              style={{
                                marginTop: 16,
                                paddingTop: 16,
                                borderTop: "1px solid rgba(255, 255, 255, 0.08)",
                              }}
                            >
                              <h4 style={{ margin: "0 0 8px 0", fontSize: 14, color: "var(--color-danger, #ef7368)" }}>
                                Request Return / Refund
                              </h4>
                              {refundMessage && (
                                <div
                                  style={{
                                    fontSize: 13,
                                    marginBottom: 10,
                                    color: refundMessage.includes("Error") ? "#ef7368" : "#73dc8c",
                                  }}
                                >
                                  {refundMessage}
                                </div>
                              )}
                              <form onSubmit={handleRequestRefund} style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
                                <input
                                  type="text"
                                  placeholder="Reason for return/refund request"
                                  value={refundReason}
                                  onChange={(e) => setRefundReason(e.target.value)}
                                  required
                                  style={{
                                    flex: 2,
                                    minWidth: 200,
                                    padding: "6px 10px",
                                    borderRadius: 6,
                                    border: "1px solid rgba(255, 255, 255, 0.15)",
                                    backgroundColor: "#0c1a14",
                                    color: "#f4fbf6",
                                    fontSize: 13,
                                  }}
                                />
                                <Button variant="danger" size="sm" type="submit" disabled={refundSubmitting}>
                                  {refundSubmitting ? "Submitting..." : "Submit Refund Request"}
                                </Button>
                              </form>
                            </div>
                          </div>
                        ) : null}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* TAB 2: Address Book */}
      {activeTab === "addresses" && (
        <div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
            <h3 style={{ margin: 0, fontSize: 18 }}>Delivery Addresses</h3>
            {!showAddAddress && (
              <Button variant="primary" size="sm" onClick={() => setShowAddAddress(true)}>
                + Add New Address
              </Button>
            )}
          </div>

          {/* Add Address Form */}
          {showAddAddress && (
            <div className="card" style={{ marginBottom: 20 }}>
              <h4 style={{ margin: "0 0 16px 0", fontSize: 16 }}>Add Delivery Address</h4>
              <form onSubmit={handleAddAddress} style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                <div>
                  <label style={{ display: "block", fontSize: 12, marginBottom: 4 }}>Recipient Name</label>
                  <input
                    type="text"
                    required
                    value={newAddr.recipientName}
                    onChange={(e) => setNewAddr({ ...newAddr, recipientName: e.target.value })}
                    style={{ width: "100%", padding: "8px", borderRadius: 6, backgroundColor: "#0c1a14", color: "#f4fbf6", border: "1px solid rgba(255,255,255,0.15)" }}
                  />
                </div>
                <div>
                  <label style={{ display: "block", fontSize: 12, marginBottom: 4 }}>Phone Number</label>
                  <input
                    type="text"
                    required
                    value={newAddr.phone}
                    onChange={(e) => setNewAddr({ ...newAddr, phone: e.target.value })}
                    style={{ width: "100%", padding: "8px", borderRadius: 6, backgroundColor: "#0c1a14", color: "#f4fbf6", border: "1px solid rgba(255,255,255,0.15)" }}
                  />
                </div>
                <div style={{ gridColumn: "1 / -1" }}>
                  <label style={{ display: "block", fontSize: 12, marginBottom: 4 }}>Street Line 1</label>
                  <input
                    type="text"
                    required
                    value={newAddr.line1}
                    onChange={(e) => setNewAddr({ ...newAddr, line1: e.target.value })}
                    style={{ width: "100%", padding: "8px", borderRadius: 6, backgroundColor: "#0c1a14", color: "#f4fbf6", border: "1px solid rgba(255,255,255,0.15)" }}
                  />
                </div>
                <div>
                  <label style={{ display: "block", fontSize: 12, marginBottom: 4 }}>City</label>
                  <input
                    type="text"
                    required
                    value={newAddr.city}
                    onChange={(e) => setNewAddr({ ...newAddr, city: e.target.value })}
                    style={{ width: "100%", padding: "8px", borderRadius: 6, backgroundColor: "#0c1a14", color: "#f4fbf6", border: "1px solid rgba(255,255,255,0.15)" }}
                  />
                </div>
                <div>
                  <label style={{ display: "block", fontSize: 12, marginBottom: 4 }}>Province / Region</label>
                  <input
                    type="text"
                    required
                    value={newAddr.state}
                    onChange={(e) => setNewAddr({ ...newAddr, state: e.target.value })}
                    style={{ width: "100%", padding: "8px", borderRadius: 6, backgroundColor: "#0c1a14", color: "#f4fbf6", border: "1px solid rgba(255,255,255,0.15)" }}
                  />
                </div>
                <div>
                  <label style={{ display: "block", fontSize: 12, marginBottom: 4 }}>Postal Code</label>
                  <input
                    type="text"
                    required
                    value={newAddr.postalCode}
                    onChange={(e) => setNewAddr({ ...newAddr, postalCode: e.target.value })}
                    style={{ width: "100%", padding: "8px", borderRadius: 6, backgroundColor: "#0c1a14", color: "#f4fbf6", border: "1px solid rgba(255,255,255,0.15)" }}
                  />
                </div>
                <div>
                  <label style={{ display: "block", fontSize: 12, marginBottom: 4 }}>Country</label>
                  <input
                    type="text"
                    readOnly
                    value="Philippines (PH)"
                    style={{ width: "100%", padding: "8px", borderRadius: 6, backgroundColor: "#0c1a14", color: "#9fbfae", border: "1px solid rgba(255,255,255,0.15)" }}
                  />
                </div>
                <div style={{ gridColumn: "1 / -1", display: "flex", gap: 10, marginTop: 8 }}>
                  <Button variant="primary" type="submit" disabled={addrSaving}>
                    {addrSaving ? "Saving..." : "Save Address"}
                  </Button>
                  <Button variant="outline" type="button" onClick={() => setShowAddAddress(false)}>
                    Cancel
                  </Button>
                </div>
              </form>
            </div>
          )}

          {/* List of addresses */}
          {addresses.length === 0 ? (
            <div className="card" style={{ textAlign: "center", padding: "30px 20px" }}>
              <p style={{ color: "var(--color-muted, #9fbfae)", margin: 0 }}>No delivery addresses saved yet.</p>
            </div>
          ) : (
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: 16 }}>
              {addresses.map((a) => (
                <div
                  key={a.id}
                  className="card"
                  style={{
                    position: "relative",
                    border: a.isDefault ? "1px solid var(--color-accent, #b6f25c)" : "1px solid rgba(255,255,255,0.08)",
                  }}
                >
                  {a.isDefault && (
                    <span
                      style={{
                        position: "absolute",
                        top: 12,
                        right: 12,
                        padding: "2px 8px",
                        borderRadius: 4,
                        fontSize: 11,
                        fontWeight: 700,
                        backgroundColor: "var(--color-accent, #b6f25c)",
                        color: "#102117",
                      }}
                    >
                      DEFAULT
                    </span>
                  )}
                  <h4 style={{ margin: "0 0 6px 0", fontSize: 15 }}>{a.recipientName}</h4>
                  <p style={{ margin: "0 0 4px 0", color: "var(--color-muted, #9fbfae)", fontSize: 13 }}>{a.phone}</p>
                  <p style={{ margin: "0 0 16px 0", color: "var(--color-muted, #9fbfae)", fontSize: 13 }}>
                    {a.line1}
                    {a.line2 && `, ${a.line2}`}, {a.city}, {a.state} {a.postalCode}, {a.country}
                  </p>
                  <div style={{ display: "flex", gap: 8 }}>
                    {!a.isDefault && (
                      <Button variant="outline" size="sm" onClick={() => handleSetDefaultAddress(a.id)}>
                        Set Default
                      </Button>
                    )}
                    <Button variant="danger" size="sm" onClick={() => handleDeleteAddress(a.id)}>
                      Delete
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* TAB 3: Profile & Security */}
      {activeTab === "profile" && (
        <div className="card">
          <h3 style={{ margin: "0 0 16px 0", fontSize: 18 }}>Profile & Security Settings</h3>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16, fontSize: 14 }}>
            <div>
              <span style={{ color: "var(--color-muted, #9fbfae)" }}>Email Address:</span>
              <div style={{ fontWeight: 600, marginTop: 2 }}>{user.email}</div>
            </div>
            <div>
              <span style={{ color: "var(--color-muted, #9fbfae)" }}>Account Status:</span>
              <div style={{ fontWeight: 600, marginTop: 2, color: "#73dc8c" }}>{user.status}</div>
            </div>
            <div>
              <span style={{ color: "var(--color-muted, #9fbfae)" }}>Assigned Roles:</span>
              <div style={{ fontWeight: 600, marginTop: 2 }}>{user.roles.join(", ")}</div>
            </div>
            <div>
              <span style={{ color: "var(--color-muted, #9fbfae)" }}>Email Verification:</span>
              <div style={{ fontWeight: 600, marginTop: 2, color: user.emailVerified ? "#73dc8c" : "#e6a15c" }}>
                {user.emailVerified ? "Verified" : "Pending"}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

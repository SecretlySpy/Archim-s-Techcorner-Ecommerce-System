"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api, ApiError, addressesApi, type Address, type CurrentUser } from "@/lib/api";
import { Button } from "@archim/ui-components";

/** Minimal authenticated page: fetches the current principal and shows roles/permissions. Redirects
 *  to /login on 401. Demonstrates the cookie session + /users/me authorization round-trip. */
export default function AccountPage() {
  const router = useRouter();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [loading, setLoading] = useState(true);
  const [showAdd, setShowAdd] = useState(false);
  const [addrForm, setAddrForm] = useState({ recipientName: "", line1: "", city: "", postalCode: "", country: "PH" });
  const [addrSaving, setAddrSaving] = useState(false);

  useEffect(() => {
    Promise.all([
      api.get<CurrentUser>("/api/v1/users/me"),
      addressesApi.list().catch(() => [])
    ]).then(([u, addrs]) => {
      setUser(u);
      setAddresses(addrs);
    }).catch((error) => {
      if (error instanceof ApiError && error.status === 401) {
        router.replace("/login");
      }
    }).finally(() => setLoading(false));
  }, [router]);

  async function logout() {
    await api.post("/api/v1/auth/logout", {});
    router.replace("/login");
  }

  async function addAddress() {
    if (!addrForm.recipientName || !addrForm.line1) return;
    setAddrSaving(true);
    try {
      const created = await addressesApi.create({
        ...addrForm,
        addressType: "SHIPPING",
        isDefault: addresses.length === 0
      });
      setAddresses([...addresses, created]);
      setAddrForm({ recipientName: "", line1: "", city: "", postalCode: "", country: "PH" });
      setShowAdd(false);
    } catch (e: any) {
      alert(e instanceof ApiError ? e.message : "Failed to add address");
    } finally {
      setAddrSaving(false);
    }
  }

  if (loading) {
    return <section className="card"><p className="subtitle">Loading…</p></section>;
  }
  if (!user) {
    return <section className="card"><p className="subtitle">Redirecting…</p></section>;
  }

  return (
    <section className="card">
      <h1>Your account</h1>
      <p className="subtitle">{user.email}</p>

      <label>Status</label>
      <div className="chip-row">
        <span className="chip">{user.status}</span>
        <span className="chip">{user.emailVerified ? "email verified" : "email not verified"}</span>
      </div>

      <label>Roles</label>
      <div className="chip-row">
        {user.roles.length ? user.roles.map((r) => <span key={r} className="chip">{r}</span>)
          : <span className="subtitle">none</span>}
      </div>

      <label>Addresses (T12)</label>
      <div className="card" style={{ margin: '8px 0', padding: 8 }}>
        {addresses.length === 0 ? (
          <div>No addresses yet.</div>
        ) : (
          addresses.map(a => (
            <div key={a.id} style={{ marginBottom: 4 }}>
              {a.recipientName} - {a.line1}, {a.city} {a.isDefault ? '(default)' : ''}
            </div>
          ))
        )}
        {!showAdd ? (
          <Button variant="outline" size="sm" onClick={() => setShowAdd(true)}>Add Address</Button>
        ) : (
          <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 6 }}>
            <input placeholder="Recipient Name" value={addrForm.recipientName} onChange={e=>setAddrForm({...addrForm, recipientName:e.target.value})} />
            <input placeholder="Address Line 1" value={addrForm.line1} onChange={e=>setAddrForm({...addrForm, line1:e.target.value})} />
            <input placeholder="City" value={addrForm.city} onChange={e=>setAddrForm({...addrForm, city:e.target.value})} />
            <input placeholder="Postal Code" value={addrForm.postalCode} onChange={e=>setAddrForm({...addrForm, postalCode:e.target.value})} />
            <div style={{ display: 'flex', gap: 8 }}>
              <Button onClick={addAddress} disabled={addrSaving}>Save</Button>
              <Button variant="outline" onClick={() => { setShowAdd(false); setAddrForm({ recipientName: "", line1: "", city: "", postalCode: "", country: "PH" }); }}>Cancel</Button>
            </div>
          </div>
        )}
      </div>

      <Button onClick={logout}>Sign out</Button>
    </section>
  );
}

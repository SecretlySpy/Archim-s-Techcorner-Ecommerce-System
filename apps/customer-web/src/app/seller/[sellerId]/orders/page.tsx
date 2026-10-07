"use client";

import { useParams } from "next/navigation";
import Link from "next/link";
import { Button } from "@archim/ui-components";

/** T19 stub: Seller order queue. Matches Figma design for seller orders. */
export default function SellerOrdersPage() {
  const params = useParams();
  const sellerId = params.sellerId as string;

  // Stub data for demo
  const orders = [
    { id: "SO-001", customer: "Juan D.", status: "PENDING", total: 1250, items: 2 },
    { id: "SO-002", customer: "Maria S.", status: "PACKING", total: 890, items: 1 },
  ];

  return (
    <div>
      <div className="card-header">
        <h1 className="page-title">Orders</h1>
        <Link href={`/seller/${sellerId}/inventory`}>
          <Button variant="outline" size="sm">Inventory</Button>
        </Link>
      </div>
      <p className="subtitle">Manage fulfillment • Packing • Shipping</p>

      <div className="card">
        {orders.length === 0 ? (
          <p>No orders yet.</p>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--border)' }}>
                <th style={{ textAlign: 'left', padding: '8px' }}>Order</th>
                <th style={{ textAlign: 'left', padding: '8px' }}>Customer</th>
                <th style={{ textAlign: 'left', padding: '8px' }}>Status</th>
                <th style={{ textAlign: 'right', padding: '8px' }}>Total</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {orders.map(o => (
                <tr key={o.id} style={{ borderBottom: '1px solid var(--border)' }}>
                  <td style={{ padding: '8px' }}>{o.id}</td>
                  <td style={{ padding: '8px' }}>{o.customer}</td>
                  <td style={{ padding: '8px' }}>
                    <span className="chip">{o.status}</span>
                  </td>
                  <td style={{ padding: '8px', textAlign: 'right' }}>₱{o.total}</td>
                  <td style={{ padding: '8px' }}>
                    <Button variant="outline" size="sm">View / Pack</Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      <p className="muted" style={{ marginTop: 12 }}>Stub for T19-T20. Full packing, tracking, transitions in later slices.</p>
    </div>
  );
}

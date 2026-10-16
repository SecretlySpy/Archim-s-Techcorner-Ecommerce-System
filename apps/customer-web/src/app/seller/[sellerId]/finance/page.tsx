"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { Button } from "@archim/ui-components";

interface SellerBalanceSummary {
  sellerId: string;
  sellerName: string;
  currentBalance: number;
  totalCredited: number;
  totalRefunded: number;
  currencyCode: string;
}

interface SellerLedgerEntry {
  id: string;
  transactionType: string;
  amount: number;
  currencyCode: string;
  orderId: string | null;
  createdAt: string;
}

function peso(amount: number): string {
  return "₱" + Number(amount || 0).toLocaleString("en-PH", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

export default function SellerFinancePage() {
  const params = useParams();
  const sellerId = params.sellerId as string;

  const [balance, setBalance] = useState<SellerBalanceSummary | null>(null);
  const [ledger, setLedger] = useState<SellerLedgerEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchFinanceData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const [balData, ledgerData] = await Promise.all([
        api.get<SellerBalanceSummary>(`/api/v1/sellers/${sellerId}/balance`),
        api.get<SellerLedgerEntry[]>(`/api/v1/sellers/${sellerId}/ledger`),
      ]);
      setBalance(balData);
      setLedger(ledgerData);
    } catch (e: any) {
      setError(e instanceof ApiError ? e.message : "Failed to load seller finance data.");
    } finally {
      setLoading(false);
    }
  }, [sellerId]);

  useEffect(() => {
    if (sellerId) {
      fetchFinanceData();
    }
  }, [sellerId, fetchFinanceData]);

  return (
    <div>
      {/* Header & Sub-navigation */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div>
          <h1 className="page-title" style={{ margin: 0 }}>Financial Ledger & Settlement</h1>
          <p style={{ margin: "4px 0 0", color: "var(--color-muted, #9fbfae)", fontSize: 14 }}>
            Track order earnings, marketplace commission deductions, and net payouts
          </p>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <Button variant="outline" size="sm" onClick={fetchFinanceData} disabled={loading}>
            Refresh
          </Button>
          <Link href={`/seller/${sellerId}/orders`}>
            <Button variant="outline" size="sm">Fulfillment Orders</Button>
          </Link>
          <Link href={`/seller/${sellerId}/inventory`}>
            <Button variant="outline" size="sm">Manage Stock</Button>
          </Link>
        </div>
      </div>

      {error && <div className="alert alert-error" style={{ marginBottom: 16 }}>{error}</div>}

      {/* Financial Metrics Cards */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
          gap: 16,
          marginBottom: 24,
        }}
      >
        <div
          className="card"
          style={{
            padding: 20,
            borderLeft: "4px solid var(--color-accent, #b6f25c)",
            background: "rgba(18, 32, 26, 0.7)",
          }}
        >
          <div style={{ fontSize: 13, color: "var(--color-muted, #9fbfae)", marginBottom: 6 }}>
            Net Available Balance
          </div>
          <div style={{ fontSize: 26, fontWeight: 700, color: "#f4fbf6" }}>
            {balance ? peso(balance.currentBalance) : "—"}
          </div>
          <div style={{ fontSize: 12, color: "#b6f25c", marginTop: 4 }}>
            Ready for bi-weekly payout
          </div>
        </div>

        <div
          className="card"
          style={{
            padding: 20,
            borderLeft: "4px solid #38bdf8",
            background: "rgba(18, 32, 26, 0.7)",
          }}
        >
          <div style={{ fontSize: 13, color: "var(--color-muted, #9fbfae)", marginBottom: 6 }}>
            Total Credited (Net of Commission)
          </div>
          <div style={{ fontSize: 26, fontWeight: 700, color: "#38bdf8" }}>
            {balance ? peso(balance.totalCredited) : "—"}
          </div>
          <div style={{ fontSize: 12, color: "var(--color-muted, #9fbfae)", marginTop: 4 }}>
            From captured marketplace orders
          </div>
        </div>

        <div
          className="card"
          style={{
            padding: 20,
            borderLeft: "4px solid #f87171",
            background: "rgba(18, 32, 26, 0.7)",
          }}
        >
          <div style={{ fontSize: 13, color: "var(--color-muted, #9fbfae)", marginBottom: 6 }}>
            Total Refund Deductions
          </div>
          <div style={{ fontSize: 26, fontWeight: 700, color: "#f87171" }}>
            {balance ? peso(balance.totalRefunded) : "—"}
          </div>
          <div style={{ fontSize: 12, color: "var(--color-muted, #9fbfae)", marginTop: 4 }}>
            Customer returns & claims
          </div>
        </div>

        <div
          className="card"
          style={{
            padding: 20,
            borderLeft: "4px solid #fbbf24",
            background: "rgba(18, 32, 26, 0.7)",
          }}
        >
          <div style={{ fontSize: 13, color: "var(--color-muted, #9fbfae)", marginBottom: 6 }}>
            Marketplace Take-Rate
          </div>
          <div style={{ fontSize: 26, fontWeight: 700, color: "#fbbf24" }}>
            10.0%
          </div>
          <div style={{ fontSize: 12, color: "var(--color-muted, #9fbfae)", marginTop: 4 }}>
            Platform fee deducted at capture
          </div>
        </div>
      </div>

      {/* Ledger Table */}
      <div className="card" style={{ padding: 20 }}>
        <h2 style={{ fontSize: 18, margin: "0 0 16px 0", color: "#f4fbf6" }}>
          Transaction History
        </h2>

        {loading ? (
          <div className="loading-center" style={{ padding: 40 }}>
            <div className="spinner" />
          </div>
        ) : ledger.length === 0 ? (
          <div style={{ padding: 40, textAlign: "center", color: "var(--color-muted, #9fbfae)" }}>
            No financial transactions recorded yet.
          </div>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 14 }}>
              <thead>
                <tr style={{ borderBottom: "1px solid rgba(255, 255, 255, 0.1)", textAlign: "left" }}>
                  <th style={{ padding: "10px 12px", color: "var(--color-muted, #9fbfae)" }}>Date</th>
                  <th style={{ padding: "10px 12px", color: "var(--color-muted, #9fbfae)" }}>Type</th>
                  <th style={{ padding: "10px 12px", color: "var(--color-muted, #9fbfae)" }}>Amount</th>
                  <th style={{ padding: "10px 12px", color: "var(--color-muted, #9fbfae)" }}>Order Ref</th>
                  <th style={{ padding: "10px 12px", color: "var(--color-muted, #9fbfae)" }}>Status</th>
                </tr>
              </thead>
              <tbody>
                {ledger.map((entry) => {
                  const isCredit = entry.amount >= 0;
                  return (
                    <tr
                      key={entry.id}
                      style={{
                        borderBottom: "1px solid rgba(255, 255, 255, 0.05)",
                      }}
                    >
                      <td style={{ padding: "12px", color: "var(--color-muted, #9fbfae)" }}>
                        {new Date(entry.createdAt).toLocaleString()}
                      </td>
                      <td style={{ padding: "12px" }}>
                        <span
                          style={{
                            display: "inline-block",
                            padding: "2px 8px",
                            borderRadius: 4,
                            fontSize: 12,
                            fontWeight: 600,
                            backgroundColor: isCredit
                              ? "rgba(182, 242, 92, 0.15)"
                              : "rgba(248, 113, 113, 0.15)",
                            color: isCredit ? "#b6f25c" : "#f87171",
                          }}
                        >
                          {entry.transactionType}
                        </span>
                      </td>
                      <td
                        style={{
                          padding: "12px",
                          fontWeight: 600,
                          color: isCredit ? "#b6f25c" : "#f87171",
                        }}
                      >
                        {isCredit ? "+" : ""}{peso(entry.amount)}
                      </td>
                      <td style={{ padding: "12px", fontFamily: "monospace", fontSize: 13 }}>
                        {entry.orderId ? (
                          <span title={entry.orderId}>
                            {entry.orderId.substring(0, 8)}...
                          </span>
                        ) : (
                          "—"
                        )}
                      </td>
                      <td style={{ padding: "12px" }}>
                        <span style={{ color: "#b6f25c", fontSize: 13 }}>● Settled</span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

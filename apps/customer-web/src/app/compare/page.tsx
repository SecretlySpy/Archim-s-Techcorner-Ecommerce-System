"use client";

import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { api, type ProductResponse } from "@/lib/api";

function peso(n: number): string {
  return "₱" + n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/** T09: Basic comparison page. Supports ?ids=ID1,ID2 (comma sep). Fetches details, shows side-by-side table.
 * States: loading, empty, error. Uses existing product data + specs (from T06).
 */
export default function ComparePage() {
  const params = useSearchParams();
  const idsParam = params.get("ids") || "";
  const ids = idsParam.split(",").filter(Boolean);

  const [products, setProducts] = useState<ProductResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (ids.length === 0) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    Promise.all(
      ids.map((id) =>
        api.get<ProductResponse>(`/api/v1/catalog/products/${id}`).catch(() => null)
      )
    )
      .then((res) => {
        const valid = res.filter((p): p is ProductResponse => !!p);
        setProducts(valid);
        if (valid.length === 0 && ids.length > 0) setError("No valid products found for comparison.");
      })
      .catch(() => setError("Failed to load comparison data."))
      .finally(() => setLoading(false));
  }, [idsParam]);

  if (loading) {
    return <div className="container"><div className="loading-center"><span className="spinner" /></div></div>;
  }

  if (error || products.length === 0) {
    return (
      <div className="container">
        <h1>Compare Products</h1>
        <p className="alert alert-error">{error || "Add products via product detail or ?ids=..."}</p>
        <Link href="/">Back to shop</Link>
      </div>
    );
  }

  // Collect all spec names for rows
  const allSpecNames = Array.from(new Set(products.flatMap(p => (p.specifications || []).map(s => s.name))));

  return (
    <div className="container" style={{ maxWidth: 1200 }}>
      <h1 className="page-title">Product Comparison</h1>
      <p className="links"><Link href="/">← Back to shop</Link></p>

      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr>
              <th style={{ textAlign: 'left', padding: 8, borderBottom: '1px solid var(--border)' }}>Feature</th>
              {products.map(p => (
                <th key={p.id} style={{ padding: 8, borderBottom: '1px solid var(--border)' }}>
                  <Link href={`/products/${p.id}`}>{p.name}</Link>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            <tr>
              <td style={{ padding: 8, borderBottom: '1px solid var(--border)', fontWeight: 500 }}>Price (from)</td>
              {products.map(p => {
                const min = p.variants.length ? Math.min(...p.variants.map(v => (v.salePrice ?? v.regularPrice) as number)) : null;
                return <td key={p.id} style={{ padding: 8, borderBottom: '1px solid var(--border)' }}>{min != null ? peso(min) : '—'}</td>;
              })}
            </tr>
            <tr>
              <td style={{ padding: 8, borderBottom: '1px solid var(--border)', fontWeight: 500 }}>Seller</td>
              {/* Note: sellerName not in ProductResponse yet; using from feed if extended, placeholder */}
              {products.map(p => <td key={p.id} style={{ padding: 8, borderBottom: '1px solid var(--border)' }}>— (see detail)</td>)}
            </tr>
            {allSpecNames.map(name => (
              <tr key={name}>
                <td style={{ padding: 8, borderBottom: '1px solid var(--border)', fontWeight: 500 }}>{name}</td>
                {products.map(p => {
                  const spec = (p.specifications || []).find(s => s.name === name);
                  return <td key={p.id} style={{ padding: 8, borderBottom: '1px solid var(--border)' }}>{spec ? spec.value : '—'}</td>;
                })}
              </tr>
            ))}
            <tr>
              <td style={{ padding: 8, fontWeight: 500 }}>Actions</td>
              {products.map(p => (
                <td key={p.id} style={{ padding: 8 }}>
                  <Link className="btn btn-sm" href={`/products/${p.id}`}>View</Link>
                </td>
              ))}
            </tr>
          </tbody>
        </table>
      </div>

      <p className="muted" style={{ marginTop: 16 }}>Compare up to 4 by visiting product pages and manually constructing ?ids= or extending with add-to-compare.</p>
    </div>
  );
}

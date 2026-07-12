"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";

/**
 * Seller Onboarding form. Allows an authenticated user to register as a seller
 * by providing business details, store info, and warehouse address. On success,
 * redirects to the seller dashboard where the new seller will appear as PENDING.
 */
export default function OnboardSellerPage() {
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setServerError(null);
    setSubmitting(true);

    const fd = new FormData(e.currentTarget);
    const body = {
      sellerName: fd.get("sellerName"),
      taxId: fd.get("taxId") || null,
      storeName: fd.get("storeName"),
      storeDescription: fd.get("storeDescription") || null,
      warehouseName: fd.get("warehouseName"),
      addressLine1: fd.get("addressLine1"),
      city: fd.get("city"),
      state: fd.get("state"),
      country: fd.get("country"),
      postalCode: fd.get("postalCode"),
    };

    try {
      await api.post("/api/v1/sellers/onboard", body);
      router.push("/seller");
    } catch (err) {
      setServerError(
        err instanceof ApiError ? err.message : "Something went wrong."
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <h1 className="page-title">Become a Seller</h1>
      <p className="page-subtitle">
        Fill in your business details to register as a seller on TechCorner.
        Your application will be reviewed by an administrator.
      </p>

      {serverError && <div className="alert alert-error">{serverError}</div>}

      <form onSubmit={handleSubmit} className="card">
        {/* ---- Business Details ---- */}
        <h3>Business Details</h3>
        <div className="form-row">
          <div className="form-group">
            <label htmlFor="sellerName">Business Name *</label>
            <input id="sellerName" name="sellerName" required placeholder="e.g. TechGadgets Inc." />
          </div>
          <div className="form-group">
            <label htmlFor="taxId">Tax ID</label>
            <input id="taxId" name="taxId" placeholder="Optional" />
          </div>
        </div>

        {/* ---- Store Details ---- */}
        <h3 style={{ marginTop: 24 }}>Store Details</h3>
        <div className="form-group">
          <label htmlFor="storeName">Store Name *</label>
          <input id="storeName" name="storeName" required placeholder="e.g. TechGadgets Official Store" />
        </div>
        <div className="form-group">
          <label htmlFor="storeDescription">Store Description</label>
          <textarea id="storeDescription" name="storeDescription" placeholder="Tell buyers about your store…" />
        </div>

        {/* ---- Warehouse / Address ---- */}
        <h3 style={{ marginTop: 24 }}>Warehouse Address</h3>
        <div className="form-group">
          <label htmlFor="warehouseName">Warehouse Name *</label>
          <input id="warehouseName" name="warehouseName" required placeholder="e.g. Main Warehouse" />
        </div>
        <div className="form-group">
          <label htmlFor="addressLine1">Address Line 1 *</label>
          <input id="addressLine1" name="addressLine1" required placeholder="123 Tech Street" />
        </div>
        <div className="form-row-3">
          <div className="form-group">
            <label htmlFor="city">City *</label>
            <input id="city" name="city" required />
          </div>
          <div className="form-group">
            <label htmlFor="state">State / Province *</label>
            <input id="state" name="state" required />
          </div>
          <div className="form-group">
            <label htmlFor="postalCode">Postal Code *</label>
            <input id="postalCode" name="postalCode" required />
          </div>
        </div>
        <div className="form-group">
          <label htmlFor="country">Country *</label>
          <input id="country" name="country" required placeholder="e.g. Philippines" />
        </div>

        <button type="submit" className="btn-full" disabled={submitting}>
          {submitting ? "Submitting…" : "Submit Application"}
        </button>
      </form>
    </>
  );
}

"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { api, ApiError, type CategoryResponse, type BrandResponse } from "@/lib/api";

/**
 * Create Product page. Allows sellers to add a new product with one initial variant.
 * Fetches categories and brands from the public API for dropdown selection.
 */
export default function CreateProductPage() {
  const params = useParams();
  const sellerId = params.sellerId as string;
  const router = useRouter();

  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [brands, setBrands] = useState<BrandResponse[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  // Load categories and brands for the dropdowns.
  useEffect(() => {
    Promise.all([
      api.get<CategoryResponse[]>("/api/v1/catalog/categories"),
      api.get<BrandResponse[]>("/api/v1/catalog/brands"),
    ]).then(([cats, brds]) => {
      setCategories(cats);
      setBrands(brds);
    });
  }, []);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setServerError(null);
    setSubmitting(true);

    const fd = new FormData(e.currentTarget);

    // Build the request body matching ProductController.CreateProductRequest.
    const body = {
      sellerPublicId: sellerId,
      categoryId: Number(fd.get("categoryId")),
      brandId: fd.get("brandId") ? Number(fd.get("brandId")) : null,
      name: fd.get("name"),
      slug: fd.get("slug"),
      description: fd.get("description") || null,
      variants: [
        {
          sku: fd.get("variantSku"),
          name: fd.get("variantName"),
          regularPrice: Number(fd.get("variantPrice")),
          stockQuantity: Number(fd.get("variantStock") || 0),
          attributes: fd.get("variantAttributes") || null,
        },
      ],
    };

    try {
      await api.post("/api/v1/catalog/products", body);
      router.push(`/seller/${sellerId}/products`);
    } catch (err) {
      setServerError(
        err instanceof ApiError ? err.message : "Failed to create product."
      );
    } finally {
      setSubmitting(false);
    }
  }

  /** Auto-generate a URL slug from the product name. */
  function generateSlug(name: string): string {
    return name
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-|-$/g, "");
  }

  return (
    <>
      <h1 className="page-title">Add Product</h1>
      <p className="page-subtitle">Create a new product listing. It will start as a DRAFT.</p>

      {serverError && <div className="alert alert-error">{serverError}</div>}

      <form onSubmit={handleSubmit} className="card">
        {/* ---- Product Info ---- */}
        <h3>Product Information</h3>
        <div className="form-group">
          <label htmlFor="name">Product Name *</label>
          <input
            id="name"
            name="name"
            required
            placeholder="e.g. Wireless Gaming Mouse Pro"
            onChange={(e) => {
              // Auto-fill slug when name changes
              const slugInput = document.getElementById("slug") as HTMLInputElement | null;
              if (slugInput) slugInput.value = generateSlug(e.target.value);
            }}
          />
        </div>
        <div className="form-group">
          <label htmlFor="slug">URL Slug *</label>
          <input id="slug" name="slug" required placeholder="wireless-gaming-mouse-pro" />
        </div>
        <div className="form-group">
          <label htmlFor="description">Description</label>
          <textarea id="description" name="description" placeholder="Describe your product…" />
        </div>

        <div className="form-row">
          <div className="form-group">
            <label htmlFor="categoryId">Category *</label>
            <select id="categoryId" name="categoryId" required>
              <option value="">Select category…</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          </div>
          <div className="form-group">
            <label htmlFor="brandId">Brand</label>
            <select id="brandId" name="brandId">
              <option value="">No brand</option>
              {brands.map((b) => (
                <option key={b.id} value={b.id}>{b.name}</option>
              ))}
            </select>
          </div>
        </div>

        {/* ---- Initial Variant ---- */}
        <h3 style={{ marginTop: 24 }}>Initial Variant</h3>
        <p className="cart-item-meta">Add at least one variant (SKU) for this product.</p>
        <div className="form-row">
          <div className="form-group">
            <label htmlFor="variantSku">SKU *</label>
            <input id="variantSku" name="variantSku" required placeholder="e.g. WGM-PRO-BLK" />
          </div>
          <div className="form-group">
            <label htmlFor="variantName">Variant Name *</label>
            <input id="variantName" name="variantName" required placeholder="e.g. Black" />
          </div>
        </div>
        <div className="form-row-3">
          <div className="form-group">
            <label htmlFor="variantPrice">Price (USD) *</label>
            <input id="variantPrice" name="variantPrice" type="number" step="0.01" min="0" required placeholder="49.99" />
          </div>
          <div className="form-group">
            <label htmlFor="variantStock">Initial Stock</label>
            <input id="variantStock" name="variantStock" type="number" min="0" defaultValue="0" />
          </div>
          <div className="form-group">
            <label htmlFor="variantAttributes">Attributes (JSON)</label>
            <input id="variantAttributes" name="variantAttributes" placeholder='{"color":"black"}' />
          </div>
        </div>

        <button type="submit" className="btn-full" disabled={submitting}>
          {submitting ? "Creating…" : "Create Product"}
        </button>
      </form>
    </>
  );
}

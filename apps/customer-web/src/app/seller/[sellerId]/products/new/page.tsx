"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { api, ApiError, type CategoryResponse, type BrandResponse } from "@/lib/api";

interface VariantFormState {
  sku: string;
  name: string;
  regularPrice: string;
  salePrice: string;
  stockQuantity: string;
  attributes: string;
}

interface SpecFormState {
  name: string;
  value: string;
}

interface ImageFormState {
  url: string;
  isPrimary: boolean;
}

/**
 * Create Product page. Allows sellers to add a new product with multiple variants,
 * specifications, and images in a single atomic aggregate submission.
 */
export default function CreateProductPage() {
  const params = useParams();
  const sellerId = params.sellerId as string;
  const router = useRouter();

  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [brands, setBrands] = useState<BrandResponse[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  // Dynamic form rows
  const [variants, setVariants] = useState<VariantFormState[]>([
    { sku: "", name: "", regularPrice: "", salePrice: "", stockQuantity: "10", attributes: "" },
  ]);
  const [specs, setSpecs] = useState<SpecFormState[]>([
    { name: "", value: "" },
  ]);
  const [images, setImages] = useState<ImageFormState[]>([
    { url: "", isPrimary: true },
  ]);

  // Load categories and brands for dropdowns.
  useEffect(() => {
    Promise.all([
      api.get<CategoryResponse[]>("/api/v1/catalog/categories"),
      api.get<BrandResponse[]>("/api/v1/catalog/brands"),
    ]).then(([cats, brds]) => {
      setCategories(cats);
      setBrands(brds);
    });
  }, []);

  function handleVariantChange(index: number, field: keyof VariantFormState, value: string) {
    setVariants((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], [field]: value };
      return next;
    });
  }

  function addVariantRow() {
    setVariants((prev) => [
      ...prev,
      { sku: "", name: "", regularPrice: "", salePrice: "", stockQuantity: "0", attributes: "" },
    ]);
  }

  function removeVariantRow(index: number) {
    if (variants.length <= 1) return;
    setVariants((prev) => prev.filter((_, i) => i !== index));
  }

  function handleSpecChange(index: number, field: keyof SpecFormState, value: string) {
    setSpecs((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], [field]: value };
      return next;
    });
  }

  function addSpecRow() {
    setSpecs((prev) => [...prev, { name: "", value: "" }]);
  }

  function removeSpecRow(index: number) {
    setSpecs((prev) => prev.filter((_, i) => i !== index));
  }

  function handleImageChange(index: number, field: keyof ImageFormState, value: string | boolean) {
    setImages((prev) => {
      const next = [...prev];
      if (field === "isPrimary" && value === true) {
        next.forEach((img, i) => {
          next[i] = { ...img, isPrimary: i === index };
        });
      } else {
        next[index] = { ...next[index], [field]: value };
      }
      return next;
    });
  }

  function addImageRow() {
    setImages((prev) => [...prev, { url: "", isPrimary: prev.length === 0 }]);
  }

  function removeImageRow(index: number) {
    setImages((prev) => prev.filter((_, i) => i !== index));
  }

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setServerError(null);
    setSubmitting(true);

    const fd = new FormData(e.currentTarget);

    // Validate variants
    const validVariants = variants.map((v) => ({
      sku: v.sku.trim(),
      name: v.name.trim(),
      regularPrice: Number(v.regularPrice),
      salePrice: v.salePrice ? Number(v.salePrice) : null,
      stockQuantity: Number(v.stockQuantity || 0),
      attributes: v.attributes.trim() || null,
    }));

    if (validVariants.some((v) => !v.sku || !v.name || isNaN(v.regularPrice) || v.regularPrice <= 0)) {
      setServerError("Please fill out all required variant fields (SKU, Name, positive Price).");
      setSubmitting(false);
      return;
    }

    const validSpecs = specs
      .filter((s) => s.name.trim() && s.value.trim())
      .map((s, idx) => ({
        name: s.name.trim(),
        value: s.value.trim(),
        displayOrder: idx,
      }));

    const validImages = images
      .filter((img) => img.url.trim())
      .map((img, idx) => ({
        url: img.url.trim(),
        isPrimary: img.isPrimary,
        displayOrder: idx,
      }));

    const body = {
      sellerPublicId: sellerId,
      categoryId: Number(fd.get("categoryId")),
      brandId: fd.get("brandId") ? Number(fd.get("brandId")) : null,
      name: (fd.get("name") as string).trim(),
      slug: (fd.get("slug") as string).trim(),
      description: (fd.get("description") as string)?.trim() || null,
      variants: validVariants,
      specifications: validSpecs,
      images: validImages,
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
      <p className="page-subtitle">Create a complete product listing with variants, specs, and media.</p>

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
          <textarea id="description" name="description" placeholder="Describe technical specifications and highlights…" rows={3} />
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

        {/* ---- Variants Section ---- */}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 28, marginBottom: 8 }}>
          <h3 style={{ margin: 0 }}>Product Variants</h3>
          <button type="button" onClick={addVariantRow} className="btn-secondary" style={{ padding: "6px 12px", fontSize: 13 }}>
            + Add Variant
          </button>
        </div>
        <p className="cart-item-meta" style={{ marginBottom: 12 }}>At least one variant with unique SKU is required.</p>

        {variants.map((v, i) => (
          <div key={i} style={{ padding: 14, border: "1px solid var(--border)", borderRadius: 8, marginBottom: 12, backgroundColor: "rgba(255,255,255,0.02)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
              <strong>Variant #{i + 1}</strong>
              {variants.length > 1 && (
                <button type="button" onClick={() => removeVariantRow(i)} className="btn-secondary" style={{ color: "#ef4444", fontSize: 12, padding: "2px 8px" }}>
                  Remove
                </button>
              )}
            </div>
            <div className="form-row">
              <div className="form-group">
                <label>SKU *</label>
                <input
                  value={v.sku}
                  onChange={(e) => handleVariantChange(i, "sku", e.target.value)}
                  required
                  placeholder="e.g. WGM-PRO-BLK"
                />
              </div>
              <div className="form-group">
                <label>Variant Name *</label>
                <input
                  value={v.name}
                  onChange={(e) => handleVariantChange(i, "name", e.target.value)}
                  required
                  placeholder="e.g. Midnight Black"
                />
              </div>
            </div>
            <div className="form-row-3">
              <div className="form-group">
                <label>Regular Price (PHP) *</label>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  value={v.regularPrice}
                  onChange={(e) => handleVariantChange(i, "regularPrice", e.target.value)}
                  required
                  placeholder="2499.00"
                />
              </div>
              <div className="form-group">
                <label>Initial Stock</label>
                <input
                  type="number"
                  min="0"
                  value={v.stockQuantity}
                  onChange={(e) => handleVariantChange(i, "stockQuantity", e.target.value)}
                />
              </div>
              <div className="form-group">
                <label>Attributes (JSON)</label>
                <input
                  value={v.attributes}
                  onChange={(e) => handleVariantChange(i, "attributes", e.target.value)}
                  placeholder='{"color":"Black","dpi":16000}'
                />
              </div>
            </div>
          </div>
        ))}

        {/* ---- Specifications Section ---- */}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 28, marginBottom: 8 }}>
          <h3 style={{ margin: 0 }}>Specifications (Optional)</h3>
          <button type="button" onClick={addSpecRow} className="btn-secondary" style={{ padding: "6px 12px", fontSize: 13 }}>
            + Add Spec
          </button>
        </div>
        <p className="cart-item-meta" style={{ marginBottom: 12 }}>Structured technical specs (e.g. Battery Life, Connectivity).</p>

        {specs.map((s, i) => (
          <div key={i} className="form-row" style={{ alignItems: "flex-end", marginBottom: 8 }}>
            <div className="form-group" style={{ flex: 1 }}>
              <label>Spec Name</label>
              <input
                value={s.name}
                onChange={(e) => handleSpecChange(i, "name", e.target.value)}
                placeholder="e.g. Connectivity"
              />
            </div>
            <div className="form-group" style={{ flex: 2 }}>
              <label>Spec Value</label>
              <input
                value={s.value}
                onChange={(e) => handleSpecChange(i, "value", e.target.value)}
                placeholder="e.g. 2.4GHz Wireless + Bluetooth 5.2"
              />
            </div>
            <button
              type="button"
              onClick={() => removeSpecRow(i)}
              className="btn-secondary"
              style={{ height: 42, marginBottom: 16, color: "#ef4444", padding: "0 10px" }}
            >
              ✕
            </button>
          </div>
        ))}

        {/* ---- Images Section ---- */}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 28, marginBottom: 8 }}>
          <h3 style={{ margin: 0 }}>Product Images (Optional)</h3>
          <button type="button" onClick={addImageRow} className="btn-secondary" style={{ padding: "6px 12px", fontSize: 13 }}>
            + Add Image URL
          </button>
        </div>
        <p className="cart-item-meta" style={{ marginBottom: 12 }}>Provide image URLs (HTTPS or root-relative paths).</p>

        {images.map((img, i) => (
          <div key={i} className="form-row" style={{ alignItems: "center", marginBottom: 8 }}>
            <div className="form-group" style={{ flex: 3 }}>
              <label>Image URL</label>
              <input
                value={img.url}
                onChange={(e) => handleImageChange(i, "url", e.target.value)}
                placeholder="https://images.example.com/products/mouse-1.jpg"
              />
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: 8, height: 42, marginTop: 12, flex: 1 }}>
              <label style={{ display: "flex", alignItems: "center", gap: 6, margin: 0, cursor: "pointer", fontSize: 13 }}>
                <input
                  type="radio"
                  name="primary_image"
                  checked={img.isPrimary}
                  onChange={() => handleImageChange(i, "isPrimary", true)}
                />
                Primary
              </label>
              <button
                type="button"
                onClick={() => removeImageRow(i)}
                className="btn-secondary"
                style={{ color: "#ef4444", padding: "4px 8px", fontSize: 12 }}
              >
                ✕
              </button>
            </div>
          </div>
        ))}

        <button type="submit" className="btn-full" style={{ marginTop: 24 }} disabled={submitting}>
          {submitting ? "Creating…" : "Create Product Listing"}
        </button>
      </form>
    </>
  );
}

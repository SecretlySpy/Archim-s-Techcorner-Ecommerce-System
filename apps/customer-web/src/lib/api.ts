// Thin API client for the customer web app. All calls go to the same-origin /api proxy (see
// next.config.mjs), so the HttpOnly auth cookies set by the backend are sent automatically.

/** Error carrying the backend's typed error code and safe message. */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string | undefined,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

/** Reads a cookie value by name (used for the double-submit CSRF token). */
function readCookie(name: string): string | undefined {
  if (typeof document === "undefined") return undefined;
  const match = document.cookie.match(new RegExp("(?:^|; )" + name + "=([^;]*)"));
  return match ? decodeURIComponent(match[1]) : undefined;
}

async function request<T>(path: string, init: RequestInit): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(init.headers as Record<string, string> | undefined),
  };
  // State-changing requests must echo the XSRF-TOKEN cookie as a header (Spring double-submit CSRF).
  if (method !== "GET" && method !== "HEAD") {
    const xsrf = readCookie("XSRF-TOKEN");
    if (xsrf) headers["X-XSRF-TOKEN"] = xsrf;
  }

  const response = await fetch(path, {
    ...init,
    credentials: "include", // send/receive the auth cookies
    headers,
  });

  const text = await response.text();
  const data = text ? JSON.parse(text) : null;

  if (!response.ok) {
    const code = data?.error?.code as string | undefined;
    const message = (data?.error?.message as string | undefined) ?? "Request failed";
    throw new ApiError(response.status, code, message);
  }
  return data as T;
}

export const api = {
  post: <T>(path: string, body: unknown) => request<T>(path, { method: "POST", body: JSON.stringify(body) }),
  get: <T>(path: string) => request<T>(path, { method: "GET" }),
  put: <T>(path: string, body: unknown) => request<T>(path, { method: "PUT", body: JSON.stringify(body) }),
  delete: <T>(path: string) => request<T>(path, { method: "DELETE" }),
};

/** Address helpers */
export const addressesApi = {
  list: () => api.get<Address[]>("/api/v1/addresses"),
  create: (body: any) => api.post<Address>("/api/v1/addresses", body),
};

/** T08 autocomplete helper */
export const autocomplete = (q: string) => api.get<FeedItem[]>(`/api/v1/catalog/products/autocomplete?q=${encodeURIComponent(q)}`);

// ========================================================================
// Shared TypeScript interfaces for API responses
// ========================================================================

/** GET /api/v1/users/me */
export interface CurrentUser {
  id: string;
  email: string;
  displayName: string | null;
  status: string;
  emailVerified: boolean;
  roles: string[];
  permissions: string[];
}

/** GET /api/v1/sellers/me */
export interface SellerSummary {
  sellerId: string;
  sellerName: string;
  status: string;
  stores: StoreSummary[];
  warehouses: WarehouseSummary[];
}

export interface StoreSummary {
  storeId: string;
  storeName: string;
  description: string | null;
}

export interface WarehouseSummary {
  warehouseId: string;
  warehouseName: string;
}

/** GET /api/v1/catalog/products?sellerPublicId= */
export interface ProductResponse {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  status: string;
  variants: VariantResponse[];
  images: ProductImageResponse[];
  specifications: ProductSpecResponse[];
}

export interface VariantResponse {
  id: string;
  sku: string;
  name: string;
  regularPrice: number;
  salePrice: number | null;
  stockQuantity: number;
  attributes: string | null;
}

export interface ProductImageResponse {
  url: string;
  primary: boolean;
  displayOrder: number;
}

export interface ProductSpecResponse {
  name: string;
  value: string;
  displayOrder: number;
}

/** GET /api/v1/catalog/products/feed — public product feed (guest browsing) */
export interface FeedItem {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  minPrice: number | null;
  sellerName: string;
}

export interface FeedPage {
  items: FeedItem[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

/** GET /api/v1/catalog/categories */
export interface CategoryResponse {
  id: number;
  name: string;
  slug: string;
}

/** GET /api/v1/catalog/brands */
export interface BrandResponse {
  id: number;
  name: string;
  slug: string;
}

/** GET /api/v1/cart */
export interface CartView {
  sellerGroups: SellerGroup[];
  cartTotal: number;
  totalItems: number;
}

export interface SellerGroup {
  sellerPublicId: string;
  sellerName: string;
  items: CartItemView[];
  subtotal: number;
}

export interface CartItemView {
  variantPublicId: string;
  productPublicId: string;
  productName: string;
  variantName: string;
  sku: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  availableStock: number;
}

/** GET /api/v1/wishlist */
export interface WishlistView {
  productPublicId: string;
  name: string;
  slug: string;
  status: string;
  addedAt: string;
}

/** GET /api/v1/inventory/low-stock */
export interface InventoryBucket {
  warehousePublicId: string;
  warehouseName: string;
  variantPublicId: string;
  variantSku: string;
  onHand: number;
  reserved: number;
  sellable: number;
  reorderThreshold: number;
}

/** Address types for T12 */
export interface Address {
  id: string;
  addressType: string;
  recipientName: string;
  phone: string | null;
  line1: string;
  line2: string | null;
  city: string;
  state: string | null;
  postalCode: string;
  country: string;
  isDefault: boolean;
}

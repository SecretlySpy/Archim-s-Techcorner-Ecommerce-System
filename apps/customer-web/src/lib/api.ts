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
};

/** Shape of GET /api/v1/users/me. */
export interface CurrentUser {
  id: string;
  email: string;
  displayName: string | null;
  status: string;
  emailVerified: boolean;
  roles: string[];
  permissions: string[];
}

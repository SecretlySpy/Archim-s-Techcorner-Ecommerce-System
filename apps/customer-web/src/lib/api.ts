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

async function request<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    credentials: "include", // send/receive the auth cookies
    headers: { "Content-Type": "application/json", ...(init.headers ?? {}) },
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

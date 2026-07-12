import { afterEach, describe, expect, it, vi } from "vitest";
import { api, ApiError } from "./api";

afterEach(() => {
  // Restore browser-like globals so each HTTP test starts from an isolated runtime state.
  vi.unstubAllGlobals();
});

describe("api", () => {
  it("performs same-origin GET requests with cookies and parses JSON", async () => {
    // Stub the platform boundary while retaining a real Response for parsing semantics.
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ id: "user-1" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await api.get<{ id: string }>("/api/v1/users/me");

    expect(result).toEqual({ id: "user-1" });
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/users/me", {
      method: "GET",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
    });
  });

  it("echoes the decoded CSRF cookie on state-changing requests", async () => {
    // A minimal document stub is sufficient because the client only reads document.cookie.
    vi.stubGlobal("document", { cookie: "theme=dark; XSRF-TOKEN=csrf%20token" });
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ message: "Logged out." }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await api.post("/api/v1/auth/logout", {});

    expect(fetchMock).toHaveBeenCalledWith("/api/v1/auth/logout", {
      method: "POST",
      body: "{}",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": "csrf token",
      },
    });
  });

  it("surfaces the backend status, code, and safe message through ApiError", async () => {
    // Preserve the backend's structured error contract for page-level recovery decisions.
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          error: {
            code: "INVALID_CREDENTIALS",
            message: "Invalid email or password.",
          },
        }),
        {
          status: 401,
          headers: { "Content-Type": "application/json" },
        },
      ),
    );
    vi.stubGlobal("fetch", fetchMock);

    const request = api.post("/api/v1/auth/login", {
      email: "customer@example.com",
      password: "wrong",
    });

    await expect(request).rejects.toBeInstanceOf(ApiError);
    await expect(request).rejects.toMatchObject({
      status: 401,
      code: "INVALID_CREDENTIALS",
      message: "Invalid email or password.",
    });
  });
});

/** @type {import('next').NextConfig} */
const backend = process.env.BACKEND_API_URL || "http://localhost:8080";

const nextConfig = {
  reactStrictMode: true,
  // Same-origin BFF proxy: the browser calls /api/* on this app; Next forwards to the Java backend.
  // This keeps auth cookies first-party (no CORS) — the pattern described in the architecture (§9).
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
  },
};

export default nextConfig;

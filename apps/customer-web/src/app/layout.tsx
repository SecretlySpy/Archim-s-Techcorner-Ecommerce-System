import "./globals.css";
import type { Metadata } from "next";
import type { ReactNode } from "react";
import { ToastProvider } from "@/components/Toast";

export const metadata: Metadata = {
  title: "Archim's TechCorner — Shop electronics, computers, gaming & more",
  description: "Browse thousands of tech products from verified sellers. No account needed to shop the catalog.",
};

/**
 * Full-width root shell. Each area supplies its own chrome:
 * the shop pages render a shop header, the Seller Center renders a dashboard sidebar,
 * and identity pages use the narrow (auth) layout.
 */
export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>
        <ToastProvider>
          {children}
        </ToastProvider>
      </body>
    </html>
  );
}

import "./globals.css";
import type { Metadata } from "next";
import type { ReactNode } from "react";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Archim's TechCorner",
  description: "Multi-vendor technology marketplace",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>
        <div className="container">
          <div className="brand">
            <Link href="/">
              Archim&apos;s <span>TechCorner</span>
            </Link>
          </div>
          {children}
        </div>
      </body>
    </html>
  );
}

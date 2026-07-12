import type { ReactNode } from "react";
import Link from "next/link";

/** Narrow, centered layout for identity pages (login, register, verify, account). */
export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="container">
      <div className="brand">
        <Link href="/">
          Archim&apos;s <span>TechCorner</span>
        </Link>
      </div>
      {children}
    </div>
  );
}

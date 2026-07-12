import Link from "next/link";

/** Landing page for the auth slice: entry points to sign in / create an account. */
export default function HomePage() {
  return (
    <section className="card">
      <h1>Welcome</h1>
      <p className="subtitle">Sign in to your account or create a new one to start shopping.</p>
      <Link href="/login">
        <button>Sign in</button>
      </Link>
      <p className="links">
        New here? <Link href="/register">Create an account</Link>
      </p>
    </section>
  );
}

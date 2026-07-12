import { z } from "zod";

// Client-side validation mirrors the backend Bean Validation rules (auth design §5). The server
// re-validates and is authoritative; these schemas exist for fast, accessible form feedback.

export const loginSchema = z.object({
  email: z.string().email("Enter a valid email"),
  password: z.string().min(1, "Password is required"),
});
export type LoginInput = z.infer<typeof loginSchema>;

export const registerSchema = z.object({
  email: z.string().email("Enter a valid email").max(254),
  password: z.string().min(12, "Use at least 12 characters").max(128),
  displayName: z.string().min(2, "Too short").max(60),
});
export type RegisterInput = z.infer<typeof registerSchema>;

// Shared design tokens (glass-over-hardware system)
// Extracted/reused from apps/customer-web/src/app/globals.css for reuse across apps
// Use via import or link styles/design-tokens.css

export const tokens = {
  colors: {
    bg: '#0c1a14',
    bgElevated: 'rgba(8, 22, 16, 0.72)',
    panel: 'rgba(244, 251, 246, 0.08)',
    panelHover: 'rgba(244, 251, 246, 0.14)',
    border: 'rgba(255, 255, 255, 0.28)',
    borderLight: 'rgba(255, 255, 255, 0.4)',
    text: '#f4fbf6',
    textSecondary: '#d5e8dc',
    muted: '#9fbfae',
    accent: '#b6f25c',
    accentHover: '#d4ff8a',
    accentDim: 'rgba(182, 242, 92, 0.16)',
    jade: '#1f8a5b',
    ink: '#102117',
    price: '#e6a15c',
    focus: '#8b7cff',
    danger: '#ef7368',
    dangerDim: 'rgba(239, 115, 104, 0.16)',
    ok: '#73dc8c',
    okDim: 'rgba(115, 220, 140, 0.16)',
    warning: '#e6a15c',
    warningDim: 'rgba(230, 161, 92, 0.16)',
  },
  radii: {
    sm: '8px',
    md: '12px',
    lg: '18px',
  },
  transitions: {
    base: '180ms ease',
  },
  shadows: {
    base: '0 10px 24px rgba(5, 20, 12, 0.32)',
  },
} as const;

export type DesignTokens = typeof tokens;

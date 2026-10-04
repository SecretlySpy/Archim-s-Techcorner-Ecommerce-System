import React from 'react';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'outline' | 'ghost' | 'danger';
  size?: 'sm' | 'md';
}

export const Button: React.FC<ButtonProps> = ({
  variant = 'primary',
  size = 'md',
  className = '',
  children,
  ...props
}) => {
  const base = 'btn';
  const v = {
    primary: 'btn',
    outline: 'btn btn-outline',
    ghost: 'btn btn-ghost',
    danger: 'btn btn-danger',
  }[variant];
  const s = size === 'sm' ? 'btn-sm' : '';
  return (
    <button className={`${base} ${v} ${s} ${className}`.trim()} {...props}>
      {children}
    </button>
  );
};

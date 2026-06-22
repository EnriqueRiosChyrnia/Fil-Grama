import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { Spinner } from './Spinner';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  fullWidth?: boolean;
  leftIcon?: ReactNode;
}

const SIZES: Record<ButtonSize, { height: number; padding: string; font: number; radius: string }> = {
  sm: { height: 34, padding: '0 13px', font: 13, radius: 'var(--fg-radius-sm)' },
  md: { height: 40, padding: '0 18px', font: 14, radius: 'var(--fg-radius)' },
  lg: { height: 48, padding: '0 22px', font: 15, radius: '11px' },
};

function variantStyle(variant: ButtonVariant, disabled: boolean): React.CSSProperties {
  if (variant === 'primary' || variant === 'danger') {
    const bg = variant === 'danger' ? 'var(--fg-danger-line)' : 'var(--fg-primary)';
    return {
      background: disabled ? 'var(--fg-gray-300)' : bg,
      color: 'var(--fg-on-primary)',
      border: '1px solid transparent',
      boxShadow: disabled ? 'none' : '0 4px 12px rgba(30,102,188,.18)',
    };
  }
  if (variant === 'secondary') {
    return {
      background: 'var(--fg-bg-surface)',
      color: 'var(--fg-text-primary)',
      border: '1px solid var(--fg-border-strong)',
    };
  }
  // ghost
  return { background: 'transparent', color: 'var(--fg-primary)', border: '1px solid transparent' };
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', loading = false, fullWidth, leftIcon, disabled, children, style, ...rest },
  ref,
) {
  const isDisabled = disabled || loading;
  const s = SIZES[size];
  return (
    <button
      ref={ref}
      disabled={isDisabled}
      aria-busy={loading || undefined}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 9,
        width: fullWidth ? '100%' : undefined,
        height: s.height,
        padding: s.padding,
        fontSize: s.font,
        fontWeight: 600,
        borderRadius: s.radius,
        cursor: isDisabled ? 'not-allowed' : 'pointer',
        whiteSpace: 'nowrap',
        transition: 'background .15s, border-color .15s, box-shadow .15s',
        ...variantStyle(variant, isDisabled),
        ...style,
      }}
      {...rest}
    >
      {loading && (
        <Spinner
          size={size === 'sm' ? 13 : 16}
          track={variant === 'secondary' || variant === 'ghost' ? 'var(--fg-border-strong)' : 'rgba(255,255,255,.45)'}
          color={variant === 'secondary' || variant === 'ghost' ? 'var(--fg-primary)' : '#fff'}
        />
      )}
      {!loading && leftIcon}
      {children}
    </button>
  );
});

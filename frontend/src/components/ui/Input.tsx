import { forwardRef, useId, useState, type InputHTMLAttributes, type ReactNode } from 'react';

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: ReactNode;
  /** Mensaje de error (rojo, con ícono). Si está presente, marca el campo en error. */
  error?: string | null;
  /** Slot a la derecha dentro del campo (ej. toggle de contraseña). */
  rightSlot?: ReactNode;
  containerStyle?: React.CSSProperties;
}

function ErrorIcon() {
  return (
    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" aria-hidden>
      <circle cx="12" cy="12" r="9" stroke="var(--fg-danger-line)" strokeWidth="1.8" />
      <path d="M12 7.5v5.5" stroke="var(--fg-danger-line)" strokeWidth="1.8" strokeLinecap="round" />
      <circle cx="12" cy="16.2" r="1" fill="var(--fg-danger-line)" />
    </svg>
  );
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, error, rightSlot, id, containerStyle, style, onFocus, onBlur, ...rest },
  ref,
) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const [focused, setFocused] = useState(false);
  const hasError = !!error;

  const border = hasError
    ? '1.5px solid var(--fg-danger-input-border)'
    : focused
      ? '1.5px solid var(--fg-primary)'
      : '1px solid #DCE2EA';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', ...containerStyle }}>
      {label && (
        <label
          htmlFor={inputId}
          style={{ fontSize: 12.5, fontWeight: 500, color: '#3D4757', marginBottom: 7 }}
        >
          {label}
        </label>
      )}
      <div style={{ position: 'relative' }}>
        <input
          ref={ref}
          id={inputId}
          aria-invalid={hasError || undefined}
          onFocus={(e) => {
            setFocused(true);
            onFocus?.(e);
          }}
          onBlur={(e) => {
            setFocused(false);
            onBlur?.(e);
          }}
          style={{
            width: '100%',
            height: 46,
            borderRadius: 'var(--fg-radius)',
            padding: rightSlot ? '0 76px 0 14px' : '0 14px',
            fontSize: 14,
            color: 'var(--fg-text-primary)',
            background: '#fff',
            border,
            outline: 'none',
            boxShadow: focused && !hasError ? 'var(--fg-ring)' : 'none',
            transition: 'border-color .12s, box-shadow .12s',
            ...style,
          }}
          {...rest}
        />
        {rightSlot && (
          <span style={{ position: 'absolute', right: 13, top: '50%', transform: 'translateY(-50%)' }}>
            {rightSlot}
          </span>
        )}
      </div>
      {hasError && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 7 }}>
          <ErrorIcon />
          <span style={{ fontSize: 12, color: 'var(--fg-danger-line)' }}>{error}</span>
        </div>
      )}
    </div>
  );
});

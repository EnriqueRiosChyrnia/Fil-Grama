import { forwardRef, useState } from 'react';
import { Input, type InputProps } from './Input';

/** Input de contraseña con toggle Mostrar/Ocultar (diseño Login). */
export const PasswordInput = forwardRef<HTMLInputElement, Omit<InputProps, 'type' | 'rightSlot'>>(
  function PasswordInput(props, ref) {
    const [show, setShow] = useState(false);
    return (
      <Input
        ref={ref}
        type={show ? 'text' : 'password'}
        rightSlot={
          <button
            type="button"
            onClick={() => setShow((v) => !v)}
            style={{
              border: 'none',
              background: 'transparent',
              fontSize: 12.5,
              fontWeight: 500,
              color: 'var(--fg-primary)',
              cursor: 'pointer',
              padding: 0,
            }}
          >
            {show ? 'Ocultar' : 'Mostrar'}
          </button>
        }
        {...props}
      />
    );
  },
);

import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../lib/auth';
import { ApiError } from '../../lib/api';
import { Button, Input, PasswordInput } from '../../components/ui';
import { Isotipo } from '../../components/brand/Logo';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Login de la agencia (admin/empleado). Diseño de alta fidelidad "Login". */
export function LoginPage() {
  const { status, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [touched, setTouched] = useState({ email: false, password: false });
  const [attempted, setAttempted] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [banner, setBanner] = useState<string | null>(null);

  if (status === 'authenticated') return <Navigate to={from} replace />;

  const errors = {
    email: email.trim() === '' ? 'Ingresá tu email.' : !EMAIL_RE.test(email.trim()) ? 'Ingresá un email válido (ej.: nombre@agencia.com).' : null,
    password: password === '' ? 'Ingresá tu contraseña.' : null,
  };
  const emailErr = touched.email || attempted ? errors.email : null;
  const pwErr = touched.password || attempted ? errors.password : null;

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setAttempted(true);
    setBanner(null);
    if (errors.email || errors.password) return;
    setSubmitting(true);
    try {
      await login(email.trim(), password);
      navigate(from, { replace: true });
    } catch (err) {
      // No revelar si el email existe: en 401 mostramos un mensaje genérico.
      if (err instanceof ApiError && err.status === 401) {
        setBanner('Email o contraseña incorrectos. Revisá los datos e intentá de nuevo.');
      } else if (err instanceof ApiError) {
        setBanner(err.humanMessage);
      } else {
        setBanner('No pudimos iniciar sesión. Probá de nuevo en un momento.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '60px 24px',
        background: 'radial-gradient(120% 90% at 50% -10%, #EAF0FA 0%, #F4F6FA 46%, #F4F6FA 100%)',
      }}
    >
      <form
        onSubmit={onSubmit}
        noValidate
        style={{
          width: 404,
          maxWidth: '100%',
          background: 'var(--fg-bg-surface)',
          border: '1px solid var(--fg-border)',
          borderRadius: 'var(--fg-radius-lg)',
          boxShadow: 'var(--fg-shadow-lg)',
          padding: '38px 40px 32px',
        }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 11, marginBottom: 26 }}>
          <Isotipo size={48} shadow />
          <div style={{ fontSize: 22, fontWeight: 700, color: 'var(--fg-text-primary)', letterSpacing: '-.3px' }}>
            Fil-Grama
          </div>
          <div style={{ fontSize: 13, color: 'var(--fg-text-secondary)' }}>Acceso para el equipo de la agencia</div>
        </div>

        {banner && (
          <div
            role="alert"
            style={{
              display: 'flex',
              gap: 10,
              alignItems: 'flex-start',
              background: 'var(--fg-danger-bg)',
              border: '1px solid var(--fg-danger-border)',
              borderRadius: 10,
              padding: '11px 13px',
              marginBottom: 18,
            }}
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" style={{ flex: 'none', marginTop: 1 }} aria-hidden>
              <circle cx="12" cy="12" r="9" stroke="var(--fg-danger-line)" strokeWidth="1.7" />
              <path d="M12 7.5v5.5" stroke="var(--fg-danger-line)" strokeWidth="1.7" strokeLinecap="round" />
              <circle cx="12" cy="16.2" r="1" fill="var(--fg-danger-line)" />
            </svg>
            <span style={{ fontSize: 13, color: 'var(--fg-danger-fg)', lineHeight: 1.45 }}>{banner}</span>
          </div>
        )}

        <div style={{ marginBottom: 16 }}>
          <Input
            label="Email"
            type="email"
            autoComplete="username"
            placeholder="nombre@agencia.com"
            value={email}
            onChange={(e) => {
              setEmail(e.target.value);
              setBanner(null);
            }}
            onBlur={() => setTouched((t) => ({ ...t, email: true }))}
            error={emailErr}
          />
        </div>

        <div style={{ marginBottom: 10 }}>
          <PasswordInput
            label="Contraseña"
            autoComplete="current-password"
            placeholder="Tu contraseña"
            value={password}
            onChange={(e) => {
              setPassword(e.target.value);
              setBanner(null);
            }}
            onBlur={() => setTouched((t) => ({ ...t, password: true }))}
            error={pwErr}
          />
        </div>

        <div style={{ textAlign: 'center', margin: '12px 0 22px' }}>
          <span style={{ fontSize: 12.5, color: 'var(--fg-text-tertiary)' }}>
            ¿Problemas para entrar? Contactá al administrador.
          </span>
        </div>

        <Button type="submit" size="lg" fullWidth loading={submitting}>
          {submitting ? 'Iniciando sesión…' : 'Iniciar sesión'}
        </Button>

        <div style={{ textAlign: 'center', fontSize: 11.5, color: 'var(--fg-text-tertiary)', lineHeight: 1.55, marginTop: 18 }}>
          Sólo para administradores y empleados.
          <br />
          Los clientes no tienen acceso.
        </div>
      </form>
    </div>
  );
}

import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useGetClientsClientIdAccounts } from '../../api/generated/endpoints';
import type { AccountResponse } from '../../api/generated/model';
import { Button, Card, Input, InfoTooltip, NetworkChip, networkLabel } from '../../components/ui';
import { ErrorState } from '../../components/layout';
import { Stepper, StatusPill } from './clientBits';
import { NETWORKS, normStatus } from './accountStatus';
import { useCreateClient, useConnectFlow } from './mutations';

const STEPS = ['Datos del cliente', 'Conectar redes', 'Listo'];

function BackLink() {
  return (
    <Link to="/" style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 13, color: 'var(--fg-text-tertiary)' }}>
      <span style={{ fontSize: 15 }}>‹</span>
      <span>Clientes</span>
    </Link>
  );
}

export function NewClientPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState(0);
  const [clientId, setClientId] = useState<number | null>(null);
  const [clientName, setClientName] = useState('');

  return (
    <div style={{ maxWidth: 640, margin: '0 auto' }}>
      <BackLink />
      <h1 style={{ fontSize: 22, fontWeight: 600, color: 'var(--fg-text-primary)', letterSpacing: '-.3px', margin: '14px 0 0' }}>
        Nuevo cliente
      </h1>
      <div style={{ marginTop: 18 }}>
        <Stepper steps={STEPS} current={step} />
      </div>

      <div style={{ marginTop: 22 }}>
        {step === 0 && (
          <DataStep
            onCreated={(c) => {
              setClientId(c.id);
              setClientName(c.name);
              setStep(1);
            }}
          />
        )}
        {step === 1 && clientId != null && (
          <ConnectStep clientId={clientId} clientName={clientName} onDone={() => setStep(2)} />
        )}
        {step === 2 && clientId != null && (
          <DoneStep
            clientId={clientId}
            clientName={clientName}
            onAddAnother={() => {
              setClientId(null);
              setClientName('');
              setStep(0);
            }}
            onGoClient={() => navigate(`/clients/${clientId}`)}
          />
        )}
      </div>
    </div>
  );
}

/* ── Paso 1: datos ─────────────────────────────────────────────────────── */
function DataStep({ onCreated }: { onCreated: (c: { id: number; name: string }) => void }) {
  const create = useCreateClient();
  const [name, setName] = useState('');
  const [plan, setPlan] = useState('');
  const [timezone, setTimezone] = useState('America/Asuncion');
  const [notes, setNotes] = useState('');
  const [touched, setTouched] = useState(false);

  const nameError = touched && !name.trim() ? 'Poné un nombre para el cliente.' : null;

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (!name.trim()) return;
    create.mutate(
      {
        name: name.trim(),
        plan: plan.trim() || undefined,
        timezone: timezone.trim() || undefined,
        notes: notes.trim() || undefined,
      },
      {
        onSuccess: (c) => {
          if (c.id != null) onCreated({ id: c.id, name: c.name ?? name.trim() });
        },
      },
    );
  };

  return (
    <Card padding={24}>
      <form onSubmit={onSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <Input
          label="Nombre del cliente"
          placeholder="Ej. Panadería La Espiga"
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={nameError}
          autoFocus
        />
        <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap' }}>
          <Input
            containerStyle={{ flex: 1, minWidth: 220 }}
            label="Plan (opcional)"
            placeholder="Ej. Mensual"
            value={plan}
            onChange={(e) => setPlan(e.target.value)}
          />
          <Input
            containerStyle={{ flex: 1, minWidth: 220 }}
            label="Zona horaria (opcional)"
            placeholder="America/Asuncion"
            value={timezone}
            onChange={(e) => setTimezone(e.target.value)}
          />
        </div>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          <label htmlFor="client-notes" style={{ fontSize: 12.5, fontWeight: 500, color: '#3D4757', marginBottom: 7 }}>
            Notas (opcional)
          </label>
          <textarea
            id="client-notes"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            rows={3}
            placeholder="Contexto interno del cliente."
            style={{
              width: '100%',
              borderRadius: 'var(--fg-radius)',
              padding: '11px 14px',
              fontSize: 14,
              fontFamily: 'inherit',
              color: 'var(--fg-text-primary)',
              background: '#fff',
              border: '1px solid #DCE2EA',
              outline: 'none',
              resize: 'vertical',
            }}
          />
        </div>

        {create.isError && (
          <ErrorState error={create.error} title="No pudimos crear el cliente" minHeight={120} />
        )}

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 4 }}>
          <Link to="/">
            <Button type="button" variant="secondary">
              Cancelar
            </Button>
          </Link>
          <Button type="submit" loading={create.isPending}>
            Crear y conectar redes
          </Button>
        </div>
      </form>
    </Card>
  );
}

/* ── Paso 2: conectar redes ────────────────────────────────────────────── */
function ConnectStep({
  clientId,
  clientName,
  onDone,
}: {
  clientId: number;
  clientName: string;
  onDone: () => void;
}) {
  const accountsQ = useGetClientsClientIdAccounts(clientId);
  const accounts: AccountResponse[] = useMemo(() => accountsQ.data?.data ?? [], [accountsQ.data]);
  const { connect, pending, error } = useConnectFlow(clientId);

  // Al volver de la pantalla de Meta/TikTok (otra pestaña), el operador vuelve a
  // esta → refrescamos las cuentas. También botón manual "Actualizar".
  useEffect(() => {
    const onFocus = () => accountsQ.refetch();
    window.addEventListener('focus', onFocus);
    return () => window.removeEventListener('focus', onFocus);
  }, [accountsQ]);

  const byPlatform = (p: string) => accounts.filter((a) => (a.platform ?? '').toUpperCase() === p);
  const unsupported = accounts.filter((a) => normStatus(a.status) === 'UNSUPPORTED');
  const connectedCount = accounts.filter((a) => normStatus(a.status) === 'CONNECTED').length;

  return (
    <Card padding={24}>
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
        <div>
          <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--fg-text-primary)' }}>
            Conectá las redes de {clientName}
          </div>
          <div style={{ fontSize: 13, color: 'var(--fg-text-secondary)', marginTop: 5, lineHeight: 1.55 }}>
            Se abre la pantalla oficial de la red en otra pestaña. Acompañá al cliente en ese paso; al
            volver, las cuentas aparecen acá. Podés conectar varias cuentas de la misma red.
          </div>
        </div>
        <Button variant="ghost" size="sm" onClick={() => accountsQ.refetch()} loading={accountsQ.isFetching}>
          Actualizar
        </Button>
      </div>

      {error && (
        <div style={{ marginTop: 14, fontSize: 13, color: 'var(--fg-danger-line)', background: 'var(--fg-danger-bg)', borderRadius: 'var(--fg-radius)', padding: '10px 13px' }}>
          {error}
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginTop: 18 }}>
        {NETWORKS.map((p) => {
          const accs = byPlatform(p).filter((a) => normStatus(a.status) !== 'UNSUPPORTED');
          return (
            <div
              key={p}
              style={{
                border: '1px solid var(--fg-border)',
                borderRadius: 'var(--fg-radius-md)',
                padding: '14px 16px',
                display: 'flex',
                flexDirection: 'column',
                gap: accs.length ? 12 : 0,
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <NetworkChip platform={p} long />
                <span style={{ fontSize: 13, color: 'var(--fg-text-tertiary)', flex: 1 }}>
                  {accs.length === 0
                    ? 'Sin cuentas conectadas'
                    : `${accs.length} ${accs.length === 1 ? 'cuenta conectada' : 'cuentas conectadas'}`}
                </span>
                <Button
                  variant="secondary"
                  size="sm"
                  loading={pending === p}
                  onClick={() => connect(p)}
                >
                  {accs.length === 0 ? `Conectar ${networkLabel(p, true)}` : 'Conectar otra'}
                </Button>
              </div>
              {accs.map((a) => (
                <div key={a.id} style={{ display: 'flex', alignItems: 'center', gap: 11 }}>
                  <span style={{ fontSize: 13, color: 'var(--fg-text-primary)' }}>
                    {a.handle || a.displayName || `Cuenta ${a.id}`}
                  </span>
                  <span style={{ marginLeft: 'auto' }}>
                    <StatusPill status={a.status} />
                  </span>
                </div>
              ))}
            </div>
          );
        })}
      </div>

      {unsupported.length > 0 && (
        <div
          style={{
            marginTop: 14,
            display: 'flex',
            alignItems: 'flex-start',
            gap: 9,
            background: 'var(--fg-bg-muted)',
            borderRadius: 'var(--fg-radius)',
            padding: '12px 14px',
          }}
        >
          <InfoTooltip title="Cuenta no compatible">
            Para conectar Instagram o Facebook hace falta una cuenta profesional (Business o Creator),
            no una personal.
          </InfoTooltip>
          <span style={{ fontSize: 12.5, color: 'var(--fg-text-secondary)', lineHeight: 1.5 }}>
            {unsupported.length === 1 ? 'Una cuenta quedó como no compatible' : `${unsupported.length} cuentas quedaron como no compatibles`}
            : son personales. Convertilas a profesional y volvé a conectarlas.
          </span>
        </div>
      )}

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10, marginTop: 22 }}>
        <span style={{ fontSize: 12.5, color: 'var(--fg-text-tertiary)' }}>
          {connectedCount > 0
            ? `${connectedCount} ${connectedCount === 1 ? 'cuenta conectada' : 'cuentas conectadas'}`
            : 'Podés continuar y conectar redes más tarde.'}
        </span>
        <Button onClick={onDone}>{connectedCount > 0 ? 'Continuar' : 'Continuar sin conectar'}</Button>
      </div>
    </Card>
  );
}

/* ── Paso 3: listo ─────────────────────────────────────────────────────── */
function DoneStep({
  clientId,
  clientName,
  onAddAnother,
  onGoClient,
}: {
  clientId: number;
  clientName: string;
  onAddAnother: () => void;
  onGoClient: () => void;
}) {
  const accountsQ = useGetClientsClientIdAccounts(clientId);
  const accounts: AccountResponse[] = accountsQ.data?.data ?? [];
  const connected = accounts.filter((a) => normStatus(a.status) === 'CONNECTED');

  return (
    <Card padding="34px 28px 26px" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center' }}>
      <div style={{ width: 60, height: 60, borderRadius: 16, background: 'var(--fg-success-bg)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" aria-hidden>
          <circle cx="12" cy="12" r="9" stroke="var(--fg-success-fg)" strokeWidth="1.7" />
          <path d="M8.5 12.2l2.4 2.3 4.6-4.8" stroke="var(--fg-success-fg)" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </div>
      <div style={{ fontSize: 19, fontWeight: 600, color: 'var(--fg-text-primary)', marginTop: 15 }}>
        {clientName} quedó dado de alta
      </div>
      <div style={{ fontSize: 14, color: 'var(--fg-text-secondary)', lineHeight: 1.6, maxWidth: 420, marginTop: 9 }}>
        {connected.length > 0
          ? 'Las primeras métricas aparecen tras la próxima captura diaria. No hay que hacer nada más.'
          : 'Todavía no conectaste redes. Podés hacerlo desde el cliente cuando quieras.'}
      </div>

      {connected.length > 0 && (
        <div style={{ display: 'flex', gap: 7, flexWrap: 'wrap', justifyContent: 'center', marginTop: 16 }}>
          {Array.from(new Set(connected.map((a) => a.platform))).map((p) => (
            <NetworkChip key={p} platform={p} long />
          ))}
        </div>
      )}

      <div style={{ display: 'flex', gap: 10, marginTop: 22 }}>
        <Button variant="secondary" onClick={onAddAnother}>
          Agregar otro cliente
        </Button>
        <Button onClick={onGoClient}>Ir al cliente</Button>
      </div>
    </Card>
  );
}

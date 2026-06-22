import { useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import {
  useGetClientsId,
  useGetClientsClientIdAccounts,
  useGetClientsClientIdSummary,
  postClientsClientIdReports,
} from '../../api/generated/endpoints';
import {
  GenerateReportRequestFormat,
  GenerateReportRequestReportType,
} from '../../api/generated/model';
import type { AccountResponse, GenerateReportRequest } from '../../api/generated/model';
import {
  Button,
  Card,
  SegmentedControl,
  NetworkChip,
  InfoTooltip,
  DateRangeControl,
  networkLabel,
} from '../../components/ui';
import { LoadingState, EmptyState, ErrorState } from '../../components/layout';
import { ApiError } from '../../lib/api';
import { computeRange, formatDate, type RangeDays } from '../../lib/format';
import { downloadReport } from './reportDownload';

/** `rankBy` lógico del backend (alias) → etiqueta humana. Default: reach. */
const RANK_OPTIONS = [
  { value: 'reach', label: 'Alcance' },
  { value: 'views', label: 'Reproducciones' },
  { value: 'engagement', label: 'Interacciones' },
  { value: 'likes', label: 'Me gusta' },
];

const TYPE_HELP: Record<string, string> = {
  SUMMARY: 'Una página con los números clave del período.',
  EXTENDED: 'Documento extendido con una grilla de publicaciones destacadas por tipo.',
};

function Breadcrumb({ to }: { to: string }) {
  return (
    <Link
      to={to}
      style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 13, color: 'var(--fg-text-tertiary)' }}
    >
      <span style={{ fontSize: 15 }}>‹</span>
      <span>Volver al cliente</span>
    </Link>
  );
}

function Field({ label, info, children }: { label: string; info?: string; children: ReactNode }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 13, fontWeight: 600, color: 'var(--fg-text-secondary)' }}>
        {label}
        {info && (
          <InfoTooltip title={label} size={14}>
            {info}
          </InfoTooltip>
        )}
      </span>
      {children}
    </div>
  );
}

export function ReportPage() {
  const { clientId } = useParams();
  const id = Number(clientId);

  const [reportType, setReportType] = useState<GenerateReportRequest['reportType']>(
    GenerateReportRequestReportType.SUMMARY,
  );
  const [format, setFormat] = useState<GenerateReportRequest['format']>(GenerateReportRequestFormat.PDF);
  const [range, setRange] = useState<RangeDays>(30);
  const [platforms, setPlatforms] = useState<string[] | null>(null);
  const [rankBy, setRankBy] = useState('reach');
  const [downloading, setDownloading] = useState(false);
  const [dlError, setDlError] = useState<string | null>(null);

  const dr = computeRange(range);

  const detailQ = useGetClientsId(id, { query: { enabled: Number.isFinite(id) } });
  const accountsQ = useGetClientsClientIdAccounts(id, { query: { enabled: Number.isFinite(id) } });
  const summaryQ = useGetClientsClientIdSummary(
    id,
    { from: dr.from, to: dr.to },
    { query: { enabled: Number.isFinite(id) } },
  );

  const mutation = useMutation({
    mutationFn: (body: GenerateReportRequest) => postClientsClientIdReports(id, body),
  });
  const report = mutation.data?.data;

  const accounts: AccountResponse[] = accountsQ.data?.data ?? [];
  const connectedPlatforms = Array.from(
    new Set(
      accounts
        .filter((a) => (a.status ?? '').toUpperCase() === 'CONNECTED')
        .map((a) => (a.platform ?? '').toUpperCase())
        .filter(Boolean),
    ),
  );
  const allPlatforms = Array.from(new Set(accounts.map((a) => (a.platform ?? '').toUpperCase()).filter(Boolean)));
  const platformOptions = connectedPlatforms.length ? connectedPlatforms : allPlatforms;

  // Por defecto, todas las redes disponibles (derivado en render, sin efecto).
  const selectedPlatforms = platforms ?? platformOptions;

  const summary = summaryQ.data?.data;
  const hasData = (summary?.platforms ?? []).some(
    (p) => (p.metrics?.length ?? 0) > 0 || p.engagementRate != null || p.followerGrowth != null,
  );

  // Cualquier cambio en la config invalida un reporte ya generado (era de otra config).
  const resetResult = () => {
    setDlError(null);
    if (mutation.isSuccess || mutation.isError) mutation.reset();
  };

  const togglePlatform = (p: string) => {
    resetResult();
    setPlatforms((prev) => {
      const cur = prev ?? platformOptions;
      return cur.includes(p) ? cur.filter((x) => x !== p) : [...cur, p];
    });
  };

  const onGenerate = () => {
    const body: GenerateReportRequest = {
      reportType,
      format,
      from: dr.from,
      to: dr.to,
      platforms: selectedPlatforms.length ? selectedPlatforms : undefined,
      rankBy,
    };
    mutation.mutate(body);
  };

  const onDownload = async () => {
    if (!report?.downloadUrl) return;
    setDownloading(true);
    setDlError(null);
    try {
      const ext = format === GenerateReportRequestFormat.PDF ? 'pdf' : 'md';
      const fallback = `reporte-${String(reportType).toLowerCase()}-${dr.from}_a_${dr.to}.${ext}`;
      await downloadReport(report.downloadUrl, fallback);
    } catch (e) {
      setDlError(e instanceof ApiError ? e.humanMessage : 'No pudimos descargar el archivo. Probá de nuevo.');
    } finally {
      setDownloading(false);
    }
  };

  if (accountsQ.isLoading || detailQ.isLoading) {
    return (
      <div>
        <Breadcrumb to={`/clients/${id}`} />
        <Card style={{ marginTop: 16 }}>
          <LoadingState message="Preparando el generador de reportes…" />
        </Card>
      </div>
    );
  }
  if (accountsQ.isError) {
    return (
      <div>
        <Breadcrumb to={`/clients/${id}`} />
        <ErrorState error={accountsQ.error} onRetry={() => accountsQ.refetch()} />
      </div>
    );
  }

  const clientName = detailQ.data?.data?.name;
  const noAccounts = accounts.length === 0;
  const generateDisabled = !hasData || summaryQ.isLoading || mutation.isPending;

  return (
    <div>
      <Breadcrumb to={`/clients/${id}`} />

      <div style={{ marginTop: 14 }}>
        <h1 style={{ fontSize: 22, fontWeight: 600, color: 'var(--fg-text-primary)', letterSpacing: '-.3px', margin: 0 }}>
          Generar reporte
        </h1>
        <div style={{ fontSize: 13.5, color: 'var(--fg-text-secondary)', marginTop: 6 }}>
          {clientName ? `${clientName} · ` : ''}Armá un PDF o Markdown limpio para compartir con el cliente.
        </div>
      </div>

      {noAccounts ? (
        <EmptyState
          title="Este cliente todavía no tiene cuentas"
          description="Cuando haya redes conectadas y datos capturados vas a poder generar reportes acá."
        />
      ) : (
        <>
          <Card style={{ marginTop: 18 }} padding={22}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
              <Field label="Tipo de reporte">
                <div>
                  <SegmentedControl<GenerateReportRequest['reportType']>
                    ariaLabel="Tipo de reporte"
                    value={reportType}
                    onChange={(v) => {
                      resetResult();
                      setReportType(v);
                    }}
                    options={[
                      { value: GenerateReportRequestReportType.SUMMARY, label: 'Resumen' },
                      { value: GenerateReportRequestReportType.EXTENDED, label: 'Extendido' },
                    ]}
                  />
                  <div style={{ fontSize: 12.5, color: 'var(--fg-text-tertiary)', marginTop: 8 }}>
                    {TYPE_HELP[String(reportType)]}
                  </div>
                </div>
              </Field>

              <Field label="Formato">
                <SegmentedControl<GenerateReportRequest['format']>
                  ariaLabel="Formato"
                  value={format}
                  onChange={(v) => {
                    resetResult();
                    setFormat(v);
                  }}
                  options={[
                    { value: GenerateReportRequestFormat.PDF, label: 'PDF' },
                    { value: GenerateReportRequestFormat.MARKDOWN, label: 'Markdown' },
                  ]}
                />
              </Field>

              <Field label="Período">
                <DateRangeControl
                  value={range}
                  onChange={(v) => {
                    resetResult();
                    setRange(v);
                  }}
                />
              </Field>

              <Field
                label="Redes"
                info="Sin selección se incluyen todas las redes conectadas del cliente."
              >
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 9 }}>
                  {platformOptions.map((p) => {
                    const checked = selectedPlatforms.includes(p);
                    return (
                      <button
                        key={p}
                        type="button"
                        role="checkbox"
                        aria-checked={checked}
                        onClick={() => togglePlatform(p)}
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 8,
                          padding: '8px 12px',
                          borderRadius: 'var(--fg-radius)',
                          border: `1px solid ${checked ? 'var(--fg-primary)' : 'var(--fg-border-strong)'}`,
                          background: checked ? 'var(--fg-blue-50)' : 'var(--fg-bg-surface)',
                          color: 'var(--fg-text-primary)',
                          fontSize: 13,
                          cursor: 'pointer',
                        }}
                      >
                        <NetworkChip platform={p} long />
                        <span style={{ fontSize: 12, color: 'var(--fg-text-tertiary)' }}>{checked ? '✓' : '+'}</span>
                      </button>
                    );
                  })}
                </div>
              </Field>

              <Field label="Ordenar destacadas por" info="Métrica con la que se rankean las publicaciones destacadas.">
                <SegmentedControl<string>
                  ariaLabel="Ordenar destacadas por"
                  value={rankBy}
                  onChange={(v) => {
                    resetResult();
                    setRankBy(v);
                  }}
                  options={RANK_OPTIONS}
                />
              </Field>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginTop: 24, flexWrap: 'wrap' }}>
              <Button
                onClick={onGenerate}
                loading={mutation.isPending}
                disabled={generateDisabled}
                title={!hasData ? 'Disponible cuando haya datos en el rango' : undefined}
              >
                Generar reporte
              </Button>
              {!hasData && !summaryQ.isLoading && (
                <span style={{ fontSize: 12.5, color: 'var(--fg-text-tertiary)' }}>
                  Aún no hay datos en este rango. Probá con un período más amplio.
                </span>
              )}
            </div>

            {mutation.isError && (
              <div
                style={{
                  marginTop: 16,
                  background: 'var(--fg-danger-bg)',
                  color: 'var(--fg-danger-fg)',
                  border: '1px solid var(--fg-danger-border)',
                  borderRadius: 'var(--fg-radius)',
                  padding: '11px 14px',
                  fontSize: 13,
                }}
              >
                {mutation.error instanceof ApiError
                  ? mutation.error.humanMessage
                  : 'No pudimos generar el reporte. Probá de nuevo.'}
              </div>
            )}
          </Card>

          {report && (
            <Card style={{ marginTop: 16 }} padding={22}>
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
                <div>
                  <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--fg-text-primary)' }}>Reporte listo</div>
                  <div style={{ fontSize: 13, color: 'var(--fg-text-secondary)', marginTop: 6, lineHeight: 1.6 }}>
                    {reportType === GenerateReportRequestReportType.SUMMARY ? 'Resumen' : 'Extendido'} ·{' '}
                    {format === GenerateReportRequestFormat.PDF ? 'PDF' : 'Markdown'}
                    <br />
                    Período: {formatDate(dr.from)} – {formatDate(dr.to)}
                    <br />
                    Redes:{' '}
                    {selectedPlatforms.length
                      ? selectedPlatforms.map((p) => networkLabel(p, true)).join(', ')
                      : 'todas las del cliente'}
                  </div>
                </div>
                <Button onClick={onDownload} loading={downloading} disabled={!report.downloadUrl}>
                  Descargar {format === GenerateReportRequestFormat.PDF ? 'PDF' : 'Markdown'}
                </Button>
              </div>
              {dlError && (
                <div style={{ marginTop: 12, fontSize: 13, color: 'var(--fg-danger-fg)' }}>{dlError}</div>
              )}
            </Card>
          )}
        </>
      )}
    </div>
  );
}

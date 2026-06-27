/**
 * Acciones (mutaciones) del track Clientes.
 *
 * NOTA IMPORTANTE: orval generó TODOS los endpoints (incluidos POST/PATCH) como
 * hooks `useQuery` (fire-on-mount), semántica equivocada para acciones disparadas
 * por el usuario (crear cliente, conectar/desconectar red). Por eso consumimos las
 * FUNCIONES async crudas exportadas (`postClients`, …) — parte de la API congelada,
 * pasan por el mutator con Bearer+refresh+ApiError — y las envolvemos en nuestro
 * propio `useMutation`. No tocamos `api/generated/*` ni nada compartido.
 */
import { useCallback, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  postClients,
  getPostClientsClientIdAccountsConnectPlatformUrl,
  postAccountsIdDisconnect,
  getGetClientsQueryKey,
  getGetClientsIdQueryKey,
  getGetClientsClientIdAccountsQueryKey,
} from '../../api/generated/endpoints';
import type { CreateClientRequest, ClientResponse, ConnectResponse } from '../../api/generated/model';
import { ApiError, orvalFetch, coreRequest, API_BASE_URL } from '../../lib/api';

function humanError(e: unknown): string {
  if (e instanceof ApiError) return e.humanMessage;
  if (e instanceof Error) return e.message;
  return 'Ocurrió un error inesperado. Probá de nuevo.';
}

/**
 * Endpoints del CICLO DE VIDA de cuenta + LINK COMPARTIBLE (track CV3, spec/09 §
 * "Ciclo de vida" y "Link compartible"; contratos en spec/03). El backend CV1/CV2
 * todavía no está mergeado, así que `reconnect`, `DELETE /accounts/{id}` y
 * `connect-links` NO existen aún en `api/generated`: los tipamos a mano contra los
 * contratos y los llamamos vía `coreRequest` (Bearer + refresh + ApiError, igual que
 * el mutator de orval). Cuando se mergee el backend → `npm run codegen` y migrar a los
 * hooks generados (`usePostAccountsIdReconnect`, etc.).
 */

/** `POST /accounts/{id}/reconnect` → reconexión inteligente. */
export interface ReconnectResult {
  /** Estado resultante: `CONNECTED` cuando el refresh reactivó sin OAuth. */
  status?: string;
  /** `true` si el token murió y hace falta re-autorización (OAuth nuevo o link). */
  requiresReauth?: boolean;
  authorizationUrl?: string;
}

/** `POST /clients/{clientId}/connect-links` → link compartible recién creado (token raw solo acá). */
export interface CreatedConnectLink {
  token: string;
  url: string;
  expiresAt?: string;
}

/** Alta de cliente (paso 1 del wizard). Devuelve el cliente creado (con id). */
export function useCreateClient() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateClientRequest): Promise<ClientResponse> => {
      const res = await postClients(body);
      return res.data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: getGetClientsQueryKey() });
    },
  });
}

/** Desconectar una cuenta. Refresca cuentas + detalle del cliente. */
export function useDisconnectAccount(clientId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (accountId: number) => {
      await postAccountsIdDisconnect(accountId);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: getGetClientsClientIdAccountsQueryKey(clientId) });
      qc.invalidateQueries({ queryKey: getGetClientsIdQueryKey(clientId) });
    },
  });
}

/**
 * Flujo de conexión OAuth de una red. El backend devuelve un `authorizationUrl`
 * (pantalla oficial Meta/TikTok). Lo abrimos en una PESTAÑA NUEVA para no perder
 * el estado del wizard; al volver, la pantalla refresca las cuentas (focus/manual).
 *
 * Abrimos la pestaña en blanco SINCRÓNICAMENTE dentro del click (antes del await)
 * para que el bloqueador de pop-ups no la corte; recién después le seteamos la URL.
 *
 * RECONEXIÓN: `connect(platform, accountId)` pasa el `accountId` esperado como query
 * param; el backend v2 valida que el open_id devuelto por la red coincida con esa
 * cuenta y rechaza (problem+json) si reconectaste la equivocada. El param es opcional
 * en el contrato nuevo y el cliente generado ya lo declara nativamente
 * (`PostClientsClientIdAccountsConnectPlatformParams`), así que se lo pasamos a la fn
 * generada en vez de adosarlo a mano a la URL.
 */
export function useConnectFlow(clientId: number) {
  const [pending, setPending] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const connect = useCallback(
    async (platform: string, accountId?: number) => {
      setError(null);
      setPending(platform);
      const tab = window.open('about:blank', '_blank');
      try {
        const path = getPostClientsClientIdAccountsConnectPlatformUrl(
          clientId,
          platform.toLowerCase(),
          accountId != null ? { accountId } : undefined,
        );
        const res = await orvalFetch<{ data: ConnectResponse }>(path, { method: 'POST' });
        const url = res.data?.authorizationUrl;
        if (!url) throw new Error('No recibimos el enlace de autorización. Probá de nuevo.');
        if (tab) tab.location.href = url;
        else window.location.href = url; // fallback si el navegador bloqueó la pestaña
      } catch (e) {
        tab?.close();
        setError(humanError(e));
      } finally {
        setPending(null);
      }
    },
    [clientId],
  );

  return { connect, pending, error };
}

/**
 * Reconexión inteligente (spec/09): un solo POST decide. Si el token sigue vivo el
 * backend reactiva (`status: CONNECTED`) sin OAuth ni molestar al cliente; si murió
 * responde `requiresReauth` y la UI ofrece re-autorizar la agencia o mandar un link.
 * Refresca cuentas + detalle del cliente al volver.
 */
export function useReconnectAccount(clientId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (accountId: number): Promise<ReconnectResult> => {
      return coreRequest<ReconnectResult>(`${API_BASE_URL}/accounts/${accountId}/reconnect`, { method: 'POST' });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: getGetClientsClientIdAccountsQueryKey(clientId) });
      qc.invalidateQueries({ queryKey: getGetClientsIdQueryKey(clientId) });
    },
  });
}

/**
 * Dar de baja una cuenta (`DELETE /accounts/{id}`, **solo ADMIN**). Operación
 * destructiva: revoca el token + borra la credencial, status `REMOVED`, conserva la
 * historia. La UI ya exige confirmación y oculta el botón a empleados; un empleado que
 * igual invoque el endpoint recibe `403`. Refresca cuentas + detalle.
 */
export function useDeleteAccount(clientId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (accountId: number): Promise<void> => {
      await coreRequest<void>(`${API_BASE_URL}/accounts/${accountId}`, { method: 'DELETE' });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: getGetClientsClientIdAccountsQueryKey(clientId) });
      qc.invalidateQueries({ queryKey: getGetClientsIdQueryKey(clientId) });
    },
  });
}

/**
 * Generar un link compartible de conexión para el cliente (spec/09 "Link
 * compartible"). `platform`/`accountId` son opcionales: fijan la red y/o marcan que es
 * una reconexión de una cuenta puntual. Devuelve el `token` raw + `url` pública (solo
 * acá, una vez) y `expiresAt` (default 72 h). No invalida queries: el link es efímero.
 */
export function useCreateConnectLink(clientId: number) {
  return useMutation({
    mutationFn: async (body: { platform?: string; accountId?: number }): Promise<CreatedConnectLink> => {
      return coreRequest<CreatedConnectLink>(`${API_BASE_URL}/clients/${clientId}/connect-links`, {
        method: 'POST',
        body: JSON.stringify(body ?? {}),
      });
    },
  });
}

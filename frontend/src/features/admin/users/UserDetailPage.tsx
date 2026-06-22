import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useGetUsersId, useGetUsersIdPriorityClients, useGetClients } from '../../../api/generated/endpoints';
import type { ClientResponse, UpdateUserRequestRole } from '../../../api/generated/model';
import { Button, Card, Input, SegmentedControl } from '../../../components/ui';
import { EmptyState, ErrorState, LoadingState } from '../../../components/layout';
import { ApiError } from '../../../lib/api';
import { roleLabel } from '../adminConstants';
import { useUpdateUser, useAddPriorityClient, useRemovePriorityClient } from '../hooks/useAdminMutations';

type ActiveValue = 'active' | 'inactive';

function BackLink({ onClick }: { onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        border: 'none',
        background: 'transparent',
        color: 'var(--fg-text-secondary)',
        fontSize: 13,
        cursor: 'pointer',
        padding: 0,
        marginBottom: 14,
      }}
    >
      ← Usuarios
    </button>
  );
}

function SectionTitle({ children }: { children: string }) {
  return <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--fg-text-primary)', marginBottom: 14 }}>{children}</div>;
}

/** Edición de datos del usuario (nombre, rol, estado). */
function EditUserCard({ id, fullName, role, isActive }: { id: number; fullName: string; role: UpdateUserRequestRole; isActive: boolean }) {
  // El card monta sólo cuando el usuario ya cargó; arranca con sus valores.
  // El padre lo remonta con `key={id}` al cambiar de usuario.
  const [name, setName] = useState(fullName);
  const [roleValue, setRoleValue] = useState<UpdateUserRequestRole>(role);
  const [active, setActive] = useState<ActiveValue>(isActive ? 'active' : 'inactive');

  const update = useUpdateUser(id);

  const nameTrim = name.trim();
  const dirty = nameTrim !== fullName || roleValue !== role || (active === 'active') !== isActive;
  const canSave = dirty && nameTrim.length > 0 && !update.isPending;
  const error = update.error instanceof ApiError ? update.error.humanMessage : null;

  const save = () => {
    if (!canSave) return;
    update.mutate({ fullName: nameTrim, role: roleValue, isActive: active === 'active' });
  };

  return (
    <Card style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <SectionTitle>Datos del usuario</SectionTitle>

      {error && (
        <div
          style={{
            fontSize: 13,
            color: 'var(--fg-danger-fg)',
            background: 'var(--fg-danger-bg)',
            border: '1px solid var(--fg-danger-border)',
            borderRadius: 'var(--fg-radius)',
            padding: '10px 12px',
          }}
        >
          {error}
        </div>
      )}

      <Input label="Nombre completo" value={name} onChange={(e) => setName(e.target.value)} error={nameTrim.length === 0 ? 'El nombre no puede quedar vacío.' : null} />

      <div>
        <div style={{ fontSize: 12.5, fontWeight: 500, color: '#3D4757', marginBottom: 7 }}>Rol</div>
        <SegmentedControl<UpdateUserRequestRole>
          ariaLabel="Rol del usuario"
          value={roleValue}
          onChange={setRoleValue}
          options={[
            { value: 'EMPLEADO', label: 'Empleado' },
            { value: 'ADMIN', label: 'Administrador' },
          ]}
        />
      </div>

      <div>
        <div style={{ fontSize: 12.5, fontWeight: 500, color: '#3D4757', marginBottom: 7 }}>Estado</div>
        <SegmentedControl<ActiveValue>
          ariaLabel="Estado del usuario"
          value={active}
          onChange={setActive}
          options={[
            { value: 'active', label: 'Activo' },
            { value: 'inactive', label: 'Inactivo' },
          ]}
        />
        <div style={{ fontSize: 12, color: 'var(--fg-text-tertiary)', marginTop: 8 }}>
          Un usuario inactivo no puede iniciar sesión.
        </div>
      </div>

      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
        <Button onClick={save} loading={update.isPending} disabled={!canSave}>
          Guardar cambios
        </Button>
      </div>
    </Card>
  );
}

/** Clientes prioritarios del usuario: flag informativo, no limita acceso (HANDOFF §3). */
function PriorityClientsCard({ userId }: { userId: number }) {
  const [search, setSearch] = useState('');
  const priorityQ = useGetUsersIdPriorityClients(userId);
  const clientsQ = useGetClients({ size: 100, status: 'ACTIVE' });
  const add = useAddPriorityClient(userId);
  const remove = useRemovePriorityClient(userId);

  const priority = useMemo(() => priorityQ.data?.data ?? [], [priorityQ.data]);
  const priorityIds = useMemo(() => new Set(priority.map((c) => c.id)), [priority]);
  const allClients = useMemo(() => clientsQ.data?.data?.content ?? [], [clientsQ.data]);

  const candidates = useMemo(() => {
    const term = search.trim().toLowerCase();
    return allClients
      .filter((c) => !priorityIds.has(c.id))
      .filter((c) => (term ? (c.name ?? '').toLowerCase().includes(term) : true))
      .slice(0, 6);
  }, [allClients, priorityIds, search]);

  const rowStyle: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 10,
    padding: '10px 0',
    borderBottom: '1px solid var(--fg-border)',
  };

  return (
    <Card style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <SectionTitle>Clientes prioritarios</SectionTitle>
      <div style={{ fontSize: 12.5, color: 'var(--fg-text-secondary)', marginTop: -8 }}>
        Marca informativa para destacar clientes en la vista del empleado. No restringe el acceso.
      </div>

      {priorityQ.isLoading ? (
        <LoadingState message="Cargando prioritarios…" minHeight={80} />
      ) : priorityQ.isError ? (
        <ErrorState error={priorityQ.error} onRetry={() => priorityQ.refetch()} minHeight={100} />
      ) : priority.length === 0 ? (
        <div style={{ fontSize: 13, color: 'var(--fg-text-tertiary)', padding: '6px 0' }}>
          Este usuario todavía no tiene clientes prioritarios.
        </div>
      ) : (
        <div>
          {priority.map((c: ClientResponse) => (
            <div key={c.id} style={rowStyle}>
              <span style={{ fontSize: 14, color: 'var(--fg-text-primary)', fontWeight: 500 }}>{c.name}</span>
              <Button
                variant="ghost"
                size="sm"
                loading={remove.isPending && remove.variables === c.id}
                disabled={remove.isPending}
                onClick={() => c.id != null && remove.mutate(c.id)}
              >
                Quitar
              </Button>
            </div>
          ))}
        </div>
      )}

      <div style={{ marginTop: 4 }}>
        <Input placeholder="Buscar cliente para agregar" value={search} onChange={(e) => setSearch(e.target.value)} style={{ height: 40 }} />
        <div style={{ marginTop: 8 }}>
          {clientsQ.isLoading ? (
            <LoadingState message="Cargando clientes…" minHeight={60} />
          ) : candidates.length === 0 ? (
            <div style={{ fontSize: 12.5, color: 'var(--fg-text-tertiary)', padding: '8px 0' }}>
              {search.trim() ? 'No hay clientes que coincidan.' : 'No hay más clientes para agregar.'}
            </div>
          ) : (
            candidates.map((c) => (
              <div key={c.id} style={rowStyle}>
                <span style={{ fontSize: 14, color: 'var(--fg-text-primary)' }}>{c.name}</span>
                <Button
                  variant="secondary"
                  size="sm"
                  loading={add.isPending && add.variables === c.id}
                  disabled={add.isPending}
                  onClick={() => c.id != null && add.mutate(c.id)}
                >
                  Agregar
                </Button>
              </div>
            ))
          )}
        </div>
      </div>

      {(add.error || remove.error) && (
        <div style={{ fontSize: 12.5, color: 'var(--fg-danger-fg)' }}>
          {(add.error instanceof ApiError && add.error.humanMessage) ||
            (remove.error instanceof ApiError && remove.error.humanMessage) ||
            'No pudimos actualizar los prioritarios.'}
        </div>
      )}
    </Card>
  );
}

export function UserDetailPage() {
  const navigate = useNavigate();
  const params = useParams();
  const id = Number(params.id);
  const validId = Number.isFinite(id) && id > 0;

  const userQ = useGetUsersId(id, { query: { enabled: validId } });
  const user = userQ.data?.data;

  if (!validId) {
    return (
      <div>
        <BackLink onClick={() => navigate('/admin/users')} />
        <EmptyState title="Usuario no válido" description="El identificador del usuario no es correcto." />
      </div>
    );
  }

  return (
    <div>
      <BackLink onClick={() => navigate('/admin/users')} />

      {userQ.isLoading ? (
        <LoadingState message="Cargando usuario…" minHeight="40vh" />
      ) : userQ.isError ? (
        <ErrorState error={userQ.error} onRetry={() => userQ.refetch()} />
      ) : !user ? (
        <EmptyState title="No encontramos el usuario" description="Puede que se haya eliminado o que el enlace sea viejo." />
      ) : (
        <>
          <div style={{ marginBottom: 18 }}>
            <h1 style={{ fontSize: 23, fontWeight: 600, color: 'var(--fg-text-primary)', letterSpacing: '-.3px', margin: 0 }}>
              {user.fullName}
            </h1>
            <div style={{ fontSize: 13, color: 'var(--fg-text-secondary)', marginTop: 4 }}>
              {user.email} · {roleLabel(user.role)}
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 18, alignItems: 'start' }}>
            <EditUserCard
              key={id}
              id={id}
              fullName={user.fullName ?? ''}
              role={(user.role as UpdateUserRequestRole) ?? 'EMPLEADO'}
              isActive={user.isActive ?? false}
            />
            <PriorityClientsCard userId={id} />
          </div>
        </>
      )}
    </div>
  );
}

import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useGetUsers } from '../../../api/generated/endpoints';
import type { GetUsersParams, UserResponse } from '../../../api/generated/model';
import { Button, Input, SegmentedControl, Table, type Column } from '../../../components/ui';
import { EmptyState, ErrorState, LoadingState, Skeleton } from '../../../components/layout';
import { formatDate } from '../../../lib/format';
import {
  ADMIN_PAGE_SIZE,
  roleLabel,
  roleFilterToParam,
  activeFilterToParam,
  type RoleFilter,
  type ActiveFilter,
} from '../adminConstants';
import { Badge } from '../components/Badge';
import { Pagination } from '../components/Pagination';
import { UserFormModal } from './UserFormModal';

function ActiveBadge({ active }: { active?: boolean }) {
  return active
    ? <Badge bg="var(--fg-success-bg)" fg="var(--fg-success-fg)">Activo</Badge>
    : <Badge bg="var(--fg-gray-100)" fg="var(--fg-gray-600)">Inactivo</Badge>;
}

export function UsersListPage() {
  const navigate = useNavigate();
  const [roleFilter, setRoleFilter] = useState<RoleFilter>('ALL');
  const [activeFilter, setActiveFilter] = useState<ActiveFilter>('ALL');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);

  const params: GetUsersParams = useMemo(
    () => ({
      role: roleFilterToParam(roleFilter),
      active: activeFilterToParam(activeFilter),
      q: q.trim() || undefined,
      page,
      size: ADMIN_PAGE_SIZE,
    }),
    [roleFilter, activeFilter, q, page],
  );

  const usersQ = useGetUsers(params);
  const pageData = usersQ.data?.data;
  const rows = pageData?.content ?? [];

  // Cualquier cambio de filtro vuelve a la primera página.
  const resetTo = <T,>(setter: (v: T) => void) => (v: T) => {
    setPage(0);
    setter(v);
  };

  const columns: Column<UserResponse>[] = [
    {
      key: 'name',
      header: 'Usuario',
      width: '2fr',
      render: (u) => (
        <div style={{ minWidth: 0 }}>
          <div style={{ fontWeight: 600, color: 'var(--fg-text-primary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {u.fullName || '—'}
          </div>
          <div style={{ fontSize: 12, color: 'var(--fg-text-tertiary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {u.email}
          </div>
        </div>
      ),
    },
    { key: 'role', header: 'Rol', width: '1fr', render: (u) => roleLabel(u.role) },
    { key: 'active', header: 'Estado', width: '110px', render: (u) => <ActiveBadge active={u.isActive} /> },
    {
      key: 'createdAt',
      header: 'Alta',
      width: '90px',
      align: 'right',
      render: (u) => <span style={{ color: 'var(--fg-text-tertiary)' }}>{formatDate(u.createdAt)}</span>,
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
        <div>
          <h1 style={{ fontSize: 24, fontWeight: 600, color: 'var(--fg-text-primary)', letterSpacing: '-.3px', margin: 0 }}>
            Usuarios
          </h1>
          <div style={{ fontSize: 13, color: 'var(--fg-text-secondary)', marginTop: 5 }}>
            Equipo de la agencia con acceso a Fil-Grama.
          </div>
        </div>
        <Button onClick={() => setCreateOpen(true)}>Crear usuario</Button>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap', marginTop: 20 }}>
        <div style={{ flex: 1, minWidth: 220 }}>
          <Input
            placeholder="Buscar por nombre o email"
            value={q}
            onChange={(e) => {
              setPage(0);
              setQ(e.target.value);
            }}
            style={{ height: 40 }}
          />
        </div>
        <SegmentedControl<RoleFilter>
          ariaLabel="Filtrar por rol"
          value={roleFilter}
          onChange={resetTo(setRoleFilter)}
          options={[
            { value: 'ALL', label: 'Todos' },
            { value: 'ADMIN', label: 'Admins' },
            { value: 'EMPLEADO', label: 'Empleados' },
          ]}
        />
        <SegmentedControl<ActiveFilter>
          ariaLabel="Filtrar por estado"
          value={activeFilter}
          onChange={resetTo(setActiveFilter)}
          options={[
            { value: 'ALL', label: 'Todos' },
            { value: 'ACTIVE', label: 'Activos' },
            { value: 'INACTIVE', label: 'Inactivos' },
          ]}
        />
      </div>

      <div style={{ marginTop: 18 }}>
        {usersQ.isLoading ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10, padding: '8px 0' }}>
            <Skeleton height={40} />
            <Skeleton height={40} />
            <Skeleton height={40} />
            <LoadingState message="Cargando usuarios…" minHeight={60} />
          </div>
        ) : usersQ.isError ? (
          <ErrorState error={usersQ.error} onRetry={() => usersQ.refetch()} />
        ) : rows.length === 0 ? (
          <EmptyState
            title={q || roleFilter !== 'ALL' || activeFilter !== 'ALL' ? 'No hay usuarios con ese filtro' : 'Todavía no hay usuarios'}
            description={
              q || roleFilter !== 'ALL' || activeFilter !== 'ALL'
                ? 'Probá con otro nombre, rol o estado.'
                : 'Creá el primer usuario para darle acceso al equipo.'
            }
            action={
              q || roleFilter !== 'ALL' || activeFilter !== 'ALL' ? undefined : (
                <Button onClick={() => setCreateOpen(true)}>Crear usuario</Button>
              )
            }
          />
        ) : (
          <>
            <Table<UserResponse>
              columns={columns}
              rows={rows}
              rowKey={(u) => u.id ?? u.email ?? ''}
              onRowClick={(u) => u.id != null && navigate(`/admin/users/${u.id}`)}
            />
            <Pagination
              page={pageData?.page ?? page}
              totalPages={pageData?.totalPages ?? 1}
              totalElements={pageData?.totalElements ?? rows.length}
              onPage={setPage}
            />
          </>
        )}
      </div>

      <UserFormModal open={createOpen} onClose={() => setCreateOpen(false)} />
    </div>
  );
}

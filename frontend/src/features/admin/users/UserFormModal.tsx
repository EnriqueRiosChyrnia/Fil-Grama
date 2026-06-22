import { useState } from 'react';
import { Button, Input, PasswordInput, SegmentedControl } from '../../../components/ui';
import { ApiError } from '../../../lib/api';
import type { CreateUserRequestRole } from '../../../api/generated/model';
import { Modal } from '../components/Modal';
import { useCreateUser } from '../hooks/useAdminMutations';

/** Alta de usuario. Maneja 409 (email duplicado) mostrando el mensaje humano. */
export function UserFormModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [email, setEmail] = useState('');
  const [fullName, setFullName] = useState('');
  const [role, setRole] = useState<CreateUserRequestRole>('EMPLEADO');
  const [password, setPassword] = useState('');
  const [touched, setTouched] = useState(false);

  const createUser = useCreateUser();

  const emailTrim = email.trim();
  const nameTrim = fullName.trim();
  const validEmail = /.+@.+\..+/.test(emailTrim);
  const validName = nameTrim.length > 0;
  const validPassword = password.length >= 8;
  const canSubmit = validEmail && validName && validPassword && !createUser.isPending;

  const err = createUser.error;
  const isDuplicate = err instanceof ApiError && err.status === 409;
  const generalError = err instanceof ApiError && !isDuplicate ? err.humanMessage : null;

  const reset = () => {
    setEmail('');
    setFullName('');
    setRole('EMPLEADO');
    setPassword('');
    setTouched(false);
    createUser.reset();
  };

  const close = () => {
    reset();
    onClose();
  };

  const submit = () => {
    setTouched(true);
    if (!validEmail || !validName || !validPassword) return;
    createUser.mutate(
      { email: emailTrim, fullName: nameTrim, role, password },
      { onSuccess: close },
    );
  };

  return (
    <Modal
      open={open}
      title="Nuevo usuario"
      onClose={close}
      footer={
        <>
          <Button variant="secondary" onClick={close} disabled={createUser.isPending}>
            Cancelar
          </Button>
          <Button onClick={submit} loading={createUser.isPending} disabled={!canSubmit}>
            Crear usuario
          </Button>
        </>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        {generalError && (
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
            {generalError}
          </div>
        )}

        <Input
          label="Email"
          type="email"
          autoComplete="off"
          placeholder="nombre@agencia.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={
            isDuplicate
              ? 'Ya existe un usuario con ese email.'
              : touched && !validEmail
                ? 'Ingresá un email válido.'
                : null
          }
        />

        <Input
          label="Nombre completo"
          placeholder="Ej. Ana Gómez"
          value={fullName}
          onChange={(e) => setFullName(e.target.value)}
          error={touched && !validName ? 'Ingresá el nombre.' : null}
        />

        <div>
          <div style={{ fontSize: 12.5, fontWeight: 500, color: '#3D4757', marginBottom: 7 }}>Rol</div>
          <SegmentedControl<CreateUserRequestRole>
            ariaLabel="Rol del usuario"
            value={role}
            onChange={setRole}
            options={[
              { value: 'EMPLEADO', label: 'Empleado' },
              { value: 'ADMIN', label: 'Administrador' },
            ]}
          />
        </div>

        <PasswordInput
          label="Contraseña"
          autoComplete="new-password"
          placeholder="Mínimo 8 caracteres"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={touched && !validPassword ? 'La contraseña necesita al menos 8 caracteres.' : null}
        />
      </div>
    </Modal>
  );
}

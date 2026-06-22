/**
 * Mutaciones del track Administración. Los hooks generados por orval son
 * `useQuery` (disparan en el render); para escrituras usamos las funciones crudas
 * (`postUsers`, `patchUsersId`, …) envueltas en `useMutation`. En error lanzan
 * `ApiError` con `humanMessage` (incluye el 409 de email duplicado).
 */
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  postUsers,
  patchUsersId,
  postUsersIdPriorityClients,
  deleteUsersIdPriorityClientsClientId,
  postSyncRun,
  getGetUsersQueryKey,
  getGetUsersIdQueryKey,
  getGetUsersIdPriorityClientsQueryKey,
  getGetSyncRunsQueryKey,
} from '../../../api/generated/endpoints';
import type { CreateUserRequest, UpdateUserRequest } from '../../../api/generated/model';

export function useCreateUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateUserRequest) => postUsers(body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: getGetUsersQueryKey() });
    },
  });
}

export function useUpdateUser(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateUserRequest) => patchUsersId(id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: getGetUsersQueryKey() });
      qc.invalidateQueries({ queryKey: getGetUsersIdQueryKey(id) });
    },
  });
}

export function useAddPriorityClient(userId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (clientId: number) => postUsersIdPriorityClients(userId, { clientId }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: getGetUsersIdPriorityClientsQueryKey(userId) });
    },
  });
}

export function useRemovePriorityClient(userId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (clientId: number) => deleteUsersIdPriorityClientsClientId(userId, clientId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: getGetUsersIdPriorityClientsQueryKey(userId) });
    },
  });
}

export function useTriggerSync() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => postSyncRun(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: getGetSyncRunsQueryKey() });
    },
  });
}

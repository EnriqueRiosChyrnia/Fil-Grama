/**
 * Errores RFC 7807 (application/problem+json) → objeto de error con `detail` humano.
 * Regla de UX (HANDOFF §7 / CLAUDE.md): mostrar SIEMPRE lenguaje humano, nunca crudo.
 */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  /** Campos extra que el backend pueda agregar (errores de validación, etc.). */
  [key: string]: unknown;
}

export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail | null;
  /** Texto listo para mostrar al usuario (en español, sin jerga de API). */
  readonly humanMessage: string;

  constructor(status: number, problem: ProblemDetail | null, fallback?: string) {
    const human =
      problem?.detail?.trim() ||
      problem?.title?.trim() ||
      fallback ||
      defaultMessageFor(status);
    super(human);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
    this.humanMessage = human;
  }
}

function defaultMessageFor(status: number): string {
  if (status === 0) return 'No pudimos conectar con el servidor. Revisá tu conexión.';
  if (status === 401) return 'Tu sesión expiró. Iniciá sesión de nuevo.';
  if (status === 403) return 'No tenés permiso para hacer esto.';
  if (status === 404) return 'No encontramos lo que buscabas.';
  if (status === 409) return 'Hay un conflicto con datos existentes.';
  if (status >= 500) return 'Algo falló de nuestro lado. Probá de nuevo en un momento.';
  return 'Ocurrió un error. Intentá de nuevo.';
}

/** Construye un ApiError desde una Response (parsea problem+json si lo hay). */
export async function toApiError(res: Response): Promise<ApiError> {
  let problem: ProblemDetail | null = null;
  try {
    const text = await res.text();
    if (text) {
      const ct = res.headers.get('content-type') ?? '';
      if (ct.includes('json')) problem = JSON.parse(text) as ProblemDetail;
      else problem = { detail: text };
    }
  } catch {
    /* cuerpo ilegible: usamos el mensaje por defecto del status */
  }
  return new ApiError(res.status, problem);
}

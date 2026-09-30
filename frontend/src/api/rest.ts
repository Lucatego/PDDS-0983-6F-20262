// Cliente REST mínimo para el backend Spring Boot. Los errores del servidor se esperan como
// { "mensaje": "..." } (ProblemDetail de Spring también se acepta por "detail").
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

export function createRestClient(baseUrl: string) {
  async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
    let res: Response;
    try {
      res = await fetch(baseUrl + path, {
        ...init,
        headers: { Accept: 'application/json', ...(init.headers ?? {}) },
      });
    } catch {
      throw new ApiError('No se pudo conectar con el servidor de PaqRap.', 0);
    }
    if (!res.ok) {
      let msg = `Error ${res.status} del servidor.`;
      try {
        const body = await res.json();
        msg = body.mensaje ?? body.detail ?? body.message ?? msg;
      } catch {
        /* cuerpo vacío o no JSON */
      }
      throw new ApiError(msg, res.status);
    }
    if (res.status === 204) return undefined as T;
    const ct = res.headers.get('content-type') ?? '';
    return (ct.includes('json') ? res.json() : undefined) as Promise<T>;
  }

  return {
    get: <T>(path: string) => request<T>(path),
    post: <T>(path: string, body?: unknown) =>
      request<T>(path, {
        method: 'POST',
        headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
        body: body === undefined ? undefined : JSON.stringify(body),
      }),
    postText: <T>(path: string, text: string) =>
      request<T>(path, { method: 'POST', headers: { 'Content-Type': 'text/plain; charset=utf-8' }, body: text }),
  };
}

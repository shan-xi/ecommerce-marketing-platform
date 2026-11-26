export type HealthStatus = 'UP' | 'DOWN' | 'UNKNOWN';

export interface HealthResponse {
  status: HealthStatus;
}

export async function fetchBackendHealth(): Promise<HealthResponse> {
  const response = await fetch('/actuator/health', {
    method: 'GET',
    headers: { Accept: 'application/json' },
  });

  if (!response.ok) {
    throw new Error(`Backend health check failed (HTTP ${response.status})`);
  }

  const payload: unknown = await response.json();
  if (
    typeof payload !== 'object' ||
    payload === null ||
    !('status' in payload) ||
    typeof payload.status !== 'string'
  ) {
    throw new Error('Backend health response has an unexpected shape');
  }

  const status = payload.status;
  if (status !== 'UP' && status !== 'DOWN') {
    return { status: 'UNKNOWN' };
  }

  return { status };
}

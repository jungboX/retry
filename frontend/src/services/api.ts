const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081';

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers ?? {}),
    },
    ...options,
  });

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }

  return response.json() as Promise<T>;
}

export const api = {
  analyzeJob: (payload: { rawRequest: string }) => request<{ workType: string }>(`/api/jobs/analyze`, {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  createJob: (payload: Record<string, unknown>) => request<{ id: string }>(`/api/jobs`, {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  getMatches: (jobId: string) => request(`/api/jobs/${jobId}/matches`),
  farmerApprove: (matchId: string) => request(`/api/matches/${matchId}/farmer-approve`, { method: 'POST' }),
  workerAccept: (matchId: string) => request(`/api/matches/${matchId}/worker-accept`, { method: 'POST' }),
  demoState: () => request(`/api/demo/state`),
};

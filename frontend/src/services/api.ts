import type { DemoState, JobAnalysisResponse, Match } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';
const SESSION_STORAGE_KEY = 'cheongdo-demo-session';

function sessionId(): string {
  let id = localStorage.getItem(SESSION_STORAGE_KEY);
  if (!id) {
    id = crypto.randomUUID();
    localStorage.setItem(SESSION_STORAGE_KEY, id);
  }
  return id;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      'X-Demo-Session-Id': sessionId(),
      ...(options.headers ?? {}),
    },
    ...options,
  });

  if (!response.ok) {
    const body = await response.json().catch(() => undefined) as { message?: string; detail?: string } | undefined;
    throw new Error(body?.message ?? body?.detail ?? `요청에 실패했습니다 (${response.status}).`);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export const api = {
  analyzeJob: (payload: { rawRequest: string }) => request<JobAnalysisResponse>(`/api/jobs/analyze`, {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  createJob: (analysisId: string) => request<{ id: string; status: string; matches: Match[]; job: Record<string, unknown> }>(`/api/jobs`, {
    method: 'POST',
    body: JSON.stringify({ analysisId }),
  }),
  getMatches: (jobId: string) => request<Match[]>(`/api/jobs/${encodeURIComponent(jobId)}/matches`),
  confirmSafety: (jobId: string, confirmed: boolean) => request(`/api/jobs/${encodeURIComponent(jobId)}/safety-confirm`, {
    method: 'POST',
    body: JSON.stringify({ confirmed }),
  }),
  farmerApprove: (matchId: string) => request<Match>(`/api/matches/${encodeURIComponent(matchId)}/farmer-approve`, { method: 'POST' }),
  workerAccept: (matchId: string) => request<Match>(`/api/matches/${encodeURIComponent(matchId)}/worker-accept`, { method: 'POST' }),
  demoState: () => request<DemoState>(`/api/demo/state`),
};

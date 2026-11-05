import type { DemoState, JobAnalysisResponse, JobRequest, JobStatus, Match } from '../types';

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
    const contentType = response.headers.get('content-type') ?? '';
    if (contentType.includes('application/json')) {
      const body = await response.json() as { message?: string; detail?: string };
      throw new Error(body.message ?? body.detail ?? `요청에 실패했습니다 (${response.status}).`);
    }
    if (response.status >= 500) {
      throw new Error('API 서버에 연결할 수 없습니다. Aspire 백엔드가 실행 중인지 확인해 주세요.');
    }
    throw new Error(`요청에 실패했습니다 (${response.status}).`);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  if (!response.headers.get('content-type')?.includes('application/json')) {
    throw new Error('API가 JSON이 아닌 응답을 반환했습니다. API 서버와 개발 프록시 설정을 확인해 주세요.');
  }
  return response.json() as Promise<T>;
}

export const api = {
  analyzeJob: (payload: { rawRequest: string }) => request<JobAnalysisResponse>(`/api/jobs/analyze`, {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  createJob: (analysisId: string) => request<{
    id: string;
    status: JobStatus;
    matches: Match[];
    job: JobRequest;
    safetyConfirmed: boolean;
  }>(`/api/jobs`, {
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

export type JobStatus = 'AWAITING_INPUT' | 'NO_CANDIDATES' | 'CANDIDATES_READY' | 'FARMER_APPROVED' | 'CONFIRMED';

export interface JobRequest {
  workType: string | null;
  location: string | null;
  startDate: string | null;
  endDate: string | null;
  workersNeeded: number | null;
  dailyPay: number | null;
  mealProvided: boolean | null;
  hazardFlags: string[];
  missingFields: string[];
  safetyReview?: {
    riskLevel: string;
    requiredChecks: string[];
    confirmationRequired: boolean;
  };
}

export interface RecommendedWorker {
  workerId: string;
  nickname: string;
  score: number;
  scoreReasons: string[];
}

export interface Match {
  id: string;
  jobId: string;
  workerId: string;
  nickname: string;
  totalScore: number;
  scoreReasons: string[];
  farmerApproved: boolean;
  workerAccepted: boolean;
  status: JobStatus;
}

export interface JobAnalysisResponse {
  analysisId: string;
  analysis: {
    job: JobRequest;
    recommendedWorkers: RecommendedWorker[];
    status: JobStatus;
    explanation: string;
  };
}

export interface DemoState {
  jobId: string | null;
  job: Partial<JobRequest> | null;
  safetyConfirmed: boolean;
  matches: Match[];
}

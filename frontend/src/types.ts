export type JobStatus = 'DRAFT' | 'CANDIDATES_READY' | 'FARMER_APPROVED' | 'CONFIRMED';

export interface JobRequest {
  rawRequest?: string;
  workType: string;
  location: string;
  startDate: string;
  endDate: string;
  workersNeeded: number;
  dailyPay: number;
  mealProvided: boolean;
  hazardFlags: string[];
}

export interface Worker {
  id: string;
  nickname: string;
  availableStart: string;
  availableEnd: string;
  homeLocation: string;
  travelRadiusKm: number;
  workTypes: string[];
  experienceLevel: string;
  status: 'AVAILABLE' | 'BUSY';
}

export interface Match {
  id: string;
  jobId: string;
  workerId: string;
  totalScore: number;
  scoreReasons: string[];
  farmerApproved: boolean;
  workerAccepted: boolean;
  status: JobStatus;
}

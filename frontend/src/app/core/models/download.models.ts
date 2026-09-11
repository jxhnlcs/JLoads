export type DownloadType = 'AUDIO' | 'VIDEO';

export type DownloadQuality = 'BEST' | 'HIGH' | 'MEDIUM' | 'LOW';

export type DownloadStatus =
  | 'QUEUED'
  | 'ANALYZING'
  | 'DOWNLOADING'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

export type DownloadEventType =
  | 'JOB_QUEUED'
  | 'JOB_STARTED'
  | 'JOB_PROGRESS'
  | 'JOB_PROCESSING'
  | 'JOB_COMPLETED'
  | 'JOB_FAILED'
  | 'JOB_CANCELLED'
  | 'JOB_EXPIRED';

export const TERMINAL_STATUSES: ReadonlySet<DownloadStatus> = new Set(['COMPLETED', 'FAILED', 'CANCELLED']);

export interface FormatOption {
  type: DownloadType;
  quality: DownloadQuality;
  label: string;
  description: string;
  estimatedSizeBytes?: number;
}

export interface VideoAnalysis {
  videoId: string;
  url: string;
  title: string;
  thumbnail: string;
  channel?: string;
  duration?: number;
  availableOptions: FormatOption[];
}

export interface DownloadJob {
  id: string;
  sourceUrl: string;
  title?: string;
  channel?: string;
  thumbnailUrl?: string;
  duration?: number;
  type: DownloadType;
  quality: DownloadQuality;
  status: DownloadStatus;
  progress: number;
  downloadedBytes: number;
  totalBytes: number;
  speed?: string;
  eta?: string;
  speedBytesPerSecond: number;
  filename?: string;
  fileSizeBytes?: number;
  fileAvailable: boolean;
  fileExpired: boolean;
  errorCode?: string;
  errorMessage?: string;
  createdAt: string;
  startedAt?: string;
  completedAt?: string;
  updatedAt: string;
}

export interface CreateDownloadRequest {
  url: string;
  type: DownloadType;
  quality: DownloadQuality;
}

export interface CreateBatchDownloadRequest {
  urls: string[];
  type: DownloadType;
  quality: DownloadQuality;
}

export interface CreateBatchDownloadResponse {
  jobs: DownloadJob[];
}

/** Limites de uso publicados pelo backend (GET /api/config). */
export interface PublicConfig {
  maxBatchSize: number;
  maxActiveJobsPerClient: number;
  maxConcurrentDownloads: number;
  maxFileSizeBytes: number;
  maxMediaDurationSeconds: number;
  fileRetentionMinutes: number;
}

export interface CreateDownloadResponse {
  jobId: string;
  status: DownloadStatus;
  job: DownloadJob;
}

export interface DownloadEvent {
  event: DownloadEventType;
  jobId: string;
  status: DownloadStatus;
  progress: number;
  downloadedBytes: number;
  totalBytes: number;
  speed?: string;
  eta?: string;
  timestamp: string;
  job: DownloadJob;
}

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  correlationId?: string;
}

/** Estado da tela de análise (antes de o job existir). */
export type AnalyzerState = 'IDLE' | 'ANALYZING' | 'READY' | 'BATCH';

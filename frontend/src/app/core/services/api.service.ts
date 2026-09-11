import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateBatchDownloadRequest,
  CreateBatchDownloadResponse,
  CreateDownloadRequest,
  CreateDownloadResponse,
  DownloadJob,
  PublicConfig,
  VideoAnalysis,
} from '../models/download.models';

/** Acesso HTTP à API REST do backend. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  getConfig(): Observable<PublicConfig> {
    return this.http.get<PublicConfig>('/api/config');
  }

  analyze(url: string): Observable<VideoAnalysis> {
    return this.http.post<VideoAnalysis>('/api/videos/analyze', { url });
  }

  createDownload(request: CreateDownloadRequest): Observable<CreateDownloadResponse> {
    return this.http.post<CreateDownloadResponse>('/api/downloads', request);
  }

  createBatch(request: CreateBatchDownloadRequest): Observable<CreateBatchDownloadResponse> {
    return this.http.post<CreateBatchDownloadResponse>('/api/downloads/batch', request);
  }

  listDownloads(): Observable<DownloadJob[]> {
    return this.http.get<DownloadJob[]>('/api/downloads');
  }

  getDownload(id: string): Observable<DownloadJob> {
    return this.http.get<DownloadJob>(`/api/downloads/${encodeURIComponent(id)}`);
  }

  cancelDownload(id: string): Observable<DownloadJob> {
    return this.http.delete<DownloadJob>(`/api/downloads/${encodeURIComponent(id)}`);
  }

  fileUrl(id: string): string {
    return `/api/downloads/${encodeURIComponent(id)}/file`;
  }
}

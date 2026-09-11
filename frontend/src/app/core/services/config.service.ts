import { Injectable, inject, signal } from '@angular/core';
import { EMPTY, catchError } from 'rxjs';
import { PublicConfig } from '../models/download.models';
import { ApiService } from './api.service';

export const DEFAULT_CONFIG: PublicConfig = {
  maxBatchSize: 5,
  maxActiveJobsPerClient: 5,
  maxConcurrentDownloads: 3,
  maxFileSizeBytes: 1024 * 1024 * 1024,
  maxMediaDurationSeconds: 3 * 60 * 60,
  fileRetentionMinutes: 30,
  // Assume tudo instalado até o servidor responder, para não piscar o aviso de instalação.
  dependencies: { ytDlp: true, ffmpeg: true, jsRuntime: true },
};

/** Limites reais do servidor; usa valores padrão até a resposta chegar (ou se ela falhar). */
@Injectable({ providedIn: 'root' })
export class ConfigService {
  readonly config = signal<PublicConfig>(DEFAULT_CONFIG);

  constructor() {
    inject(ApiService)
      .getConfig()
      .pipe(catchError(() => EMPTY))
      .subscribe((config) => this.config.set(config));
  }
}

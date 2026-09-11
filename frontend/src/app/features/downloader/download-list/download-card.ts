import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { DownloadJob, TERMINAL_STATUSES } from '../../../core/models/download.models';
import { SaveStatus } from '../../../core/services/file-save.service';
import { Icon } from '../../../shared/components/icon/icon';
import { BytesPipe } from '../../../shared/pipes/bytes.pipe';
import { STATUS_PRESENTATION } from '../../../shared/status-presentation';

@Component({
  selector: 'app-download-card',
  imports: [Icon, BytesPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './download-card.html',
})
export class DownloadCard {
  readonly job = input.required<DownloadJob>();
  readonly fileUrl = input.required<string>();
  readonly cancelling = input(false);
  readonly saveStatus = input<SaveStatus | null>(null);
  readonly folderName = input<string | null>(null);
  readonly cancel = output<void>();
  readonly retry = output<void>();
  readonly dismiss = output<void>();
  readonly saveToFolder = output<void>();

  protected readonly presentation = computed(() => STATUS_PRESENTATION[this.job().status]);
  protected readonly terminal = computed(() => TERMINAL_STATUSES.has(this.job().status));
  protected readonly showProgress = computed(() => ['DOWNLOADING', 'PROCESSING', 'QUEUED', 'ANALYZING'].includes(this.job().status));
  protected readonly indeterminate = computed(() => {
    const job = this.job();
    return job.status === 'QUEUED' || job.status === 'ANALYZING' || job.status === 'PROCESSING'
      || (job.status === 'DOWNLOADING' && job.totalBytes <= 0);
  });
  protected readonly title = computed(() => this.job().title || 'Obtendo informações…');
  protected readonly typeIcon = computed(() => (this.job().type === 'AUDIO' ? 'music' : 'video'));
  protected readonly qualityLabel = computed(() => {
    const job = this.job();
    const labels = job.type === 'AUDIO'
      ? { BEST: 'MP3 máxima', HIGH: 'MP3 256 kbps', MEDIUM: 'MP3 192 kbps', LOW: 'MP3 128 kbps' }
      : { BEST: 'MP4 melhor', HIGH: 'MP4 até 1080p', MEDIUM: 'MP4 até 720p', LOW: 'MP4 até 480p' };
    return labels[job.quality];
  });

  /** Botão "Salvar na pasta" só aparece quando há pasta escolhida e o arquivo ainda não foi salvo lá. */
  protected readonly canSaveToFolder = computed(() => {
    const status = this.saveStatus();
    return this.folderName() !== null
      && !(status?.state === 'saving' || (status?.state === 'saved' && status.target === 'folder'));
  });

  protected readonly saveButtonLabel = computed(() => {
    switch (this.saveStatus()?.state) {
      case 'permission':
        return 'Autorizar e salvar';
      case 'error':
        return 'Tentar salvar';
      default:
        return 'Salvar na pasta';
    }
  });
}

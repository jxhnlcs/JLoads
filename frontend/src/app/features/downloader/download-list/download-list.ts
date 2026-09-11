import { ChangeDetectionStrategy, Component, inject, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DownloadJob } from '../../../core/models/download.models';
import { toFriendlyError } from '../../../core/services/api-error';
import { DownloadService } from '../../../core/services/download.service';
import { FileSaveService } from '../../../core/services/file-save.service';
import { Icon } from '../../../shared/components/icon/icon';
import { BytesPipe } from '../../../shared/pipes/bytes.pipe';
import { DownloadCard } from './download-card';

@Component({
  selector: 'app-download-list',
  imports: [DownloadCard, Icon, BytesPipe, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="downloads" aria-labelledby="downloads-title">
      <header class="section-header">
        <h2 id="downloads-title" class="section-header__title">
          Downloads
          @if (downloads.activeCount() > 0) {
            <span class="count-pill">{{ downloads.activeCount() }} ativo{{ downloads.activeCount() > 1 ? 's' : '' }}</span>
            @if (downloads.totalSpeedBytesPerSecond() > 0) {
              <span class="downloads__speed" title="Velocidade total">
                <app-icon name="download" [size]="14" /> {{ downloads.totalSpeedBytesPerSecond() | bytes }}/s
              </span>
            }
          }
        </h2>
        @if (downloads.hasFinished()) {
          <button type="button" class="btn btn--ghost btn--sm" (click)="downloads.clearFinished()">Limpar finalizados</button>
        }
      </header>

      <p class="downloads__save">
        <app-icon name="folder" [size]="14" />
        @if (saver.autoSave()) {
          <span>Salvando automaticamente em
            <strong>{{ saver.directoryName() ? '“' + saver.directoryName() + '”' : 'Downloads do navegador' }}</strong></span>
        } @else {
          <span>Salvamento automático desativado</span>
        }
        <a routerLink="/settings" class="link">Alterar</a>
      </p>

      @if (saver.autoSave() && saver.needsPermission()) {
        <div class="alert alert--warning" role="status">
          <app-icon name="alert" [size]="18" />
          <span>O navegador precisa da sua autorização para salvar em “{{ saver.directoryName() }}”.</span>
          <button type="button" class="btn btn--secondary btn--sm" (click)="saver.authorize()">Autorizar</button>
        </div>
      }

      @if (actionError()) {
        <div class="alert alert--danger" role="alert">
          <app-icon name="alert" [size]="18" />
          <span>{{ actionError() }}</span>
          <button type="button" class="btn btn--ghost btn--icon" aria-label="Fechar" (click)="actionError.set(null)">
            <app-icon name="x" [size]="16" />
          </button>
        </div>
      }

      @if (!downloads.loaded()) {
        <div class="card skeleton" aria-hidden="true"></div>
      } @else {
        <div class="downloads__list">
          @for (job of downloads.jobs(); track job.id) {
            <app-download-card
              [job]="job"
              [fileUrl]="downloads.fileUrl(job.id)"
              [cancelling]="cancelling().has(job.id)"
              [saveStatus]="saver.statusOf(job.id)"
              [folderName]="saver.directoryName()"
              (cancel)="cancel(job)"
              (retry)="retry.emit(job)"
              (dismiss)="downloads.dismiss(job.id)"
              (saveToFolder)="saver.saveToFolder(job, true)"
            />
          } @empty {
            <div class="empty-state">
              <app-icon name="wave" [size]="28" />
              <p>Nenhum download ainda. Os arquivos que você baixar aparecem aqui com o progresso em tempo real.</p>
            </div>
          }
        </div>
      }
    </section>
  `,
})
export class DownloadList {
  protected readonly downloads = inject(DownloadService);
  protected readonly saver = inject(FileSaveService);
  protected readonly cancelling = signal<ReadonlySet<string>>(new Set());
  protected readonly actionError = signal<string | null>(null);

  readonly retry = output<DownloadJob>();

  protected cancel(job: DownloadJob): void {
    this.cancelling.update((ids) => new Set([...ids, job.id]));
    this.actionError.set(null);
    this.downloads.cancel(job.id).subscribe({
      next: () => this.finishCancel(job.id),
      error: (error: unknown) => {
        this.finishCancel(job.id);
        this.actionError.set(toFriendlyError(error).message);
      },
    });
  }

  private finishCancel(id: string): void {
    this.cancelling.update((ids) => new Set([...ids].filter((current) => current !== id)));
  }
}

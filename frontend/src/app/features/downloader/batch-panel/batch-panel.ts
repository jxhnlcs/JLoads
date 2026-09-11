import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { VideoAnalysis } from '../../../core/models/download.models';
import { FORMAT_PRESETS } from '../../../core/models/format-presets';
import { Icon } from '../../../shared/components/icon/icon';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { FormatSelection, FormatSelector } from '../format-selector/format-selector';

/** Link do envio em lote com o resultado da sua análise individual. */
export interface BatchItem {
  url: string;
  status: 'loading' | 'ready' | 'error';
  analysis?: VideoAnalysis;
  error?: string;
  /** Erro definitivo (indisponível, privado, restrito…): o link não será enviado. */
  blocking?: boolean;
}

const VIDEO_ID = /(?:[?&]v=|youtu\.be\/|\/shorts\/|\/embed\/|\/live\/)([A-Za-z0-9_-]{11})/;

@Component({
  selector: 'app-batch-panel',
  imports: [FormatSelector, Icon, DurationPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="card batch" aria-labelledby="batch-title">
      <header class="preview__header">
        <div>
          <h2 id="batch-title" class="preview__title">{{ items().length }} links</h2>
          <p class="preview__channel">Escolha um formato para todos.</p>
        </div>
        <button type="button" class="btn btn--ghost btn--icon" (click)="dismiss.emit()" aria-label="Cancelar envio em lote"
                title="Cancelar">
          <app-icon name="x" [size]="18" />
        </button>
      </header>

      <ul class="batch__list">
        @for (item of items(); track item.url; let i = $index) {
          <li class="batch__item" [class.batch__item--blocked]="item.blocking" [attr.aria-busy]="item.status === 'loading'">
            <div class="batch__thumb" aria-hidden="true">
              @if (item.analysis; as a) {
                @if (!failedThumbnails().has(item.url)) {
                  <img [src]="a.thumbnail" alt="" loading="lazy" referrerpolicy="no-referrer"
                       (error)="markThumbnailFailed(item.url)" />
                } @else {
                  <app-icon name="video" [size]="16" />
                }
              } @else if (item.status === 'loading') {
                <span class="batch__thumb-skeleton skeleton-block"></span>
              } @else {
                <app-icon name="alert" [size]="16" />
              }
            </div>

            <div class="batch__info">
              @if (item.analysis; as a) {
                <p class="batch__title" [title]="a.title">{{ a.title }}</p>
                <p class="batch__meta">
                  @if (a.channel) { <span class="batch__channel">{{ a.channel }}</span> }
                  @if (a.duration) { <span>{{ a.duration | duration }}</span> }
                  @if (duplicates().has(item.url)) { <span class="batch__warning">Repetido — baixado uma vez</span> }
                </p>
              } @else if (item.status === 'loading') {
                <p class="batch__title batch__title--muted">Carregando informações…</p>
                <p class="batch__meta batch__url">{{ display(item.url) }}</p>
              } @else {
                <p class="batch__title batch__url" [title]="item.url">{{ display(item.url) }}</p>
                <p class="batch__meta" [class.batch__meta--danger]="item.blocking" [class.batch__warning]="!item.blocking">
                  {{ item.error }} {{ item.blocking ? 'Não será enviado.' : 'O download será tentado mesmo assim.' }}
                </p>
              }
            </div>

            <button type="button" class="btn btn--ghost btn--icon btn--sm" (click)="remove.emit(i)" [disabled]="busy()"
                    [attr.aria-label]="'Remover link ' + (i + 1)" title="Remover">
              <app-icon name="x" [size]="16" />
            </button>
          </li>
        }
      </ul>

      <app-format-selector [options]="presets" [busy]="busy() || sendableCount() === 0" [submitLabel]="submitLabel()"
                           (download)="download.emit($event)" />
      <p class="field__hint">Para vídeo, a qualidade é um limite: se a origem tiver resolução menor, a melhor disponível é usada.</p>
      <ng-content />
    </article>
  `,
})
export class BatchPanel {
  readonly items = input.required<BatchItem[]>();
  readonly busy = input(false);
  readonly download = output<FormatSelection>();
  readonly remove = output<number>();
  readonly dismiss = output<void>();

  protected readonly presets = FORMAT_PRESETS;
  protected readonly failedThumbnails = signal<ReadonlySet<string>>(new Set());

  protected readonly sendableCount = computed(() => this.items().filter((item) => !item.blocking).length);

  protected readonly submitLabel = computed(() => {
    const count = this.sendableCount();
    return count === 1 ? 'Baixar 1 item' : `Baixar ${count} itens`;
  });

  /** Links diferentes que apontam para o mesmo vídeo (o backend envia apenas o primeiro). */
  protected readonly duplicates = computed(() => {
    const seen = new Set<string>();
    const duplicated = new Set<string>();
    for (const item of this.items()) {
      const id = item.analysis?.videoId;
      if (!id) {
        continue;
      }
      if (seen.has(id)) {
        duplicated.add(item.url);
      }
      seen.add(id);
    }
    return duplicated;
  });

  protected markThumbnailFailed(url: string): void {
    this.failedThumbnails.update((current) => new Set([...current, url]));
  }

  protected display(url: string): string {
    const id = VIDEO_ID.exec(url)?.[1];
    return id ? `youtu.be/${id}` : url;
  }
}

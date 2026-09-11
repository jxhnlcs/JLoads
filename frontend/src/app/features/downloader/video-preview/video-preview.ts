import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { VideoAnalysis } from '../../../core/models/download.models';
import { Icon } from '../../../shared/components/icon/icon';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';

@Component({
  selector: 'app-video-preview',
  imports: [Icon, DurationPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="card preview" aria-labelledby="preview-title">
      <div class="preview__media">
        @if (!thumbnailFailed()) {
          <img class="preview__thumb" [src]="analysis().thumbnail" alt="" loading="lazy" referrerpolicy="no-referrer"
               (error)="thumbnailFailed.set(true)" />
        } @else {
          <div class="preview__thumb preview__thumb--fallback"><app-icon name="video" [size]="40" /></div>
        }
        @if (analysis().duration) {
          <span class="preview__duration">{{ analysis().duration | duration }}</span>
        }
      </div>

      <div class="preview__body">
        <header class="preview__header">
          <div>
            <h2 id="preview-title" class="preview__title">{{ analysis().title }}</h2>
            @if (analysis().channel) {
              <p class="preview__channel">{{ analysis().channel }}</p>
            }
          </div>
          <button type="button" class="btn btn--ghost btn--icon" (click)="dismiss.emit()" aria-label="Analisar outro link"
                  title="Analisar outro link">
            <app-icon name="x" [size]="18" />
          </button>
        </header>
        <ng-content />
      </div>
    </article>
  `,
})
export class VideoPreview {
  readonly analysis = input.required<VideoAnalysis>();
  readonly dismiss = output<void>();
  protected readonly thumbnailFailed = signal(false);
}

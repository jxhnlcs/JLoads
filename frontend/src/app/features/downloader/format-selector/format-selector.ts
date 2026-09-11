import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DownloadQuality, DownloadType, FormatOption } from '../../../core/models/download.models';
import { PreferencesService } from '../../../core/services/preferences.service';
import { Icon } from '../../../shared/components/icon/icon';
import { BytesPipe } from '../../../shared/pipes/bytes.pipe';

export interface FormatSelection {
  type: DownloadType;
  quality: DownloadQuality;
}

@Component({
  selector: 'app-format-selector',
  imports: [ReactiveFormsModule, Icon, BytesPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './format-selector.html',
})
export class FormatSelector {
  private readonly preferences = inject(PreferencesService);

  readonly options = input.required<FormatOption[]>();
  readonly busy = input(false);
  readonly submitLabel = input('Baixar');
  readonly download = output<FormatSelection>();

  protected readonly form = new FormGroup({
    type: new FormControl<DownloadType>(this.preferences.defaultType(), { nonNullable: true, validators: Validators.required }),
    quality: new FormControl<DownloadQuality>('BEST', { nonNullable: true, validators: Validators.required }),
  });

  private readonly selectedType = toSignal(this.form.controls.type.valueChanges, {
    initialValue: this.form.controls.type.value,
  });
  private readonly selectedQuality = toSignal(this.form.controls.quality.valueChanges, {
    initialValue: this.form.controls.quality.value,
  });

  protected readonly hasAudio = computed(() => this.options().some((o) => o.type === 'AUDIO'));
  protected readonly hasVideo = computed(() => this.options().some((o) => o.type === 'VIDEO'));
  protected readonly qualityOptions = computed(() => this.options().filter((o) => o.type === this.selectedType()));
  protected readonly selectedOption = computed(() =>
    this.qualityOptions().find((o) => o.quality === this.selectedQuality()),
  );

  constructor() {
    // Garante que tipo e qualidade selecionados existam para o conteúdo analisado.
    effect(() => {
      const options = this.options();
      const typeControl = this.form.controls.type;
      if (!options.some((o) => o.type === typeControl.value) && options.length > 0) {
        typeControl.setValue(options[0].type);
      }
    });
    effect(() => {
      const available = this.qualityOptions();
      const qualityControl = this.form.controls.quality;
      if (available.length > 0 && !available.some((o) => o.quality === qualityControl.value)) {
        qualityControl.setValue(available[0].quality);
      }
    });
    effect(() => {
      if (this.busy()) {
        this.form.disable({ emitEvent: false });
      } else {
        this.form.enable({ emitEvent: false });
      }
    });
  }

  protected selectType(type: DownloadType): void {
    if (this.busy()) {
      return;
    }
    this.form.controls.type.setValue(type);
    this.preferences.defaultType.set(type);
  }

  protected submit(): void {
    if (this.form.invalid || this.busy() || !this.selectedOption()) {
      return;
    }
    this.download.emit(this.form.getRawValue());
  }
}

import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, input, output, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Icon } from '../../../shared/components/icon/icon';

const ALLOWED_HOSTS = /^(www\.|m\.|music\.)?youtube\.com$|^(www\.)?youtu\.be$/i;

/** Separa o texto colado em links (quebra de linha, espaço, vírgula ou ponto e vírgula), sem repetições. */
export function parseLinks(text: string | null | undefined): string[] {
  const tokens = (text ?? '').split(/[\s,;]+/).map((token) => token.trim()).filter(Boolean);
  return [...new Set(tokens)];
}

/** Validação leve no cliente para feedback imediato; a validação definitiva acontece no backend. */
export function isYoutubeUrl(raw: string): boolean {
  try {
    const url = new URL(raw.includes('://') ? raw : `https://${raw}`);
    return ALLOWED_HOSTS.test(url.hostname);
  } catch {
    return false;
  }
}

@Component({
  selector: 'app-url-input',
  imports: [ReactiveFormsModule, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './url-input.html',
})
export class UrlInput {
  readonly busy = input(false);
  readonly serverError = input<string | null>(null);
  readonly maxLinks = input(5);
  readonly analyze = output<string>();
  readonly batch = output<string[]>();

  private readonly textarea = viewChild<ElementRef<HTMLTextAreaElement>>('textarea');

  protected readonly form = new FormGroup({
    links: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(20_000), (control) => this.validateLinks(control)],
    }),
  });

  private readonly value = toSignal(this.form.controls.links.valueChanges, { initialValue: '' });
  protected readonly links = computed(() => parseLinks(this.value()));
  protected readonly submitLabel = computed(() =>
    this.links().length > 1 ? `Continuar com ${this.links().length} links` : 'Analisar',
  );

  protected readonly clipboardSupported = typeof navigator !== 'undefined' && !!navigator.clipboard?.readText;

  constructor() {
    effect(() => {
      const control = this.form.controls.links;
      if (this.busy()) {
        control.disable({ emitEvent: false });
      } else {
        control.enable({ emitEvent: false });
      }
    });
    effect(() => {
      this.maxLinks();
      this.form.controls.links.updateValueAndValidity({ emitEvent: false });
    });
  }

  protected get linksControl(): FormControl<string> {
    return this.form.controls.links;
  }

  /** Limpa o campo (após enviar um lote). */
  reset(): void {
    this.form.reset();
    queueMicrotask(() => this.autosize());
  }

  protected submit(): void {
    if (this.busy()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const links = this.links();
    if (links.length === 1) {
      this.analyze.emit(links[0]);
    } else {
      this.batch.emit(links);
    }
  }

  /** Enter envia; Shift+Enter adiciona uma nova linha. */
  protected onEnter(event: Event): void {
    if ((event as KeyboardEvent).shiftKey) {
      return;
    }
    event.preventDefault();
    this.submit();
  }

  protected autosize(): void {
    const element = this.textarea()?.nativeElement;
    if (element) {
      element.style.height = 'auto';
      element.style.height = `${Math.min(element.scrollHeight, 200)}px`;
    }
  }

  protected async pasteFromClipboard(): Promise<void> {
    try {
      const text = (await navigator.clipboard.readText()).trim();
      if (!text) {
        return;
      }
      const current = this.linksControl.value.trim();
      this.linksControl.setValue((current ? `${current}\n${text}` : text).slice(0, 20_000));
      this.linksControl.markAsTouched();
      queueMicrotask(() => this.autosize());
      if (!current && this.form.valid && this.links().length === 1) {
        this.submit();
      }
    } catch {
      // permissão negada: o usuário pode colar manualmente
    }
  }

  private validateLinks(control: AbstractControl<string>): ValidationErrors | null {
    const links = parseLinks(control.value);
    if (links.length === 0) {
      return null;
    }
    const max = this.maxLinks();
    if (links.length > max) {
      return { tooMany: { count: links.length, max } };
    }
    const invalid = links.findIndex((link) => !isYoutubeUrl(link));
    return invalid >= 0 ? { invalidLink: { position: invalid + 1 } } : null;
  }
}

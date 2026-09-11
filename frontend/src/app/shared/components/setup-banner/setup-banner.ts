import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ConfigService } from '../../../core/services/config.service';
import { Icon } from '../icon/icon';

type OperatingSystem = 'windows' | 'mac' | 'linux';
type DependencyKey = 'ytDlp' | 'ffmpeg' | 'jsRuntime';

interface Dependency {
  key: DependencyKey;
  name: string;
  purpose: string;
  install: Record<OperatingSystem, string>;
}

const DEPENDENCIES: Dependency[] = [
  {
    key: 'ytDlp',
    name: 'yt-dlp',
    purpose: 'Faz o download — sem ele nada funciona',
    install: {
      windows: 'winget install yt-dlp.yt-dlp',
      mac: 'brew install yt-dlp',
      linux: 'sudo curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp && sudo chmod a+rx /usr/local/bin/yt-dlp',
    },
  },
  {
    key: 'ffmpeg',
    name: 'FFmpeg',
    purpose: 'Converte para MP3 e junta vídeo com áudio',
    install: {
      windows: 'winget install Gyan.FFmpeg',
      mac: 'brew install ffmpeg',
      linux: 'sudo apt install -y ffmpeg',
    },
  },
  {
    key: 'jsRuntime',
    name: 'Deno',
    purpose: 'Runtime JavaScript que o YouTube exige',
    install: {
      windows: 'winget install DenoLand.Deno',
      mac: 'brew install deno',
      linux: 'curl -fsSL https://deno.land/install.sh | sh',
    },
  },
];

const SYSTEMS: { value: OperatingSystem; label: string }[] = [
  { value: 'windows', label: 'Windows' },
  { value: 'mac', label: 'macOS' },
  { value: 'linux', label: 'Linux' },
];

function detectOperatingSystem(): OperatingSystem {
  const agent = typeof navigator === 'undefined' ? '' : navigator.userAgent;
  if (/Windows/i.test(agent)) {
    return 'windows';
  }
  return /Mac/i.test(agent) ? 'mac' : 'linux';
}

/** Aviso exibido quando o JLoads não encontrou alguma ferramenta externa ao iniciar. */
@Component({
  selector: 'app-setup-banner',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (missing().length > 0) {
      <section class="card setup" role="alert" aria-labelledby="setup-title">
        <header class="setup__header">
          <span class="setup__icon"><app-icon name="alert" [size]="20" /></span>
          <div>
            <h2 id="setup-title" class="setup__title">
              {{ missing().length === 1 ? 'Falta instalar uma ferramenta' : 'Faltam ' + missing().length + ' ferramentas' }}
            </h2>
            <p class="setup__text">
              {{ blocking() ? 'Sem o yt-dlp, o JLoads não consegue baixar nada.' : 'O JLoads funciona, mas alguns downloads podem falhar.' }}
              Instale e depois <strong>feche e abra o JLoads novamente</strong>.
            </p>
          </div>
        </header>

        <div class="segmented segmented--sm setup__systems" role="radiogroup" aria-label="Sistema operacional">
          @for (system of systems; track system.value) {
            <button type="button" role="radio" class="segmented__option"
                    [class.segmented__option--active]="os() === system.value"
                    [attr.aria-checked]="os() === system.value" (click)="os.set(system.value)">
              {{ system.label }}
            </button>
          }
        </div>

        <ul class="setup__list">
          @for (dependency of missing(); track dependency.key) {
            <li class="setup__item">
              <div class="setup__tool">
                <strong>{{ dependency.name }}</strong>
                <span>{{ dependency.purpose }}</span>
              </div>
              <div class="setup__command">
                <code>{{ dependency.install[os()] }}</code>
                <button type="button" class="btn btn--ghost btn--icon btn--sm" (click)="copy(dependency)"
                        [attr.aria-label]="'Copiar comando de instalação do ' + dependency.name"
                        [title]="copied() === dependency.key ? 'Copiado' : 'Copiar'">
                  <app-icon [name]="copied() === dependency.key ? 'check' : 'clipboard'" [size]="16" />
                </button>
              </div>
            </li>
          }
        </ul>

        <p class="setup__hint">
          Prefere não instalar? Coloque o executável numa pasta <code>bin</code> ao lado do JLoads.
          <a class="link" href="https://github.com/jxhnlcs/JLoads#instalando-as-dependências" target="_blank" rel="noopener">
            Guia de instalação
          </a>
        </p>
      </section>
    }
  `,
})
export class SetupBanner {
  private readonly config = inject(ConfigService).config;

  protected readonly systems = SYSTEMS;
  protected readonly os = signal<OperatingSystem>(detectOperatingSystem());
  protected readonly copied = signal<DependencyKey | null>(null);

  protected readonly missing = computed(() => {
    const available = this.config().dependencies;
    return DEPENDENCIES.filter((dependency) => !available[dependency.key]);
  });

  protected readonly blocking = computed(() => this.missing().some((dependency) => dependency.key === 'ytDlp'));

  protected copy(dependency: Dependency): void {
    navigator.clipboard
      ?.writeText(dependency.install[this.os()])
      .then(() => {
        this.copied.set(dependency.key);
        setTimeout(() => this.copied.set(null), 2000);
      })
      .catch(() => undefined);
  }
}

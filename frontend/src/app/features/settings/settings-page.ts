import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DownloadType } from '../../core/models/download.models';
import { DownloadService } from '../../core/services/download.service';
import { FileSaveService } from '../../core/services/file-save.service';
import { PreferencesService } from '../../core/services/preferences.service';
import { WebSocketService } from '../../core/services/websocket.service';
import { Icon } from '../../shared/components/icon/icon';

@Component({
  selector: 'app-settings-page',
  imports: [RouterLink, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page">
      <header class="page__header">
        <h1 class="page__title">Preferências</h1>
        <p class="muted">Configurações salvas apenas neste navegador.</p>
      </header>

      <div class="card settings" aria-labelledby="save-settings-title">
        <h2 id="save-settings-title" class="settings__section">Salvar downloads</h2>

        <div class="settings__row">
          <div>
            <h3 class="settings__label">Pasta de destino</h3>
            <p class="muted">Onde os arquivos concluídos são gravados.</p>
          </div>
          <div class="settings__control">
            <span class="folder-chip" [title]="saver.directoryName() ?? ''">
              <app-icon name="folder" [size]="16" />
              <span class="folder-chip__name">{{ saver.directoryName() ?? 'Downloads do navegador' }}</span>
            </span>
            @if (saver.supported) {
              <button type="button" class="btn btn--secondary btn--sm" (click)="saver.chooseDirectory()">
                {{ saver.directoryName() ? 'Trocar pasta' : 'Escolher pasta' }}
              </button>
              @if (saver.directoryName()) {
                <button type="button" class="btn btn--ghost btn--icon btn--sm" (click)="saver.forgetDirectory()"
                        aria-label="Voltar para a pasta de downloads do navegador" title="Voltar para a pasta de downloads do navegador">
                  <app-icon name="x" [size]="16" />
                </button>
              }
            }
          </div>
        </div>

        @if (saver.needsPermission()) {
          <div class="alert alert--warning" role="status">
            <app-icon name="alert" [size]="18" />
            <span>O navegador precisa da sua autorização para gravar em “{{ saver.directoryName() }}”.</span>
            <button type="button" class="btn btn--secondary btn--sm" (click)="saver.authorize()">Autorizar</button>
          </div>
        } @else if (!saver.supported) {
          <p class="settings__hint">
            Este navegador não permite escolher a pasta, então os arquivos vão para a pasta de downloads configurada nele.
            A escolha de pasta funciona no Chrome, Edge e Opera para computador. No Brave, ative
            <code>brave://flags/#file-system-access-api</code>.
          </p>
        }

        <div class="settings__row">
          <div>
            <h3 class="settings__label" id="auto-save-label">Salvar automaticamente</h3>
            <p class="muted">Salva cada arquivo assim que o download termina, sem precisar clicar em “Baixar arquivo”.</p>
          </div>
          <label class="switch">
            <input type="checkbox" aria-labelledby="auto-save-label" [checked]="saver.autoSave()" (change)="toggleAutoSave($event)" />
          </label>
        </div>
      </div>

      <div class="card settings" aria-labelledby="general-settings-title">
        <h2 id="general-settings-title" class="settings__section">Geral</h2>

        <div class="settings__row">
          <div>
            <h3 class="settings__label">Tipo padrão</h3>
            <p class="muted">Formato selecionado automaticamente após analisar um link.</p>
          </div>
          <div class="segmented" role="radiogroup" aria-label="Tipo padrão">
            @for (option of types; track option.value) {
              <button type="button" role="radio" class="segmented__option"
                      [class.segmented__option--active]="preferences.defaultType() === option.value"
                      [attr.aria-checked]="preferences.defaultType() === option.value"
                      (click)="setType(option.value)">
                <app-icon [name]="option.icon" [size]="18" /> {{ option.label }}
              </button>
            }
          </div>
        </div>

        <div class="settings__row">
          <div>
            <h3 class="settings__label">Histórico</h3>
            <p class="muted">Remove da lista os downloads concluídos, cancelados ou com falha.</p>
          </div>
          <button type="button" class="btn btn--secondary" [disabled]="!downloads.hasFinished()" (click)="downloads.clearFinished()">
            <app-icon name="trash" [size]="16" /> Limpar finalizados
          </button>
        </div>

        <div class="settings__row">
          <div>
            <h3 class="settings__label">Conexão em tempo real</h3>
            <p class="muted">Atualizações de progresso via WebSocket.</p>
          </div>
          <span class="badge" [class.tone--success]="socket.state() === 'open'" [class.tone--neutral]="socket.state() !== 'open'">
            {{ socket.state() === 'open' ? 'Conectado' : 'Reconectando…' }}
          </span>
        </div>
      </div>

      <div class="card notice">
        <h2 class="settings__label">Uso responsável</h2>
        <p class="muted">
          Baixe apenas conteúdo que você tem permissão para baixar — por exemplo, seus próprios vídeos, conteúdo em domínio
          público ou licenciado para reutilização. Respeite os direitos autorais e os termos da plataforma. Este serviço não
          contorna DRM, login, paywalls ou restrições de acesso, e os arquivos são removidos automaticamente do servidor
          após um período.
        </p>
      </div>

      <a routerLink="/" class="btn btn--ghost page__back">← Voltar</a>
    </section>
  `,
})
export class SettingsPage {
  protected readonly preferences = inject(PreferencesService);
  protected readonly downloads = inject(DownloadService);
  protected readonly socket = inject(WebSocketService);
  protected readonly saver = inject(FileSaveService);

  protected readonly types = [
    { value: 'VIDEO' as DownloadType, label: 'Vídeo', icon: 'video' as const },
    { value: 'AUDIO' as DownloadType, label: 'Áudio', icon: 'music' as const },
  ];

  protected setType(type: DownloadType): void {
    this.preferences.defaultType.set(type);
  }

  protected toggleAutoSave(event: Event): void {
    this.saver.autoSave.set((event.target as HTMLInputElement).checked);
  }
}

import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ConfigService } from './core/services/config.service';
import { DownloadService } from './core/services/download.service';
import { FileSaveService } from './core/services/file-save.service';
import { WebSocketService } from './core/services/websocket.service';
import { Icon } from './shared/components/icon/icon';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
})
export class App {
  // Instanciados no shell para manter conexão em tempo real, limites e salvamento automático em todas as rotas.
  protected readonly downloads = inject(DownloadService);
  protected readonly socket = inject(WebSocketService);
  protected readonly saver = inject(FileSaveService);
  protected readonly config = inject(ConfigService);
}

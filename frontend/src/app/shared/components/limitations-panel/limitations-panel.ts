import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ConfigService } from '../../../core/services/config.service';
import { formatBytes } from '../../pipes/bytes.pipe';
import { Icon, IconName } from '../icon/icon';

interface Limitation {
  icon: IconName;
  title: string;
  text: string;
}

function humanDuration(seconds: number): string {
  if (seconds >= 3600 && seconds % 3600 === 0) {
    const hours = seconds / 3600;
    return `${hours} hora${hours > 1 ? 's' : ''}`;
  }
  return `${Math.round(seconds / 60)} minutos`;
}

@Component({
  selector: 'app-limitations-panel',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section id="limitacoes" class="card limitations" aria-labelledby="limitations-title">
      <h2 id="limitations-title" class="limitations__title">
        <app-icon name="info" [size]="20" /> Limitações do JLoads
      </h2>
      <ul class="limitations__list">
        @for (item of items(); track item.title) {
          <li class="limitations__item">
            <app-icon [name]="item.icon" [size]="18" />
            <p><strong>{{ item.title }}.</strong> {{ item.text }}</p>
          </li>
        }
      </ul>
    </section>
  `,
})
export class LimitationsPanel {
  private readonly config = inject(ConfigService).config;

  protected readonly items = computed<Limitation[]>(() => {
    const c = this.config();
    return [
      {
        icon: 'link',
        title: 'Somente YouTube',
        text: 'Vídeos individuais, Shorts e YouTube Music. Playlists e canais inteiros não são suportados.',
      },
      {
        icon: 'list',
        title: 'Quantidade',
        text: `Até ${c.maxBatchSize} links por envio e ${c.maxActiveJobsPerClient} downloads ativos por vez. O servidor processa ${c.maxConcurrentDownloads} simultaneamente; os demais aguardam na fila.`,
      },
      {
        icon: 'download',
        title: 'Tamanho e duração',
        text: `Arquivos de até ${formatBytes(c.maxFileSizeBytes)} e conteúdos de até ${humanDuration(c.maxMediaDurationSeconds)}.`,
      },
      {
        icon: 'clock',
        title: 'Arquivos temporários',
        text: `Cada arquivo fica no servidor por ${c.fileRetentionMinutes} minutos após concluir. Salve antes disso. O histórico é apagado quando o servidor reinicia.`,
      },
      {
        icon: 'alert',
        title: 'Conteúdo restrito',
        text: 'Sem login ou cookies: vídeos privados, com restrição de idade, exclusivos para membros, com DRM e transmissões ao vivo não podem ser baixados.',
      },
      {
        icon: 'retry',
        title: 'Bloqueios temporários',
        text: 'O YouTube pode pedir verificação anti-robô e recusar downloads por um tempo. Nesse caso, tente novamente mais tarde.',
      },
      {
        icon: 'folder',
        title: 'Escolha de pasta',
        text: 'Funciona no Chrome, Edge e Opera para computador. Nos outros navegadores e no celular, os arquivos vão para a pasta de downloads padrão.',
      },
      {
        icon: 'music',
        title: 'Qualidade',
        text: 'Limitada à qualidade original do vídeo. O áudio é convertido para MP3, e o vídeo é entregue em MP4.',
      },
      {
        icon: 'check',
        title: 'Uso responsável',
        text: 'Baixe apenas conteúdo que você tem direito de baixar. Respeite os direitos autorais e os termos da plataforma.',
      },
    ];
  });
}

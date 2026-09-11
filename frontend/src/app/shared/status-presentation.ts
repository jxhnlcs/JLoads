import { AnalyzerState, DownloadStatus } from '../core/models/download.models';
import { IconName } from './components/icon/icon';

export type Tone = 'neutral' | 'info' | 'progress' | 'success' | 'danger' | 'muted';

export interface StatusPresentation {
  label: string;
  hint: string;
  icon: IconName;
  tone: Tone;
}

/** Texto amigável, indicador visual e tom de cada estado da interface. */
export const STATUS_PRESENTATION: Record<DownloadStatus | AnalyzerState, StatusPresentation> = {
  IDLE: { label: 'Pronto', hint: 'Cole um link para começar.', icon: 'link', tone: 'neutral' },
  ANALYZING: { label: 'Analisando…', hint: 'Consultando as informações do conteúdo.', icon: 'spinner', tone: 'info' },
  READY: { label: 'Pronto para baixar', hint: 'Escolha o formato e a qualidade.', icon: 'check', tone: 'info' },
  BATCH: { label: 'Vários links', hint: 'Escolha um formato para todos.', icon: 'list', tone: 'info' },
  QUEUED: { label: 'Na fila', hint: 'Aguardando um espaço livre para iniciar.', icon: 'clock', tone: 'neutral' },
  DOWNLOADING: { label: 'Baixando…', hint: 'Transferindo o conteúdo.', icon: 'download', tone: 'progress' },
  PROCESSING: { label: 'Processando…', hint: 'Convertendo e finalizando o arquivo.', icon: 'cog', tone: 'progress' },
  COMPLETED: { label: 'Download concluído', hint: 'Seu arquivo está pronto.', icon: 'check', tone: 'success' },
  FAILED: { label: 'Falhou', hint: 'Não foi possível concluir o download.', icon: 'alert', tone: 'danger' },
  CANCELLED: { label: 'Cancelado', hint: 'O download foi interrompido.', icon: 'x', tone: 'muted' },
};

import { FormatOption } from './download.models';

/**
 * Presets usados no envio em lote, quando os formatos de cada vídeo ainda não são conhecidos. Para vídeo, a
 * qualidade é um limite superior: se a origem tiver resolução menor, o backend baixa o melhor disponível.
 */
export const FORMAT_PRESETS: FormatOption[] = [
  { type: 'VIDEO', quality: 'BEST', label: 'Melhor disponível', description: 'MP4 · maior resolução' },
  { type: 'VIDEO', quality: 'HIGH', label: 'Até 1080p', description: 'MP4 · Full HD' },
  { type: 'VIDEO', quality: 'MEDIUM', label: 'Até 720p', description: 'MP4 · HD' },
  { type: 'VIDEO', quality: 'LOW', label: 'Até 480p', description: 'MP4 · SD' },
  { type: 'AUDIO', quality: 'BEST', label: 'Máxima qualidade', description: 'MP3 · VBR' },
  { type: 'AUDIO', quality: 'HIGH', label: 'Alta', description: 'MP3 · 256 kbps' },
  { type: 'AUDIO', quality: 'MEDIUM', label: 'Média', description: 'MP3 · 192 kbps' },
  { type: 'AUDIO', quality: 'LOW', label: 'Econômica', description: 'MP3 · 128 kbps' },
];

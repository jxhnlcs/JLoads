import { Pipe, PipeTransform } from '@angular/core';

const UNITS = ['B', 'KB', 'MB', 'GB', 'TB'];

export function formatBytes(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value) || value <= 0) {
    return '—';
  }
  let scaled = value;
  let unit = 0;
  while (scaled >= 1024 && unit < UNITS.length - 1) {
    scaled /= 1024;
    unit++;
  }
  const digits = unit === 0 || scaled >= 100 ? 0 : 1;
  return `${scaled.toFixed(digits)} ${UNITS[unit]}`;
}

@Pipe({ name: 'bytes' })
export class BytesPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    return formatBytes(value);
  }
}

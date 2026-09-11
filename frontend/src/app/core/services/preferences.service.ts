import { Injectable, effect, signal } from '@angular/core';
import { DownloadType } from '../models/download.models';

const STORAGE_KEY = 'jloads.preferences';

interface Preferences {
  defaultType: DownloadType;
}

const DEFAULTS: Preferences = { defaultType: 'VIDEO' };

/** Preferências locais do usuário (somente neste navegador). */
@Injectable({ providedIn: 'root' })
export class PreferencesService {
  readonly defaultType = signal<DownloadType>(this.load().defaultType);

  constructor() {
    effect(() => {
      const value: Preferences = { defaultType: this.defaultType() };
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(value));
      } catch {
        // armazenamento indisponível
      }
    });
  }

  private load(): Preferences {
    try {
      const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}') as Partial<Preferences>;
      return {
        defaultType: parsed.defaultType === 'AUDIO' || parsed.defaultType === 'VIDEO' ? parsed.defaultType : DEFAULTS.defaultType,
      };
    } catch {
      return DEFAULTS;
    }
  }
}

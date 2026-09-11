import { Injectable } from '@angular/core';

const STORAGE_KEY = 'jloads.clientId';
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Identificador anônimo deste navegador. Serve apenas para separar a lista de downloads de cada visitante
 * enquanto a aplicação não possui login.
 */
@Injectable({ providedIn: 'root' })
export class ClientIdService {
  readonly clientId: string = this.loadOrCreate();

  private loadOrCreate(): string {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored && UUID_PATTERN.test(stored)) {
        return stored;
      }
      const created = crypto.randomUUID();
      localStorage.setItem(STORAGE_KEY, created);
      return created;
    } catch {
      return crypto.randomUUID();
    }
  }
}

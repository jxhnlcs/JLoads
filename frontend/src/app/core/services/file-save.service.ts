import { DestroyRef, Injectable, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DownloadJob } from '../models/download.models';
import { DownloadService } from './download.service';
import { idbDelete, idbGet, idbSet } from './handle-store';

export type SaveState = 'saving' | 'saved' | 'error' | 'permission';

export interface SaveStatus {
  state: SaveState;
  /** 'folder' = pasta escolhida; 'browser' = pasta de downloads padrão do navegador. */
  target?: 'folder' | 'browser';
  filename?: string;
}

type PermissionHandle = FileSystemDirectoryHandle & {
  queryPermission?(descriptor: { mode: 'readwrite' }): Promise<PermissionState>;
  requestPermission?(descriptor: { mode: 'readwrite' }): Promise<PermissionState>;
};

type DirectoryPickerWindow = Window & {
  showDirectoryPicker?(options?: { id?: string; mode?: 'read' | 'readwrite'; startIn?: string }): Promise<FileSystemDirectoryHandle>;
};

const HANDLE_KEY = 'saveDirectory';
const AUTO_SAVE_KEY = 'jloads.autoSave';
const SAVED_KEY = 'jloads.savedJobs';
const MAX_SAVED_ENTRIES = 300;

/**
 * Salva os arquivos concluídos numa pasta escolhida pelo usuário (File System Access API — Chrome, Edge e Opera
 * desktop). A escrita acontece no navegador: o servidor nunca recebe caminhos do computador do usuário.
 * Em navegadores sem suporte, o arquivo é enviado para a pasta de downloads padrão.
 */
@Injectable({ providedIn: 'root' })
export class FileSaveService {
  private readonly downloads = inject(DownloadService);
  private readonly directory = signal<PermissionHandle | null>(null);
  private readonly permission = signal<PermissionState | 'unknown'>('unknown');
  private readonly statuses = signal<ReadonlyMap<string, SaveStatus>>(this.loadSaved());

  readonly supported = typeof window !== 'undefined' && typeof (window as DirectoryPickerWindow).showDirectoryPicker === 'function';
  readonly directoryName = computed(() => this.directory()?.name ?? null);
  readonly needsPermission = computed(() => this.directory() !== null && this.permission() !== 'granted');
  readonly autoSave = signal<boolean>(this.loadAutoSave());

  constructor() {
    void this.restoreDirectory();
    effect(() => {
      const value = this.autoSave();
      try {
        localStorage.setItem(AUTO_SAVE_KEY, JSON.stringify(value));
      } catch {
        // armazenamento indisponível
      }
    });
    this.downloads.completed$
      .pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe((job) => {
        if (this.autoSave()) {
          void this.autoSaveJob(job);
        }
      });
  }

  statusOf(jobId: string): SaveStatus | null {
    return this.statuses().get(jobId) ?? null;
  }

  async chooseDirectory(): Promise<boolean> {
    const picker = (window as DirectoryPickerWindow).showDirectoryPicker;
    if (!picker) {
      return false;
    }
    try {
      const handle = (await picker.call(window, { id: 'jloads', mode: 'readwrite', startIn: 'downloads' })) as PermissionHandle;
      this.directory.set(handle);
      this.permission.set('granted');
      this.autoSave.set(true);
      await idbSet(HANDLE_KEY, handle).catch(() => undefined);
      return true;
    } catch {
      return false; // usuário cancelou a escolha
    }
  }

  async forgetDirectory(): Promise<void> {
    this.directory.set(null);
    this.permission.set('unknown');
    await idbDelete(HANDLE_KEY).catch(() => undefined);
  }

  /** Pede novamente a permissão da pasta (precisa de um clique) e retoma salvamentos pendentes. */
  async authorize(): Promise<void> {
    if (await this.ensurePermission(true)) {
      const pending = this.downloads.jobs().filter((job) => this.statusOf(job.id)?.state === 'permission');
      for (const job of pending) {
        await this.saveToFolder(job, false);
      }
    }
  }

  /** Salva um job concluído. {@code interactive} indica que veio de um clique (permite pedir permissão/pasta). */
  async saveToFolder(job: DownloadJob, interactive: boolean): Promise<void> {
    if (!job.fileAvailable) {
      return;
    }
    if (!this.supported) {
      this.sendToBrowserDownloads(job);
      return;
    }
    if (!this.directory() && !(interactive && (await this.chooseDirectory()))) {
      return;
    }
    if (!(await this.ensurePermission(interactive))) {
      this.setStatus(job.id, { state: 'permission' });
      return;
    }
    await this.writeToDirectory(job);
  }

  private async autoSaveJob(job: DownloadJob): Promise<void> {
    if (this.statusOf(job.id)?.state === 'saved' || this.statusOf(job.id)?.state === 'saving') {
      return;
    }
    if (this.directory()) {
      await this.saveToFolder(job, false);
    } else {
      this.sendToBrowserDownloads(job);
    }
  }

  private async writeToDirectory(job: DownloadJob): Promise<void> {
    const directory = this.directory();
    if (!directory) {
      return;
    }
    this.setStatus(job.id, { state: 'saving', target: 'folder' });
    let writable: FileSystemWritableFileStream | undefined;
    try {
      const response = await fetch(this.downloads.fileUrl(job.id));
      if (!response.ok || !response.body) {
        throw new Error(`HTTP ${response.status}`);
      }
      const filename = await uniqueFilename(directory, safeFilename(job.filename));
      const fileHandle = await directory.getFileHandle(filename, { create: true });
      writable = await fileHandle.createWritable();
      await response.body.pipeTo(writable);
      this.setStatus(job.id, { state: 'saved', target: 'folder', filename }, true);
    } catch {
      await writable?.abort().catch(() => undefined);
      this.setStatus(job.id, { state: 'error', target: 'folder' });
    }
  }

  private sendToBrowserDownloads(job: DownloadJob): void {
    const anchor = document.createElement('a');
    anchor.href = this.downloads.fileUrl(job.id);
    anchor.download = job.filename ?? '';
    anchor.rel = 'noopener';
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    this.setStatus(job.id, { state: 'saved', target: 'browser', filename: job.filename }, true);
  }

  private async ensurePermission(interactive: boolean): Promise<boolean> {
    const directory = this.directory();
    if (!directory) {
      return false;
    }
    try {
      let state = (await directory.queryPermission?.({ mode: 'readwrite' })) ?? 'granted';
      if (state !== 'granted' && interactive) {
        state = (await directory.requestPermission?.({ mode: 'readwrite' })) ?? 'denied';
      }
      this.permission.set(state);
      return state === 'granted';
    } catch {
      this.permission.set('denied');
      return false;
    }
  }

  private async restoreDirectory(): Promise<void> {
    if (!this.supported || typeof indexedDB === 'undefined') {
      return;
    }
    try {
      const handle = await idbGet<PermissionHandle>(HANDLE_KEY);
      if (handle) {
        this.directory.set(handle);
        await this.ensurePermission(false);
      }
    } catch {
      // IndexedDB indisponível: o usuário escolhe a pasta de novo
    }
  }

  private setStatus(jobId: string, status: SaveStatus, persist = false): void {
    this.statuses.update((current) => new Map(current).set(jobId, status));
    if (persist) {
      this.persistSaved();
    }
  }

  private loadAutoSave(): boolean {
    try {
      return JSON.parse(localStorage.getItem(AUTO_SAVE_KEY) ?? 'false') === true;
    } catch {
      return false;
    }
  }

  private loadSaved(): ReadonlyMap<string, SaveStatus> {
    try {
      const parsed: unknown = JSON.parse(localStorage.getItem(SAVED_KEY) ?? '[]');
      const entries = Array.isArray(parsed) ? parsed : [];
      return new Map(
        entries
          .filter((e): e is [string, SaveStatus] => Array.isArray(e) && typeof e[0] === 'string' && e[1]?.state === 'saved')
          .map(([id, status]) => [id, status]),
      );
    } catch {
      return new Map();
    }
  }

  private persistSaved(): void {
    try {
      const saved = [...this.statuses().entries()].filter(([, status]) => status.state === 'saved').slice(-MAX_SAVED_ENTRIES);
      localStorage.setItem(SAVED_KEY, JSON.stringify(saved));
    } catch {
      // armazenamento indisponível
    }
  }
}

/** Nome já vem sanitizado do servidor; esta é uma segunda barreira no cliente. */
function safeFilename(name: string | undefined): string {
  const cleaned = (name ?? '').replace(/[\\/:*?"<>| -]/g, ' ').replace(/\s+/g, ' ').trim().replace(/^\.+/, '');
  return cleaned || 'download';
}

/** Evita sobrescrever arquivos existentes: "nome.mp3" → "nome (1).mp3". */
async function uniqueFilename(directory: FileSystemDirectoryHandle, filename: string): Promise<string> {
  const dot = filename.lastIndexOf('.');
  const base = dot > 0 ? filename.slice(0, dot) : filename;
  const extension = dot > 0 ? filename.slice(dot) : '';
  for (let attempt = 0; attempt < 100; attempt++) {
    const candidate = attempt === 0 ? filename : `${base} (${attempt})${extension}`;
    try {
      await directory.getFileHandle(candidate);
    } catch {
      return candidate; // NotFoundError: nome livre
    }
  }
  return `${base} (${Date.now()})${extension}`;
}

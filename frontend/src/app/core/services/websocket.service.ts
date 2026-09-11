import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { Observable, Subject, Subscription, filter, interval, repeat, retry, share, timer } from 'rxjs';
import { WebSocketSubject, webSocket } from 'rxjs/webSocket';
import { DownloadEvent } from '../models/download.models';
import { ClientIdService } from './client-id.service';

export type ConnectionState = 'connecting' | 'open' | 'reconnecting';

const PING_INTERVAL_MS = 25_000;
const MAX_BACKOFF_MS = 30_000;

type SocketMessage = DownloadEvent | 'pong' | 'ping';

/**
 * Conexão WebSocket com reconexão automática (backoff exponencial) e keep-alive. O servidor envia apenas
 * eventos dos jobs deste navegador.
 */
@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private readonly clientId = inject(ClientIdService).clientId;
  private readonly opened = new Subject<void>();
  private socket?: WebSocketSubject<SocketMessage>;
  private pingSubscription?: Subscription;

  readonly state = signal<ConnectionState>('connecting');

  /** Emite sempre que a conexão é (re)estabelecida — útil para ressincronizar o estado via REST. */
  readonly connected$ = this.opened.asObservable();

  readonly events$: Observable<DownloadEvent> = new Observable<SocketMessage>((subscriber) => {
    this.socket = webSocket<SocketMessage>({
      url: this.url(),
      deserializer: (message) => (message.data === 'pong' ? 'pong' : JSON.parse(message.data as string)),
      serializer: (value) => (typeof value === 'string' ? value : JSON.stringify(value)),
      openObserver: {
        next: () => {
          this.state.set('open');
          this.startPing();
          this.opened.next();
        },
      },
      closeObserver: {
        next: () => {
          this.state.set('reconnecting');
          this.stopPing();
        },
      },
    });
    const inner = this.socket.subscribe(subscriber);
    return () => {
      this.stopPing();
      inner.unsubscribe();
    };
  }).pipe(
    retry({
      delay: (_error, attempt) => {
        this.state.set('reconnecting');
        return timer(Math.min(1000 * 2 ** (attempt - 1), MAX_BACKOFF_MS));
      },
      resetOnSuccess: true,
    }),
    // O servidor pode encerrar a conexão de forma limpa (ex.: reinício); nesse caso o stream completa.
    repeat({
      delay: () => {
        this.state.set('reconnecting');
        return timer(2_000);
      },
    }),
    filter((message): message is DownloadEvent => message !== 'pong' && typeof message === 'object'),
    share(),
  );

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopPing());
  }

  private url(): string {
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
    return `${protocol}://${location.host}/ws?clientId=${encodeURIComponent(this.clientId)}`;
  }

  private startPing(): void {
    this.stopPing();
    this.pingSubscription = interval(PING_INTERVAL_MS).subscribe(() => this.socket?.next('ping'));
  }

  private stopPing(): void {
    this.pingSubscription?.unsubscribe();
    this.pingSubscription = undefined;
  }
}

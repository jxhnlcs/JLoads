import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { App } from './app';
import { ConfigService, DEFAULT_CONFIG } from './core/services/config.service';
import { FileSaveService } from './core/services/file-save.service';
import { WebSocketService } from './core/services/websocket.service';
import { isYoutubeUrl, parseLinks } from './features/downloader/url-input/url-input';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: WebSocketService, useValue: { events$: of(), connected$: of(), state: () => 'open' } },
        { provide: ConfigService, useValue: { config: signal(DEFAULT_CONFIG) } },
        { provide: FileSaveService, useValue: {} },
      ],
    }).compileComponents();
  });

  it('renders the JLoads shell with navigation and legal notice', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('.brand__name')?.textContent).toContain('JLoads');
    expect(element.querySelector('a[aria-label="Preferências"]')).toBeTruthy();
    expect(element.querySelector('a[aria-label="Limitações"]')).toBeNull();
    expect(element.querySelector('.footer')?.textContent).toContain('direito de baixar');
    TestBed.inject(HttpTestingController).verify();
  });
});

describe('parseLinks', () => {
  it('splits by line, space, comma and removes duplicates', () => {
    const links = parseLinks('https://youtu.be/aaaaaaaaaaa\n https://youtu.be/bbbbbbbbbbb, https://youtu.be/aaaaaaaaaaa;\n\n');
    expect(links).toEqual(['https://youtu.be/aaaaaaaaaaa', 'https://youtu.be/bbbbbbbbbbb']);
  });

  it('recognizes only YouTube hosts', () => {
    expect(isYoutubeUrl('https://www.youtube.com/watch?v=aaaaaaaaaaa')).toBe(true);
    expect(isYoutubeUrl('youtu.be/aaaaaaaaaaa')).toBe(true);
    expect(isYoutubeUrl('https://youtube.com.evil.com/watch?v=x')).toBe(false);
    expect(isYoutubeUrl('https://vimeo.com/123')).toBe(false);
  });
});

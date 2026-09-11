import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'JLoads',
    loadComponent: () => import('./features/downloader/downloader-page/downloader-page').then((m) => m.DownloaderPage),
  },
  {
    path: 'settings',
    title: 'Preferências · JLoads',
    loadComponent: () => import('./features/settings/settings-page').then((m) => m.SettingsPage),
  },
  { path: '**', redirectTo: '' },
];

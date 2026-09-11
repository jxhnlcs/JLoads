import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { ClientIdService } from './client-id.service';

export const clientIdInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/')) {
    return next(req);
  }
  const clientId = inject(ClientIdService).clientId;
  return next(req.clone({ setHeaders: { 'X-Client-Id': clientId } }));
};

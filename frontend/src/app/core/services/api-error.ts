import { HttpErrorResponse } from '@angular/common/http';
import { ApiErrorBody } from '../models/download.models';

export interface FriendlyError {
  code: string;
  message: string;
}

const NETWORK_ERROR: FriendlyError = {
  code: 'NETWORK',
  message: 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.',
};

const GENERIC_ERROR: FriendlyError = {
  code: 'UNKNOWN',
  message: 'Ocorreu um erro inesperado. Tente novamente.',
};

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as ApiErrorBody).code === 'string' &&
    typeof (value as ApiErrorBody).message === 'string'
  );
}

/** Converte qualquer erro HTTP em uma mensagem amigável — nunca exibe detalhes técnicos. */
export function toFriendlyError(error: unknown): FriendlyError {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return NETWORK_ERROR;
    }
    if (isApiErrorBody(error.error)) {
      return { code: error.error.code, message: error.error.message };
    }
    if (error.status === 429) {
      return { code: 'RATE_LIMITED', message: 'Muitas requisições. Aguarde um momento e tente novamente.' };
    }
    if (error.status >= 500) {
      return { code: 'SERVER', message: 'O servidor não conseguiu processar a solicitação. Tente novamente.' };
    }
  }
  return GENERIC_ERROR;
}

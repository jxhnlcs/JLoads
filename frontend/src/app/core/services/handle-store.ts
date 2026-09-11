/** Armazenamento mínimo em IndexedDB — necessário porque handles de pasta não cabem no localStorage. */
const DB_NAME = 'jloads';
const STORE = 'handles';

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 1);
    request.onupgradeneeded = () => request.result.createObjectStore(STORE);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function withStore<T>(mode: IDBTransactionMode, action: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const db = await openDatabase();
  try {
    return await new Promise<T>((resolve, reject) => {
      const request = action(db.transaction(STORE, mode).objectStore(STORE));
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  } finally {
    db.close();
  }
}

export function idbGet<T>(key: string): Promise<T | undefined> {
  return withStore<T | undefined>('readonly', (store) => store.get(key));
}

export function idbSet(key: string, value: unknown): Promise<IDBValidKey> {
  return withStore('readwrite', (store) => store.put(value, key));
}

export function idbDelete(key: string): Promise<undefined> {
  return withStore('readwrite', (store) => store.delete(key));
}

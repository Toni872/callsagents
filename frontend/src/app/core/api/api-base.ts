import { environment } from '../../../environments/environment';

export const apiUrl = (path: string): string => `${environment.apiBaseUrl}${path}`;

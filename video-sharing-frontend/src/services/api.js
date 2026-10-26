import axios from 'axios';
import { clearAuth, getAuthToken, saveAuth } from './auth';
import { API_BASE_URL } from '../config/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
});

let refreshPromise = null;

export const refreshAccessToken = async () => {
  if (!refreshPromise) {
    refreshPromise = axios.post(`${API_BASE_URL}/api/auth/refresh`, {}, {
      withCredentials: true,
      headers: { 'X-CSRF-Guard': '1' },
    }).then(({ data }) => {
      saveAuth(data);
      return data.token;
    }).finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
};

api.interceptors.request.use((config) => {
  const token = getAuthToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  if (/\/api\/auth\/(refresh|logout)(?:\?|$)/.test(config.url || '')) {
    config.headers['X-CSRF-Guard'] = '1';
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const config = error.config;
      const url = config?.url || '';
      const isAuthEndpoint = /\/api\/auth\/(login|register|refresh|logout)(?:\?|$)/.test(url);
      if (!isAuthEndpoint && config?._refreshRetried) {
        clearAuth();
        if (window.location.pathname !== '/login') {
          const redirect = `${window.location.pathname}${window.location.search}`;
          window.location.assign(`/login?redirect=${encodeURIComponent(redirect)}`);
        }
      } else if (!isAuthEndpoint && config) {
        config._refreshRetried = true;
        return refreshAccessToken().then((token) => {
          config.headers = config.headers || {};
          config.headers.Authorization = `Bearer ${token}`;
          return api.request(config);
        }, (refreshError) => {
          clearAuth();
          if (window.location.pathname !== '/login') {
            const redirect = `${window.location.pathname}${window.location.search}`;
            window.location.assign(`/login?redirect=${encodeURIComponent(redirect)}`);
          }
          return Promise.reject(refreshError);
        });
      }
    }
    return Promise.reject(error);
  },
);

export default api;

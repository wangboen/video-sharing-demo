import axios from 'axios';
import { clearAuth, getAuthToken } from './auth';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
});

api.interceptors.request.use((config) => {
  const token = getAuthToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      clearAuth();
      if (window.location.pathname !== '/login') {
        const redirect = `${window.location.pathname}${window.location.search}`;
        window.location.assign(`/login?redirect=${encodeURIComponent(redirect)}`);
      }
    }
    return Promise.reject(error);
  },
);

export default api;

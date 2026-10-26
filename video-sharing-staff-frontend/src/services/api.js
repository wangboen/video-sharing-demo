import axios from 'axios'
import { apiBaseUrl } from '../config/apiBase'
import {
  getStaffAccessToken,
  clearStaffSession,
  refreshStaffSession,
} from './staffAuth'

const api = axios.create({
  baseURL: apiBaseUrl,
  withCredentials: true,
})

let refreshPromise = null

export function handleStaffUnauthorized(
  status,
  redirect = (path) => window.location.assign(path),
  currentPath = window.location.pathname,
) {
  if (status !== 401) return false
  clearStaffSession()
  if (currentPath !== '/staff/login') {
    redirect('/staff/login')
  }
  return true
}

api.interceptors.request.use((config) => {
  const token = getStaffAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  if (/\/api\/staff\/auth\/(refresh|logout)(?:\?|$)/.test(config.url || '')) {
    config.headers['X-CSRF-Guard'] = '1'
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const config = error.config
      const url = config?.url || ''
      const isAuthEndpoint = /\/api\/staff\/auth\/(login|refresh|logout)(?:\?|$)/.test(url)
      if (!isAuthEndpoint && config?._refreshRetried) {
        handleStaffUnauthorized(401)
      } else if (!isAuthEndpoint && config) {
        config._refreshRetried = true
        if (!refreshPromise) {
          refreshPromise = refreshStaffSession().finally(() => {
            refreshPromise = null
          })
        }
        return refreshPromise.then((data) => {
          config.headers = config.headers || {}
          config.headers.Authorization = `Bearer ${data.token}`
          return api.request(config)
        }, (refreshError) => {
          handleStaffUnauthorized(401)
          return Promise.reject(refreshError)
        })
      }
    }
    return Promise.reject(error)
  },
)

export default api

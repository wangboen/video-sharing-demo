import axios from 'axios'
import { apiBaseUrl } from '../config/apiBase'
import {
  STAFF_TOKEN_KEY,
  clearStaffSession,
} from './staffAuth'

const api = axios.create({
  baseURL: apiBaseUrl,
})

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
  const token = localStorage.getItem(STAFF_TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    handleStaffUnauthorized(error.response?.status)
    return Promise.reject(error)
  },
)

export default api

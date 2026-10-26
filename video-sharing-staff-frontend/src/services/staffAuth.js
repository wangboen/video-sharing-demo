import axios from 'axios'
import { apiBaseUrl } from '../config/apiBase'

export const STAFF_TOKEN_KEY = 'staffAuthToken'
export const STAFF_USER_KEY = 'staffUser'

const STAFF_ROLES = new Set(['REVIEWER', 'ADMIN'])
let accessToken = null

export const getStaffAccessToken = () => accessToken

export function isStaffRole(role) {
  return STAFF_ROLES.has(role)
}

export function clearStaffSession() {
  accessToken = null
  localStorage.removeItem(STAFF_TOKEN_KEY)
  localStorage.removeItem(STAFF_USER_KEY)
}

export function saveStaffSession(payload) {
  if (!payload?.token || !isStaffRole(payload.role)) {
    clearStaffSession()
    throw new Error('Insufficient staff role')
  }

  const user = {
    username: payload.username,
    id: payload.id,
    email: payload.email,
    role: payload.role,
  }
  accessToken = payload.token
  localStorage.removeItem(STAFF_TOKEN_KEY)
  localStorage.setItem(STAFF_USER_KEY, JSON.stringify(user))
}

export function readStaffSession() {
  const serializedUser = localStorage.getItem(STAFF_USER_KEY)
  if (!serializedUser) {
    localStorage.removeItem(STAFF_TOKEN_KEY)
    clearStaffSession()
    return null
  }

  try {
    const user = JSON.parse(serializedUser)
    if (!isStaffRole(user?.role)) {
      clearStaffSession()
      return null
    }
    localStorage.removeItem(STAFF_TOKEN_KEY)
    return { token: accessToken, user }
  } catch {
    clearStaffSession()
    return null
  }
}

export async function refreshStaffSession() {
  const { data } = await axios.post(`${apiBaseUrl}/api/staff/auth/refresh`, {}, {
    withCredentials: true,
    headers: { 'X-CSRF-Guard': '1' },
  })
  saveStaffSession(data)
  return data
}

// Remove access tokens persisted by versions before refresh-cookie sessions.
localStorage.removeItem(STAFF_TOKEN_KEY)

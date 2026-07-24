export const STAFF_TOKEN_KEY = 'staffAuthToken'
export const STAFF_USER_KEY = 'staffUser'

const STAFF_ROLES = new Set(['REVIEWER', 'ADMIN'])

export function isStaffRole(role) {
  return STAFF_ROLES.has(role)
}

export function clearStaffSession() {
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
  localStorage.setItem(STAFF_TOKEN_KEY, payload.token)
  localStorage.setItem(STAFF_USER_KEY, JSON.stringify(user))
}

export function readStaffSession() {
  const token = localStorage.getItem(STAFF_TOKEN_KEY)
  const serializedUser = localStorage.getItem(STAFF_USER_KEY)
  if (!token || !serializedUser) {
    clearStaffSession()
    return null
  }

  try {
    const user = JSON.parse(serializedUser)
    if (!isStaffRole(user?.role)) {
      clearStaffSession()
      return null
    }
    return { token, user }
  } catch {
    clearStaffSession()
    return null
  }
}

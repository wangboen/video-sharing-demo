export function resolveApiBaseUrl(configuredValue, isDevelopment) {
  if (configuredValue !== undefined) {
    return configuredValue.trim().replace(/\/+$/, '')
  }
  return isDevelopment ? 'http://192.168.50.167:8080' : ''
}

export const API_BASE_URL = resolveApiBaseUrl(
  import.meta.env.VITE_API_BASE_URL,
  import.meta.env.DEV,
)

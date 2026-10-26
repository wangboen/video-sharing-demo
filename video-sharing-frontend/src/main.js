import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import api, { refreshAccessToken } from './services/api'
import { clearAuth, getStoredUser, updateStoredUser } from './services/auth'

import './assets/main.css' // We'll create this later

async function startApp() {
  if (getStoredUser()) {
    try {
      await refreshAccessToken()
      const { data } = await api.get('/api/auth/me')
      updateStoredUser(data)
    } catch {
      clearAuth()
    }
  }

  const app = createApp(App)
  app.use(router)
  app.mount('#app')
}

startApp()

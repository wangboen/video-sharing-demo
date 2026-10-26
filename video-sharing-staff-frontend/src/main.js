import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './assets/main.css'
import { clearStaffSession, readStaffSession, refreshStaffSession } from './services/staffAuth'

async function startApp() {
  if (readStaffSession()) {
    try {
      const data = await refreshStaffSession()
      if (!['REVIEWER', 'ADMIN'].includes(data?.role)) clearStaffSession()
    } catch {
      clearStaffSession()
    }
  }

  createApp(App).use(router).mount('#app')
}

startApp()

import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import RegisterView from '../views/RegisterView.vue'
import LoginView from '../views/LoginView.vue'
import UserDashboard from '../views/UserDashboard.vue'
import VideoUpload from '../views/VideoUpload.vue'
import VideoList from '../views/VideoList.vue'
import VideoPlayer from '../views/VideoPlayer.vue' // Import VideoPlayer component
import { getAuthToken, getStoredUser } from '../services/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
      meta: { title: '发现' }
    },
    {
      path: '/about',
      name: 'about',
      component: () => import('../views/AboutView.vue'),
      meta: { title: '关于' }
    },
    {
      path: '/register',
      name: 'register',
      component: RegisterView,
      meta: { title: '注册' }
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { title: '登录' }
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: UserDashboard,
      meta: { requiresAuth: true, title: '个人中心' }
    },
    {
      path: '/upload',
      name: 'upload',
      component: VideoUpload,
      meta: { requiresAuth: true, title: '上传视频' }
    },
    {
      path: '/videos',
      name: 'videos',
      component: VideoList,
      meta: { title: '视频库' }
    },
    {
      path: '/video/:id',
      name: 'video-player',
      component: VideoPlayer, // Add video player route
      meta: { title: '播放视频' }
    }
  ]
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && (!getAuthToken() || !getStoredUser())) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
})

router.afterEach((to) => {
  document.title = `${to.meta.title || '发现'} · 视界`
})

export default router

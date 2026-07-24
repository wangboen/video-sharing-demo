import { createRouter, createWebHistory } from 'vue-router'
import { isStaffRole, readStaffSession } from '../services/staffAuth'
import StaffLoginView from '../views/StaffLoginView.vue'
import ModerationView from '../views/ModerationView.vue'
import ReviewerManagementView from '../views/ReviewerManagementView.vue'

export function resolveStaffAccess(to, session) {
  if (to.meta.publicOnly) {
    return session?.user && isStaffRole(session.user.role)
      ? { name: 'moderation' }
      : null
  }

  if (!session?.user || !isStaffRole(session.user.role)) {
    return { name: 'login' }
  }
  if (to.meta.adminOnly && session.user.role !== 'ADMIN') {
    return { name: 'moderation' }
  }
  return null
}

export function createStaffRouter(history = createWebHistory(import.meta.env.BASE_URL)) {
  const router = createRouter({
    history,
    routes: [
      {
        path: '/login',
        name: 'login',
        component: StaffLoginView,
        meta: { publicOnly: true },
      },
      {
        path: '/moderation',
        name: 'moderation',
        component: ModerationView,
      },
      {
        path: '/reviewers',
        name: 'reviewers',
        component: ReviewerManagementView,
        meta: { adminOnly: true },
      },
      {
        path: '/:pathMatch(.*)*',
        redirect: { name: 'moderation' },
      },
    ],
  })

  router.beforeEach((to) => resolveStaffAccess(to, readStaffSession()))
  return router
}

export default createStaffRouter()

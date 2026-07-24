<script setup>
import { ref, onMounted, watch } from 'vue';
import { RouterView, useRoute } from 'vue-router';
import api from './services/api';
import { clearAuth, getAuthToken, getStoredUser, updateStoredUser, USER_ROLE } from './services/auth';
import { useSidebar } from './composables/useSidebar';
import AppHeader from './components/AppHeader.vue';
import AppSidebar from './components/AppSidebar.vue';

const user = ref(null);
const route = useRoute();
const { isCollapsed, toggleSidebar } = useSidebar();

const checkUserStatus = () => {
  const storedUser = getStoredUser();
  if (storedUser?.role === USER_ROLE) {
    user.value = storedUser;
  } else {
    if (storedUser) clearAuth();
    user.value = null;
  }
};

onMounted(() => {
  checkUserStatus();
  if (getAuthToken()) {
    api.get('/api/auth/me')
      .then((response) => {
        if (response.data.role !== USER_ROLE) {
          clearAuth();
          user.value = null;
          window.location.assign('/login?error=access-denied');
          return;
        }
        updateStoredUser(response.data);
        user.value = response.data;
      })
      .catch(() => {
        clearAuth();
        user.value = null;
      });
  }
});

// Watch for route changes to update user status if needed (e.g., after login/logout)
watch(route, () => {
  checkUserStatus();
});
</script>

<template>
  <AppHeader :user="user" :sidebar-collapsed="isCollapsed" @toggle-sidebar="toggleSidebar" />
  <AppSidebar :user="user" :collapsed="isCollapsed" />
  <main class="app-main" :class="{ 'app-main--collapsed': isCollapsed }">
    <RouterView />
  </main>
</template>

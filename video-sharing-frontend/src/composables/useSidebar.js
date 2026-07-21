import { ref } from 'vue'

const isCollapsed = ref(localStorage.getItem('vision-sidebar-collapsed') === 'true')

export function useSidebar() {
  const toggleSidebar = () => {
    isCollapsed.value = !isCollapsed.value
    localStorage.setItem('vision-sidebar-collapsed', String(isCollapsed.value))
  }

  return { isCollapsed, toggleSidebar }
}

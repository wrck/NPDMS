import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  // Sidebar collapse state, persisted across reloads for convenience
  const COLLAPSE_KEY = 'pms_sidebar_collapsed'
  const sidebarCollapsed = ref<boolean>(localStorage.getItem(COLLAPSE_KEY) === 'true')

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
    localStorage.setItem(COLLAPSE_KEY, String(sidebarCollapsed.value))
  }

  return { sidebarCollapsed, toggleSidebar }
})

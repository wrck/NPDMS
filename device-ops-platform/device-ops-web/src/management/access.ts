import { ref, onMounted, onBeforeUnmount } from 'vue'
import { loadRuntimeConfig } from '@/config/runtime'
import { getUserManager } from '@/auth/oidc'

export async function loadScopes(): Promise<string[]> {
  const config = await loadRuntimeConfig()
  if (config.authMode === 'local') return ['device-ops:collections:read', 'device-ops:collections:execute', 'parser:release:read', 'parser:release:write', 'parser:task:create', 'parser:task:read', 'parser:task:cancel', 'parser:task:terminate']
  const user = await (await getUserManager()).getUser()
  return !user || user.expired ? [] : (user.scope ?? '').split(/\s+/).filter(Boolean)
}
export function useAccess() {
  const scopes = ref<string[]>([])
  let active = true
  onBeforeUnmount(() => { active = false })
  onMounted(async () => { try { const value = await loadScopes(); if (active) scopes.value = value } catch { /* Fail closed; backend remains authoritative. */ } })
  return { can: (scope: string) => scopes.value.includes(scope) }
}

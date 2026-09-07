import { computed, onMounted, ref } from 'vue'
import { getTenantList } from '@/api/system/tenant'

export interface TenantOption {
  id: number
  name: string
}

const tenants = ref<TenantOption[]>([])
let pending: Promise<TenantOption[]> | undefined

export const loadTenantOptions = (): Promise<TenantOption[]> => {
  if (!pending) {
    pending = getTenantList()
      .then((items: TenantOption[]) => {
        tenants.value = items
        return items
      })
      .catch((error: unknown) => {
        pending = undefined
        throw error
      })
  }
  return pending!
}

/** Reuse the tenant directory for every login method; never infer a tenant ID. */
export const useTenantSelection = (
  selectDefault: (tenant: TenantOption, options: TenantOption[]) => void
) => {
  const enabled = import.meta.env.VITE_APP_TENANT_ENABLE !== 'false'
  let initialized = false
  const initializeTenantSelection = async () => {
    if (!enabled || initialized) return
    const options = await loadTenantOptions()
    if (initialized) return
    const first = options[0]
    if (first) selectDefault(first, options)
    initialized = true
  }
  onMounted(initializeTenantSelection)
  return {
    showTenantSelector: computed(() => enabled && tenants.value.length > 1),
    initializeTenantSelection
  }
}

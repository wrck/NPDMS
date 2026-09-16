import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { acquireLock, renewLock, releaseLock, type EditLockInfo } from '@/api/lowcode-edit-lock'

export function useEditLock(configType: string, configId: number, userId: number, userName?: string) {
  const lockInfo = ref<EditLockInfo | null>(null)
  const locked = ref(false)
  let renewTimer: ReturnType<typeof setInterval> | null = null

  async function acquire() {
    lockInfo.value = await acquireLock(configType, configId, userId, userName)
    locked.value = lockInfo.value.acquired
    if (!locked.value) {
      ElMessage.warning(lockInfo.value.message || '获取锁失败')
    }
    return locked.value
  }

  async function renew() {
    if (!locked.value) return
    const info = await renewLock(configType, configId, userId)
    if (!info.acquired) {
      locked.value = false
      ElMessage.warning('编辑锁已失效，请重新获取')
      stopRenew()
    }
  }

  function startRenew() {
    if (renewTimer) return
    renewTimer = setInterval(renew, 5 * 60 * 1000) // 5 分钟心跳
  }

  function stopRenew() {
    if (renewTimer) {
      clearInterval(renewTimer)
      renewTimer = null
    }
  }

  async function release() {
    if (!locked.value) return
    await releaseLock(configType, configId, userId)
    locked.value = false
    stopRenew()
  }

  onMounted(async () => {
    const ok = await acquire()
    if (ok) startRenew()
  })

  onBeforeUnmount(() => {
    release()
  })

  return { lockInfo, locked, acquire, release, renew }
}

/**
 * usePWA — PWA 注册与离线检测 composable（批次4-T10）。
 *
 * <p>封装 Service Worker 注册、更新检测、离线状态监听，借鉴 Joget PWA 与
 * Zoho 离线模式的实现：</p>
 *
 * <h3>能力</h3>
 * <ol>
 *   <li><b>SW 自动注册</b>：生产环境自动注册 /sw.js，开发环境不注册</li>
 *   <li><b>更新检测</b>：监听 SW 更新，提示用户刷新页面（needRefresh）</li>
 *   <li><b>离线状态</b>：监听 online/offline 事件，isOnline 响应式状态</li>
 *   <li><b>主动更新</b>：update() 方法触发 SW 主动检查更新</li>
 *   <li><b>应用更新</b>：applyUpdate() 方法跳过 waiting 立即激活新 SW</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>
 * const { isOnline, needRefresh, applyUpdate } = usePWA()
 * watch(needRefresh, (v) => {
 *   if (v) ElMessageBox.confirm('有新版本可用，是否刷新？').then(applyUpdate)
 * })
 * </pre>
 */
import { ref, onMounted, onBeforeUnmount } from 'vue'

/** SW 注册状态 */
const swRegistration = ref<ServiceWorkerRegistration | null>(null)
/** 是否需要刷新（检测到新版本） */
const needRefresh = ref(false)
/** 是否在线 */
const isOnline = ref(navigator.onLine)
/** 是否已注册 */
const isRegistered = ref(false)

/** 在线状态变化监听 */
function onOnline() {
  isOnline.value = true
}
function onOffline() {
  isOnline.value = false
}

/**
 * 注册 Service Worker。
 *
 * <p>仅在生产环境注册（import.meta.env.PROD），开发环境不注册避免热更新冲突。</p>
 */
export async function registerSW(): Promise<void> {
  if (!('serviceWorker' in navigator)) {
    console.warn('[PWA] 当前浏览器不支持 Service Worker')
    return
  }
  if (!import.meta.env.PROD) {
    console.info('[PWA] 开发环境跳过 SW 注册')
    return
  }
  try {
    const reg = await navigator.serviceWorker.register('/sw.js', { scope: '/' })
    swRegistration.value = reg
    isRegistered.value = true
    console.info('[PWA] Service Worker 注册成功:', reg.scope)

    // 监听更新
    reg.addEventListener('updatefound', () => {
      const newWorker = reg.installing
      if (!newWorker) return
      newWorker.addEventListener('statechange', () => {
        if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
          // 新版本已安装，等待激活
          needRefresh.value = true
        }
      })
    })

    // 监听控制器变化（新 SW 已激活）
    navigator.serviceWorker.addEventListener('controllerchange', () => {
      // 控制器变化后刷新页面加载新资源
      window.location.reload()
    })
  } catch (e) {
    console.error('[PWA] Service Worker 注册失败:', e)
  }
}

/**
 * 主动检查更新。
 */
export async function update(): Promise<void> {
  if (swRegistration.value) {
    await swRegistration.value.update()
  }
}

/**
 * 应用更新（跳过 waiting 立即激活新 SW）。
 */
export async function applyUpdate(): Promise<void> {
  const reg = swRegistration.value
  if (reg && reg.waiting) {
    // 通知 waiting SW 跳过等待
    reg.waiting.postMessage('SKIP_WAITING')
  }
}

/**
 * usePWA composable 主入口。
 */
export function usePWA() {
  onMounted(() => {
    window.addEventListener('online', onOnline)
    window.addEventListener('offline', onOffline)
    // 自动注册（仅生产）
    if (!isRegistered.value) {
      registerSW()
    }
  })
  onBeforeUnmount(() => {
    window.removeEventListener('online', onOnline)
    window.removeEventListener('offline', onOffline)
  })
  return {
    isOnline,
    needRefresh,
    isRegistered,
    registerSW,
    update,
    applyUpdate
  }
}

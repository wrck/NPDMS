<!--
  ComponentSandbox 组件（批次4-T7）

  用途：以 iframe + sandbox 隔离加载第三方/市场远程组件，通过 postMessage 双向通信。
  借鉴 ToolJet iframe 沙箱与 Power Apps PCF 隔离机制，解决：
  1. 第三方组件 JS 不可信 → iframe 隔离，防止污染父页面 DOM/全局变量
  2. 远程组件 props 实时同步 → postMessage UPDATE_PROPS 响应式推送
  3. v-model 双向绑定 → iframe 上报 UPDATE_VALUE，父页面 emit update:modelValue
  4. iframe 高度自适应 → REQUEST_HEIGHT + REPORT_HEIGHT 协议
  5. CSP 白名单 + origin 校验 → 防止恶意 iframe 窃取父页面数据

  用法：
  <ComponentSandbox
    :entry-url="componentMeta.entryUrl"
    :component-name="componentMeta.name"
    :props="renderProps"
    :context="lowCodeContext"
    v-model="formValue"
    @event="onComponentEvent"
  />
-->
<template>
  <div ref="containerRef" class="component-sandbox" :style="containerStyle">
    <iframe
      v-if="shouldRender"
      ref="iframeRef"
      :src="entryUrl"
      :sandbox="sandboxAttr"
      :style="iframeStyle"
      frameborder="0"
      :allow="allowFeatures"
      @load="onIframeLoad"
    />
    <div v-else class="component-sandbox__error">
      <el-alert
        :title="errorMessage"
        type="error"
        :closable="false"
        show-icon
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import {
  HostToGuestMessage,
  GuestToHostMessage,
  createMessage,
  isSandboxMessage,
  type InitPayload,
  type UpdatePropsPayload,
  type UpdateContextPayload,
  type UpdateValuePayload,
  type EventPayload,
  type ReportHeightPayload,
  type ResizePayload,
  type ErrorPayload,
  type LogPayload,
  type SandboxMessage
} from './protocol'
import { isAllowedUrl, buildSandboxAttribute } from '@/sdk/csp-allowlist'

interface Props {
  /** 远程组件入口 URL（必填） */
  entryUrl: string
  /** 组件名（注册 key，用于 INIT 标识） */
  componentName: string
  /** 初始 props（响应式 watch 后推送 UPDATE_PROPS） */
  props?: Record<string, unknown>
  /** 运行时上下文（LowCodeContext） */
  context?: Record<string, unknown>
  /** v-model 绑定值 */
  modelValue?: unknown
  /** iframe 高度模式：fixed 固定高度 / auto 自适应 */
  heightMode?: 'fixed' | 'auto'
  /** 固定高度（px，heightMode=fixed 时生效） */
  height?: number
  /** 容器最小高度（避免 iframe 高度为 0 时塌陷） */
  minHeight?: number
  /** 是否允许同源访问（trusted 场景才开启，默认 false） */
  sameOrigin?: boolean
  /** 是否允许表单提交 */
  allowForms?: boolean
  /** 是否允许弹窗 */
  allowPopups?: boolean
  /** iframe allow 属性（权限策略） */
  allowFeatures?: string
}

const props = withDefaults(defineProps<Props>(), {
  props: () => ({}),
  context: () => ({}),
  modelValue: undefined,
  heightMode: 'auto',
  height: 0,
  minHeight: 32,
  sameOrigin: false,
  allowForms: true,
  allowPopups: false,
  allowFeatures: 'clipboard-read; clipboard-write'
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: unknown): void
  (e: 'event', payload: EventPayload): void
  (e: 'ready'): void
  (e: 'error', payload: ErrorPayload): void
  (e: 'log', payload: LogPayload): void
}>()

const containerRef = ref<HTMLDivElement>()
const iframeRef = ref<HTMLIFrameElement>()

/** iframe 是否已 READY（收到 GuestToHostMessage.READY） */
const iframeReady = ref(false)
/** iframe 实际内容高度（自适应模式用） */
const contentHeight = ref(0)
/** 渲染错误信息（URL 校验失败等） */
const errorMessage = ref('')
/** 是否应渲染 iframe（URL 校验通过后为 true） */
const shouldRender = ref(false)

/** 计算 sandbox 属性值 */
const sandboxAttr = computed(() =>
  buildSandboxAttribute(props.sameOrigin, props.allowForms, props.allowPopups)
)

/** 容器样式 */
const containerStyle = computed(() => ({
  minHeight: props.minHeight + 'px',
  height:
    props.heightMode === 'fixed' && props.height > 0
      ? props.height + 'px'
      : props.heightMode === 'auto' && contentHeight.value > 0
        ? contentHeight.value + 'px'
        : 'auto'
}))

/** iframe 样式（铺满容器） */
const iframeStyle = computed(() => ({
  width: '100%',
  height: '100%',
  border: 'none',
  display: 'block'
}))

/**
 * 校验 entryUrl 并触发渲染。
 *
 * <p>URL 不在白名单时显示错误提示，不渲染 iframe。</p>
 */
function validateAndRender() {
  if (!props.entryUrl) {
    errorMessage.value = 'ComponentSandbox: 缺少 entryUrl'
    shouldRender.value = false
    return
  }
  if (!isAllowedUrl(props.entryUrl)) {
    errorMessage.value = `ComponentSandbox: entryUrl 不在 CSP 白名单内: ${props.entryUrl}`
    shouldRender.value = false
    return
  }
  errorMessage.value = ''
  shouldRender.value = true
}

/**
 * iframe load 事件回调。
 *
 * <p>注意：load 事件触发不代表 iframe 内 JS 已初始化完成，需等待 READY 消息。
 * 但同源 iframe 可在此处主动推送 INIT（异源需等 READY 后再推送）。</p>
 */
function onIframeLoad() {
  // 异源 iframe 无法直接访问 contentWindow.postMessage 的 targetOrigin，
  // 需等待 READY 消息携带 origin 后再推送 INIT
  // 同源 iframe 可立即推送
  if (props.sameOrigin && iframeRef.value?.contentWindow) {
    sendInit()
  }
}

/**
 * 向 iframe 发送 INIT 消息（注入初始 props + context）。
 */
function sendInit() {
  const payload: InitPayload = {
    componentName: props.componentName,
    props: { ...props.props, modelValue: props.modelValue },
    context: props.context
  }
  postToGuest(HostToGuestMessage.INIT, payload)
}

/**
 * 向 iframe 发送消息（postMessage）。
 *
 * <p>targetOrigin 使用 entryUrl 的 origin（精确匹配，避免 * 通配的安全风险）。</p>
 */
function postToGuest(type: HostToGuestMessage, payload: unknown): void {
  if (!iframeRef.value?.contentWindow) return
  let targetOrigin: string
  try {
    targetOrigin = new URL(props.entryUrl).origin
  } catch {
    targetOrigin = '*'
  }
  const msg = createMessage(type, payload)
  iframeRef.value.contentWindow.postMessage(msg, targetOrigin)
}

/**
 * 接收 iframe 发来的消息（Guest → Host）。
 *
 * <p>origin 校验：仅接受 entryUrl 对应 origin 的消息，防止恶意页面仿冒。</p>
 */
function onMessage(event: MessageEvent) {
  // origin 校验
  let expectedOrigin: string
  try {
    expectedOrigin = new URL(props.entryUrl).origin
  } catch {
    return
  }
  if (event.origin !== expectedOrigin) return

  if (!isSandboxMessage(event.data)) return
  const msg = event.data as SandboxMessage

  switch (msg.type) {
    case GuestToHostMessage.READY:
      iframeReady.value = true
      // iframe 通知已就绪，推送初始数据
      sendInit()
      emit('ready')
      break

    case GuestToHostMessage.UPDATE_VALUE: {
      const payload = msg.payload as UpdateValuePayload
      emit('update:modelValue', payload.value)
      break
    }

    case GuestToHostMessage.EVENT: {
      const payload = msg.payload as EventPayload
      emit('event', payload)
      // change 事件同步触发 update:modelValue（兼容部分组件 change 即值变更的约定）
      if (payload.eventName === 'change' && payload.args?.[0] !== undefined) {
        emit('update:modelValue', payload.args[0])
      }
      break
    }

    case GuestToHostMessage.REPORT_HEIGHT: {
      const payload = msg.payload as ReportHeightPayload
      if (props.heightMode === 'auto' && payload.height > 0) {
        contentHeight.value = payload.height
      }
      break
    }

    case GuestToHostMessage.ERROR: {
      const payload = msg.payload as ErrorPayload
      console.error(`[ComponentSandbox] iframe error from "${props.componentName}":`, payload)
      emit('error', payload)
      break
    }

    case GuestToHostMessage.LOG: {
      const payload = msg.payload as LogPayload
      emit('log', payload)
      break
    }

    default:
      // 未知消息类型，忽略
      break
  }
}

/**
 * 监听容器尺寸变化，通知 iframe（响应式布局）。
 */
let resizeObserver: ResizeObserver | null = null
function setupResizeObserver() {
  if (!containerRef.value || typeof ResizeObserver === 'undefined') return
  resizeObserver = new ResizeObserver((entries) => {
    if (!iframeReady.value) return
    for (const entry of entries) {
      const { width, height } = entry.contentRect
      const payload: ResizePayload = { width: width, height: height }
      postToGuest(HostToGuestMessage.RESIZE, payload)
    }
  })
  resizeObserver.observe(containerRef.value)
}

/**
 * 主动请求 iframe 上报高度（自适应场景）。
 */
function requestHeight() {
  postToGuest(HostToGuestMessage.REQUEST_HEIGHT, {})
}

// watch props 变化，推送 UPDATE_PROPS
watch(
  () => props.props,
  (newProps) => {
    if (!iframeReady.value) return
    const payload: UpdatePropsPayload = { props: { ...newProps, modelValue: props.modelValue } }
    postToGuest(HostToGuestMessage.UPDATE_PROPS, payload)
  },
  { deep: true }
)

// watch modelValue 变化，推送 UPDATE_PROPS（含 modelValue）
watch(
  () => props.modelValue,
  (newVal) => {
    if (!iframeReady.value) return
    const payload: UpdatePropsPayload = {
      props: { ...props.props, modelValue: newVal }
    }
    postToGuest(HostToGuestMessage.UPDATE_PROPS, payload)
  }
)

// watch context 变化，推送 UPDATE_CONTEXT
watch(
  () => props.context,
  (newContext) => {
    if (!iframeReady.value) return
    const payload: UpdateContextPayload = { context: { ...newContext } }
    postToGuest(HostToGuestMessage.UPDATE_CONTEXT, payload)
  },
  { deep: true }
)

// watch entryUrl 变化，重新校验并渲染
watch(
  () => props.entryUrl,
  () => {
    iframeReady.value = false
    contentHeight.value = 0
    validateAndRender()
  }
)

onMounted(() => {
  window.addEventListener('message', onMessage)
  validateAndRender()
  nextTick(() => {
    setupResizeObserver()
  })
})

onBeforeUnmount(() => {
  window.removeEventListener('message', onMessage)
  resizeObserver?.disconnect()
  resizeObserver = null
})

// 暴露方法给父组件（可选）
defineExpose({
  /** 主动请求 iframe 上报高度 */
  requestHeight,
  /** 向 iframe 发送自定义消息 */
  postMessage: postToGuest
})
</script>

<style scoped>
.component-sandbox {
  width: 100%;
  position: relative;
  overflow: hidden;
}

.component-sandbox__error {
  padding: 8px;
  width: 100%;
}
</style>

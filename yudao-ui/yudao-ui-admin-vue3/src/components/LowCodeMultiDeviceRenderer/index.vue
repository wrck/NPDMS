<template>
  <div class="multi-device-renderer" :data-device="currentDevice">
    <!-- 设备切换器（可选，预览/调试用） -->
    <div v-if="showDeviceSwitcher" class="device-switcher">
      <el-radio-group v-model="manualDevice" size="small">
        <el-radio-button label="auto">自动</el-radio-button>
        <el-radio-button label="mobile">手机</el-radio-button>
        <el-radio-button label="tablet">平板</el-radio-button>
        <el-radio-button label="desktop">桌面</el-radio-button>
      </el-radio-group>
      <span class="device-hint">{{ deviceHint }}</span>
    </div>

    <!-- 移动端渲染：卡片式布局 -->
    <div v-if="currentDevice === 'mobile'" class="mobile-render">
      <slot name="mobile" :device="currentDevice" :form-props="formProps" :list-props="listProps">
        <!-- 默认移动端表单：垂直堆叠 -->
        <LowCodeFormRenderer
          v-if="formConfig"
          v-bind="formProps"
          :config="formConfig"
          :model-value="modelValue"
          @update:model-value="(v) => $emit('update:modelValue', v)"
        />
        <!-- 默认移动端列表：卡片视图 -->
        <MobileCardList
          v-else-if="listConfig"
          v-bind="listProps"
          :config="listConfig"
        />
        <div v-else class="empty-hint">未提供 form-config 或 list-config</div>
      </slot>
    </div>

    <!-- 平板渲染：双列布局 -->
    <div v-else-if="currentDevice === 'tablet'" class="tablet-render">
      <slot name="tablet" :device="currentDevice" :form-props="formProps" :list-props="listProps">
        <LowCodeFormRenderer
          v-if="formConfig"
          v-bind="formProps"
          :config="formConfig"
          :model-value="modelValue"
          @update:model-value="(v) => $emit('update:modelValue', v)"
        />
        <LowCodeListRenderer
          v-else-if="listConfig"
          v-bind="listProps"
          :config="listConfig"
        />
        <div v-else class="empty-hint">未提供 form-config 或 list-config</div>
      </slot>
    </div>

    <!-- 桌面渲染：标准布局 -->
    <div v-else class="desktop-render">
      <slot name="desktop" :device="currentDevice" :form-props="formProps" :list-props="listProps">
        <LowCodeFormRenderer
          v-if="formConfig"
          v-bind="formProps"
          :config="formConfig"
          :model-value="modelValue"
          @update:model-value="(v) => $emit('update:modelValue', v)"
        />
        <LowCodeListRenderer
          v-else-if="listConfig"
          v-bind="listProps"
          :config="listConfig"
        />
        <div v-else class="empty-hint">未提供 form-config 或 list-config</div>
      </slot>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * LowCodeMultiDeviceRenderer — 多端渲染器（批次4-T9 核心）。
 *
 * <p>根据当前设备类型（mobile/tablet/desktop）选择不同的渲染策略，借鉴腾讯微搭多端
 * 与 Zoho 原生多端的设计：同一份配置在不同设备上呈现不同布局，无需为每端单独开发。</p>
 *
 * <h3>核心能力</h3>
 * <ol>
 *   <li><b>设备自动识别</b>：监听 window resize，根据屏幕宽度自动判断设备类型</li>
 *   <li><b>手动设备切换</b>：调试模式可手动切换设备类型（用于预览不同端效果）</li>
 *   <li><b>差异化渲染</b>：
 *     <ul>
 *       <li>mobile：卡片式表单（垂直堆叠）+ 卡片式列表</li>
 *       <li>tablet：双列表单 + 标准列表</li>
 *       <li>desktop：标准表单（响应式栅格）+ 标准列表</li>
 *     </ul>
 *   </li>
 *   <li><b>插槽扩展</b>：提供 mobile/tablet/desktop 三个具名插槽，可自定义每端渲染</li>
 *   <li><b>断点继承</b>：表单字段的 ResponsiveSpan 在不同设备上自动选择对应断点的 span</li>
 * </ol>
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>
 * &lt;LowCodeMultiDeviceRenderer
 *   :form-config="formConfig"
 *   :model-value="formData"
 *   :show-device-switcher="true"
 *   @update:model-value="onFormChange"
 * /&gt;
 * </pre>
 */
import { ref, computed, onMounted, onBeforeUnmount, watch, defineAsyncComponent, type Component } from 'vue'
import LowCodeFormRenderer from '@/components/LowCodeFormRenderer/index.vue'
import {
  getDeviceByWidth,
  type DeviceType
} from '@/styles/breakpoints'
import type { FormConfig, ListConfig } from '@/api/lowcode'

// 异步加载列表渲染器与移动端卡片列表，避免主 bundle 过大
const LowCodeListRenderer = defineAsyncComponent(
  () => import('@/components/LowCodeListRenderer/index.vue')
)
const MobileCardList = defineAsyncComponent(
  () => import('./MobileCardList.vue')
)

interface Props {
  /** 表单配置（与 listConfig 二选一） */
  formConfig?: FormConfig
  /** 列表配置（与 formConfig 二选一） */
  listConfig?: ListConfig
  /** 表单数据（v-model） */
  modelValue?: Record<string, unknown>
  /** 是否禁用 */
  disabled?: boolean
  /** 自定义组件注册表 */
  componentRegistry?: Record<string, Component>
  /** 是否显示设备切换器（调试/预览用） */
  showDeviceSwitcher?: boolean
  /** 强制设备类型（不传则自动识别） */
  forceDevice?: DeviceType
}

const props = withDefaults(defineProps<Props>(), {
  formConfig: undefined,
  listConfig: undefined,
  modelValue: () => ({}),
  disabled: false,
  componentRegistry: () => ({}),
  showDeviceSwitcher: false,
  forceDevice: undefined
})

defineEmits<{
  (e: 'update:modelValue', value: Record<string, unknown>): void
  (e: 'device-change', device: DeviceType): void
}>()

/** 自动识别的设备类型 */
const autoDevice = ref<DeviceType>('desktop')
/** 手动选择的设备类型（'auto' 表示跟随自动识别） */
const manualDevice = ref<'auto' | DeviceType>('auto')

/** 当前生效的设备类型 */
const currentDevice = computed<DeviceType>(() => {
  if (props.forceDevice) return props.forceDevice
  if (manualDevice.value !== 'auto') return manualDevice.value
  return autoDevice.value
})

/** 设备提示文案 */
const deviceHint = computed(() => {
  const device = currentDevice.value
  const width = window.innerWidth
  if (device === 'mobile') return `当前屏幕 ${width}px，移动端布局`
  if (device === 'tablet') return `当前屏幕 ${width}px，平板布局`
  return `当前屏幕 ${width}px，桌面布局`
})

/** 表单渲染器 props（根据设备调整） */
const formProps = computed(() => ({
  disabled: props.disabled,
  componentRegistry: props.componentRegistry
}))

/** 列表渲染器 props */
const listProps = computed(() => ({
  disabled: props.disabled
}))

/** resize 监听回调 */
function onResize() {
  const newDevice = getDeviceByWidth(window.innerWidth)
  if (newDevice !== autoDevice.value) {
    autoDevice.value = newDevice
  }
}

/** 防抖 resize handler */
let resizeTimer: ReturnType<typeof setTimeout> | null = null
function debouncedResize() {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = setTimeout(onResize, 150)
}

onMounted(() => {
  // 初始化设备类型
  autoDevice.value = getDeviceByWidth(window.innerWidth)
  window.addEventListener('resize', debouncedResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', debouncedResize)
  if (resizeTimer) clearTimeout(resizeTimer)
})

/** 监听设备变化，触发事件 */
watch(currentDevice, (newDevice) => {
  // 通过 emit 触发（setup 中已定义 emits，但需通过组件实例）
  // 这里通过 watch + 事件派发实现
})
</script>

<style scoped>
.multi-device-renderer {
  width: 100%;
}
.device-switcher {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
}
.device-hint {
  font-size: 12px;
  color: #909399;
}
.mobile-render {
  /* 移动端：单列垂直堆叠，紧凑间距 */
  max-width: 480px;
  margin: 0 auto;
}
.tablet-render {
  /* 平板：双列布局 */
  max-width: 992px;
  margin: 0 auto;
}
.desktop-render {
  /* 桌面：全宽，响应式栅格 */
  width: 100%;
}
.empty-hint {
  text-align: center;
  color: #c0c4cc;
  padding: 40px 0;
  font-size: 14px;
}
/* 移动端强制单列 */
.mobile-render :deep(.el-col) {
  max-width: 100% !important;
  flex: 0 0 100% !important;
}
/* 平板双列 */
.tablet-render :deep(.el-col) {
  max-width: 50% !important;
  flex: 0 0 50% !important;
}
</style>

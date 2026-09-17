<script setup lang="ts">
/**
 * LowCodeFormRenderer 的稳定消费入口。
 *
 * 历史表单默认继续使用 V1；只有配置或调用方显式指定 v2 时才启用 FormCreate。
 * 对消费方统一暴露 V1 的 methods / events，避免 workflow、integration、关联页等
 * 业务模块感知具体渲染引擎。
 */
import { computed, onMounted, ref, shallowRef, type Component } from 'vue'
import type { FormFieldConfig } from '@/api/lowcode'
import LowCodeFormRenderer from '@/components/LowCodeFormRenderer/index.vue'
import LowCodeFormRendererV2 from '@/components/LowCodeFormRendererV2/index.vue'
import {
  componentMap,
  initBuiltinComponents
} from '@/components/LowCodeComponentRegistry'
import {
  LowCodeFormRendererVersion,
  resolveLowCodeFormRendererVersion,
  type VersionedFormConfig
} from '@/components/LowCodeFormRenderer/rendererVersion'
import { normalizeRendererConfig } from './compat'
import { useRendererModel } from './useRendererModel'

interface RendererExpose {
  validate?: () => Promise<boolean>
  submit?: () => Promise<void>
  resetFields?: () => void
  clearValidate?: () => void
  getFormData?: () => Record<string, unknown>
  formRef?: unknown
  formApi?: unknown
}

const props = withDefaults(
  defineProps<{
    config: VersionedFormConfig
    modelValue?: Record<string, unknown>
    disabled?: boolean
    componentRegistry?: Record<string, Component>
    eventHandlers?: Record<string, (...args: unknown[]) => void>
    /** 调用方临时覆盖版本；不写回持久化配置。 */
    rendererVersion?: string
  }>(),
  {
    disabled: false,
    componentRegistry: () => ({}),
    eventHandlers: () => ({}),
    rendererVersion: undefined
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, unknown>): void
  (e: 'submit', value: Record<string, unknown>): void
  (e: 'validate-fail', errors: unknown): void
  (e: 'field-change', field: FormFieldConfig, value: unknown): void
}>()

const rendererRef = ref<RendererExpose | null>(null)
const runtimeComponentRegistry = shallowRef<Record<string, Component>>(componentMap())

/**
 * 仅在运行时做 Schema 兼容，不迁移持久化配置。
 * 历史 V1 和当前设计器产出的 custom 字段都从同一 Facade 进入。
 */
const rendererConfig = computed<VersionedFormConfig>(() => normalizeRendererConfig(props.config))

const effectiveComponentRegistry = computed<Record<string, Component>>(() =>
  Object.keys(props.componentRegistry).length > 0
    ? props.componentRegistry
    : runtimeComponentRegistry.value
)

onMounted(async () => {
  if (Object.keys(props.componentRegistry).length > 0) return
  await initBuiltinComponents()
  runtimeComponentRegistry.value = componentMap()
})

const effectiveRendererVersion = computed(() =>
  resolveLowCodeFormRendererVersion(props.config, props.rendererVersion)
)

const { model: rendererModel, updateModel } = useRendererModel(
  () => props.modelValue,
  () => effectiveRendererVersion.value,
  () => rendererRef.value?.getFormData?.()
)

function handleModelUpdate(value: Record<string, unknown>): void {
  updateModel(value)
  emit('update:modelValue', value)
}

const rendererComponent = computed(() =>
  effectiveRendererVersion.value === LowCodeFormRendererVersion.V2
    ? LowCodeFormRendererV2
    : LowCodeFormRenderer
)

async function validate(): Promise<boolean> {
  return (await rendererRef.value?.validate?.()) ?? false
}

async function submit(): Promise<void> {
  await rendererRef.value?.submit?.()
}

function resetFields(): void {
  rendererRef.value?.resetFields?.()
}

function clearValidate(): void {
  rendererRef.value?.clearValidate?.()
}

function getFormData(): Record<string, unknown> {
  return rendererRef.value?.getFormData?.() ?? { ...rendererModel.value }
}

/** V1 兼容逃生口；V2 时返回 FormCreate Api。业务代码优先使用上面的统一 methods。 */
const formRef = computed(() => rendererRef.value?.formRef ?? rendererRef.value?.formApi)
const formApi = computed(() => rendererRef.value?.formApi)

defineExpose({
  validate,
  submit,
  resetFields,
  clearValidate,
  getFormData,
  formRef,
  formApi,
  rendererVersion: effectiveRendererVersion
})
</script>

<template>
  <component
    :is="rendererComponent"
    ref="rendererRef"
    :config="rendererConfig"
    :model-value="rendererModel"
    :disabled="disabled"
    :component-registry="effectiveComponentRegistry"
    :event-handlers="eventHandlers"
    @update:model-value="handleModelUpdate"
    @submit="(value: Record<string, unknown>) => emit('submit', value)"
    @validate-fail="(errors: unknown) => emit('validate-fail', errors)"
    @field-change="(field: FormFieldConfig, value: unknown) => emit('field-change', field, value)"
  />
</template>

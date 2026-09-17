import { shallowRef, watch } from 'vue'
import { mergeRendererModel } from './modelState'

/**
 * 将表单数据的生命周期提升到 Facade；更换渲染引擎不等于更换业务表单。
 * 外部 modelValue 仍采用 V1 的增量合并语义，不要求消费方一定绑定 v-model。
 */
export function useRendererModel(
  readModel: () => Record<string, unknown> | undefined,
  readVersion: () => string,
  readSnapshot: () => Record<string, unknown> | undefined
) {
  const model = shallowRef<Record<string, unknown>>({ ...(readModel() || {}) })

  // 在旧组件卸载前保存最新值（包括尚未 emit 的编辑值和默认值）。
  watch(readVersion, () => updateModel(readSnapshot()), { flush: 'sync' })

  // 保持 pre 调度：同一轮同时切换引擎和回填数据时，新的外部回填在快照之后生效。
  watch(readModel, (value) => updateModel(value), { deep: true })

  function updateModel(value?: Record<string, unknown>): void {
    model.value = mergeRendererModel(model.value, value)
  }

  return { model, updateModel }
}

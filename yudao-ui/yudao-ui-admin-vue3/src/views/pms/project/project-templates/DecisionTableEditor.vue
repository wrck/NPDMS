<template>
  <section class="decision-table-editor" aria-label="决策表编辑器">
    <el-alert
      title="原生 DMN 决策表；使用 JUEL 条件，不自动转换条件树。输入列需绑定开放字段。"
      type="info"
      :closable="false"
    />
    <el-alert v-if="failure" :title="failure" type="error" :closable="false" />
    <div ref="container" v-loading="loading" :inert="readonly" class="decision-modeler"></div>
    <el-form label-position="top" :disabled="readonly" class="input-bindings">
      <el-form-item
        v-for="input in inputs"
        :key="input.variable"
        :label="`${input.label}（${input.variable || '请先命名变量'}）`"
      >
        <el-select
          :model-value="modelValue.inputFields[input.variable]"
          filterable
          placeholder="绑定实际字段"
          @update:model-value="bind(input.variable, $event)"
        >
          <el-option
            v-for="field in fields"
            :key="field.code"
            :value="field.code"
            :label="field.label"
          />
        </el-select>
      </el-form-item>
    </el-form>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, toRaw, watch } from 'vue'
import type DmnManager from 'dmn-js/lib/Modeler'
import type { DecisionTableDefinition, RuleField } from '@/api/pms/project/project-templates/rules'
import { getRuleFields } from '@/api/pms/project/project-templates/rules'
import 'dmn-js/dist/assets/diagram-js.css'
import 'dmn-js/dist/assets/dmn-js-shared.css'
import 'dmn-js/dist/assets/dmn-js-decision-table.css'
import 'dmn-js/dist/assets/dmn-js-decision-table-controls.css'
import 'dmn-js/dist/assets/dmn-font/css/dmn.css'
const props = defineProps<{ modelValue: DecisionTableDefinition; readonly?: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: DecisionTableDefinition]
  outputs: [value: { name: string; type: string }[]]
}>()
const container = ref<HTMLElement>()
const loading = ref(false)
const failure = ref('')
const fields = ref<RuleField[]>([])
const inputs = ref<{ variable: string; label: string }[]>([])
let manager: DmnManager | undefined
let importing = false
let lastXml = ''
let pending: Promise<void> = Promise.resolve()
let generation = 0
let lastEmitted: DecisionTableDefinition | undefined
const publish = (value: DecisionTableDefinition) => {
  lastEmitted = value
  emit('update:modelValue', value)
}
const metadata = () => {
  const view = manager?.getViews().find((item) => item.type === 'decisionTable')
  inputs.value = (view?.element.decisionLogic?.input ?? []).map((input) => ({
    variable: input.inputExpression?.text ?? '',
    label: input.label ?? '输入列'
  }))
  emit(
    'outputs',
    (view?.element.decisionLogic?.output ?? []).map((output) => ({
      name: output.name ?? '',
      type: output.typeRef ?? 'string'
    }))
  )
  return view
}
const flush = async (instance = manager, current = generation) => {
  if (!instance || instance !== manager || current !== generation || importing || props.readonly) return
  const result = await instance.saveXML({ format: true })
  if (instance !== manager || current !== generation || props.readonly) return
  const source = props.modelValue
  const view = metadata()
  lastXml = result.xml
  const inputFields: Record<string, string> = {}
  for (const input of inputs.value)
    inputFields[input.variable] = props.modelValue.inputFields[input.variable] ?? ''
  publish({
    ...source,
    xml: result.xml,
    decisionKey: view?.element.id ?? source.decisionKey,
    inputFields
  })
}
const changed = (instance: DmnManager, current: number) => {
  if (importing || props.readonly || instance !== manager || current !== generation) return
  pending = pending.then(() => flush(instance, current)).catch(() => {
    if (instance === manager && current === generation)
      failure.value = '决策表内容暂未同步，请重试保存。'
  })
}
const load = async () => {
  if (!container.value) return
  const current = ++generation
  const source = props.modelValue
  lastEmitted = undefined
  loading.value = true
  failure.value = ''
  importing = true
  manager?.destroy()
  manager = undefined
  try {
    const { default: Manager } = props.readonly
      ? await import('dmn-js/lib/NavigatedViewer')
      : await import('dmn-js/lib/Modeler')
    if (current !== generation) return
    const instance = new Manager({ container: container.value })
    manager = instance
    instance.on('viewer.created', ({ viewer }) => viewer.on('commandStack.changed', () => changed(instance, current)))
    // dmn-js import/open/save are asynchronous; an older editor must never publish into a new definition.
    // https://github.com/bpmn-io/dmn-js#usage
    await instance.importXML(source.xml)
    if (current !== generation || instance !== manager) return
    const view = metadata()
    if (!view) throw new Error('未找到决策表')
    await instance.open(view)
    if (current !== generation || instance !== manager) return
    lastXml = source.xml
    if (!props.readonly && !fields.value.length) {
      const availableFields = await getRuleFields()
      if (current === generation) fields.value = availableFields
    }
  } catch (error) {
    if (current === generation) failure.value = error instanceof Error ? error.message : '决策表加载失败'
  } finally {
    if (current === generation) {
      importing = false
      loading.value = false
    }
  }
}
const bind = (variable: string, field: string) => {
  if (props.readonly || importing) return
  publish({
    ...props.modelValue,
    inputFields: { ...props.modelValue.inputFields, [variable]: field }
  })
}
watch(
  [() => props.modelValue, () => props.modelValue.xml, () => props.readonly],
  ([value, xml, readonly], [, , previousReadonly]) => {
    if (toRaw(value) !== lastEmitted || xml !== lastXml || readonly !== previousReadonly) void load()
  }
)
onMounted(load)
onBeforeUnmount(() => {
  generation++
  manager?.destroy()
  manager = undefined
})
defineExpose({
  flush: async () => {
    const instance = manager
    const current = generation
    await pending
    await flush(instance, current)
  }
})
</script>

<style scoped>
.decision-modeler {
  min-height: 280px;
  overflow: auto;
  margin: 12px 0;
  background: var(--el-bg-color);
}
.input-bindings {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}
.decision-table-editor :deep(.dmn-decision-table-container) {
  min-width: 480px;
}
</style>

<template>
  <section class="operation-contract" aria-label="业务操作前后置规则">
    <el-divider content-position="left">业务操作规则</el-divider>
    <p class="field-hint">操作规则只用于明确的项目办理或受控入口，不授予业务权限。</p>
    <el-alert v-if="parseError" :title="parseError" type="error" :closable="false" />
    <el-alert v-if="catalogError" :title="catalogError" type="warning" :closable="false" />
    <template v-if="contract">
      <section v-for="operation in contract.operations" :key="operation.operationCode" class="operation-row">
        <header>
          <strong>{{ operationLabel(operation) }} · v{{ operation.operationVersion }}</strong>
          <el-button v-if="!readonly" link type="danger" @click="remove(operation.operationCode)">移除操作</el-button>
        </header>
        <el-alert
          v-if="!readonly && !catalogLoading && !descriptor(operation)?.runtimeAvailable"
          title="此操作的执行适配尚未就绪：可以保存草稿，发布和计划生效仍由服务端拒绝。"
          type="info" :closable="false"
        />
        <RuleSlotEditor
          :document="document"
          :model-value="operation.pre.mode === 'RULE' ? operation.pre.ruleKey : undefined"
          :label="`${operationLabel(operation)} · 前置`"
          :readonly="readonly"
          empty-text="NONE：不增加操作前置条件；仍须满足业务权限与节点办理资格。"
          @update:model-value="setRule(operation.operationCode, 'pre', $event)"
        />
        <RuleSlotEditor
          :document="document"
          :model-value="operation.post.mode === 'RULE' ? operation.post.ruleKey : undefined"
          :label="`${operationLabel(operation)} · 后置`"
          :readonly="readonly"
          empty-text="NONE：不增加操作后置条件；不能用异步完成条件代替本事务结果校验。"
          @update:model-value="setRule(operation.operationCode, 'post', $event)"
        />
      </section>
    </template>
    <p v-else-if="!parseError" class="field-hint">未配置操作子契约，保留原绑定解释。</p>
    <template v-if="!readonly">
      <el-select
        :model-value="undefined"
        :loading="catalogLoading"
        :disabled="!!parseError || !!catalogError || !choices.length"
        aria-label="添加业务操作规则" placeholder="选择已登记业务操作" filterable
        @update:model-value="add"
      >
        <el-option v-for="item in choices" :key="catalogKey(item)" :value="catalogKey(item)"
          :label="`${item.label} · v${item.operationVersion}`" />
      </el-select>
      <el-button v-if="rawContract !== undefined" link type="danger" @click="clear">移除整个操作子契约</el-button>
      <p class="field-hint">前置支持固定条件、项目字段/决策表及时间到达；后置仅支持本事务内项目字段/决策表和固定条件。未接入的事实类型会被编译器拒绝，不会自动改成 NONE。</p>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import type { TemplateDesignerDocument, WorkBindingSpec } from '@/api/pms/project/project-templates'
import { getBusinessOperationCatalog, type BusinessOperationDescriptor, type OperationWorkBindingSpec } from '@/api/pms/project/project-templates/operations'
import { readOperationContract, replaceOperationCheck, withOperationContract, type TemplateOperation, type TemplateOperationContract } from './operationContract'
import RuleSlotEditor from './RuleSlotEditor.vue'

const props = defineProps<{ binding: WorkBindingSpec; document: TemplateDesignerDocument; readonly?: boolean }>()
const emit = defineEmits<{ 'update:binding': [binding: OperationWorkBindingSpec] }>()
const rawContract = computed(() => (props.binding as OperationWorkBindingSpec).operationContract)
const parsed = computed(() => {
  try { return { value: readOperationContract(rawContract.value), error: '' } }
  catch (error) { return { value: undefined, error: error instanceof Error ? error.message : '操作子契约无法读取' } }
})
const contract = computed(() => parsed.value.value)
const parseError = computed(() => parsed.value.error)
const catalog = ref<BusinessOperationDescriptor[]>([])
const catalogLoading = ref(false)
const catalogError = ref('')
let generation = 0
let disposed = false
const catalogKey = (item: BusinessOperationDescriptor) => `${item.operationCode}@${item.operationVersion}`
const descriptor = (operation: TemplateOperation) => catalog.value.find(item =>
  item.operationCode === operation.operationCode && item.operationVersion === operation.operationVersion)
const operationLabel = (operation: TemplateOperation) => descriptor(operation)?.label ?? operation.operationCode
const choices = computed(() => catalog.value.filter(item =>
  !contract.value?.operations.some(operation => operation.operationCode === item.operationCode)))
watch(() => [props.binding.targetContextCode, props.binding.targetObjectType, props.readonly] as const,
  async ([owner, objectType, readonly]) => {
    const current = ++generation
    catalog.value = []
    catalogError.value = ''
    catalogLoading.value = false
    if (readonly || !owner || !objectType) return
    catalogLoading.value = true
    try {
      const next = await getBusinessOperationCatalog(owner, objectType)
      if (!disposed && current === generation) catalog.value = next
    } catch {
      if (!disposed && current === generation) catalogError.value = '无法读取操作目录；原配置保留，不能根据页面名称猜测操作。'
    } finally {
      if (!disposed && current === generation) catalogLoading.value = false
    }
  }, { immediate: true })
const update = (next: TemplateOperationContract | undefined) => {
  if (props.readonly) return
  emit('update:binding', withOperationContract(props.binding, next))
}
const add = (key: string) => {
  if (props.readonly || parseError.value || catalogLoading.value || catalogError.value) return
  const item = choices.value.find(entry => catalogKey(entry) === key)
  if (!item) return
  update({ version: 1, operations: [...(contract.value?.operations ?? []), {
    operationCode: item.operationCode, operationVersion: item.operationVersion,
    pre: { mode: 'NONE' }, post: { mode: 'NONE' }
  }] })
}
const setRule = (operationCode: string, phase: 'pre' | 'post', key: string | undefined) => {
  if (props.readonly || !contract.value) return
  update(replaceOperationCheck(contract.value, operationCode, phase,
    key ? { mode: 'RULE', ruleKey: key } : { mode: 'NONE' }))
}
const remove = (operationCode: string) => {
  if (props.readonly || !contract.value) return
  const remaining = contract.value.operations.filter(item => item.operationCode !== operationCode)
  update(remaining.length ? { version: 1, operations: remaining } : undefined)
}
const clear = async () => {
  const binding = props.binding, document = props.document, current = generation
  if (props.readonly) return
  try { await ElMessageBox.confirm('移除本绑定的操作子契约？不会修改在用项目、节点规则或共享规则定义。', '移除操作规则') }
  catch { return }
  if (disposed || current !== generation || props.readonly || props.binding !== binding || props.document !== document) return
  update(undefined)
}
onBeforeUnmount(() => { disposed = true; generation++ })
</script>

<style scoped>
.operation-row { margin: 12px 0; padding: 12px; border: 1px solid var(--el-border-color-lighter); }
.operation-row header { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.field-hint { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
</style>

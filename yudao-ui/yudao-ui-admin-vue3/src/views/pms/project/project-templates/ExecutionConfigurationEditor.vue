<template>
  <section class="execution-editor" aria-label="执行与结果配置">
    <el-divider content-position="left">执行与结果配置</el-divider>
    <p class="field-hint">业务操作、结果订阅和页面分别配置。订阅满足后仍须通过节点完成条件。</p>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" />
    <template v-if="!legacyOperations">
      <h4>业务操作</h4>
      <el-alert v-if="ambiguousOperations.length" type="warning" :closable="false"
        title="部分动作存在多个实现版本，无法唯一选择；请先明确部署绑定，已有配置保留。" />
      <section v-for="(operation, index) in modelValue?.operations ?? []" :key="index" class="configuration-row">
        <header><strong>{{ operationLabel(operation.operationCode) }}</strong>
          <el-button v-if="!readonly" link type="danger" @click="removeOperation(index)">移除操作</el-button></header>
        <p class="field-hint">{{ operation.permissionCode }}</p>
        <RuleSlotEditor :document="document" :readonly="readonly" label="操作前置"
          :model-value="operation.pre.mode === 'RULE' ? operation.pre.ruleKey : undefined"
          empty-text="无附加前置条件" @update:model-value="setCheck(index, 'pre', $event)" />
        <RuleSlotEditor :document="document" :readonly="readonly" label="操作后置" operation-post
          :model-value="operation.post.mode === 'RULE' ? operation.post.ruleKey : undefined"
          empty-text="无附加后置条件" @update:model-value="setCheck(index, 'post', $event)" />
      </section>
      <el-select v-if="!readonly" :model-value="undefined" :disabled="!binding || loading" filterable
        aria-label="按权限选择业务动作" placeholder="按权限选择业务动作" @update:model-value="addOperation">
        <el-option v-for="operation in operationChoices" :key="operation.operationCode" :value="operation.operationCode"
          :label="`${operation.label} · ${operation.permissionCode}`" :disabled="!operation.runtimeAvailable" />
      </el-select>
      <p v-if="!binding" class="field-hint">选择业务绑定后可配置操作；仅订阅结果无需业务绑定。</p>
    </template>
    <h4>结果订阅</h4>
    <section v-for="(subscription, index) in modelValue?.subscriptions ?? []" :key="index" class="configuration-row">
      <header><strong>{{ resultLabel(subscription) }}</strong>
        <el-button v-if="!readonly" link type="danger" @click="removeSubscription(index)">移除订阅</el-button></header>
      <el-form label-position="top" :disabled="readonly">
        <el-form-item label="订阅标识"><el-input :model-value="subscription.key" @update:model-value="setSubscription(index, { key: $event })" /></el-form-item>
        <el-form-item label="对象范围">
          <el-select :model-value="subscription.scope.mode" @update:model-value="setScope(index, $event)">
            <el-option value="PROJECT" label="当前项目" /><el-option value="OBJECTS" label="指定业务对象" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="subscription.scope.mode === 'OBJECTS'" label="业务对象标识">
          <el-select multiple filterable allow-create default-first-option :model-value="subscription.scope.objectIds"
            placeholder="输入业务对象标识并回车" @update:model-value="setObjects(index, $event)">
            <el-option v-for="id in subscription.scope.objectIds" :key="id" :value="id" :label="id" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果获取方式">
          <el-select :model-value="subscription.policy.acquisition" @update:model-value="setAcquisition(index, $event)">
            <el-option value="REUSE_EXISTING" label="可复用已有结果" :disabled="!source(subscription)?.inventory || !source(subscription)?.currentLookup" />
            <el-option value="NEW_RESULT" label="本轮形成的新结果" :disabled="!source(subscription)?.inventory || !source(subscription)?.commitBarrier" />
            <el-option value="PINNED_RESULT" label="指定结果" :disabled="!source(subscription)?.exactLookup" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="subscription.policy.acquisition === 'PINNED_RESULT'" label="结果标识">
          <el-input :model-value="subscription.policy.pinnedResultId" @update:model-value="setPolicy(index, { pinnedResultId: $event })" />
        </el-form-item>
        <el-form-item label="有效性要求">
          <el-select :model-value="subscription.policy.validity" @update:model-value="setPolicy(index, { validity: $event })">
            <el-option value="CURRENT_VALID" label="当前仍有效" />
            <el-option value="HISTORICAL_FACT" label="历史已形成事实" :disabled="!source(subscription)?.historicalLookup" />
          </el-select>
        </el-form-item>
        <el-form-item label="满足方式">
          <el-select :model-value="subscription.policy.selection" @update:model-value="setPolicy(index, { selection: $event })">
            <el-option value="EXACT_ONE" label="恰好一个结果" /><el-option value="ANY_MATCHING" label="任一结果满足" />
            <el-option value="ALL_EXPECTED" label="全部指定对象满足" :disabled="subscription.scope.mode !== 'OBJECTS'" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert v-if="!readonly && !loading && !error && !source(subscription)" title="当前结果来源不可用，原配置已保留，请重新核对后发布。" type="warning" :closable="false" />
    </section>
    <el-select v-if="!readonly" :model-value="undefined" :loading="loading" filterable
      aria-label="添加结果订阅" placeholder="选择业务结果来源" @update:model-value="addSubscription">
      <el-option v-for="item in results" :key="sourceKey(item)" :value="sourceKey(item)" :label="resultLabel(item)"
        :disabled="!item.changes || !item.commitBarrier || !(item.exactLookup || item.inventory && item.currentLookup)" />
    </el-select>
    <el-button v-if="!readonly && binding && modelValue?.subscriptions?.length && !modelValue?.operations?.length && !legacyOperations"
      @click="emit('pure-subscription')">改为仅订阅结果</el-button>
    <h4>业务页面</h4>
    <el-select :model-value="modelValue?.presentation?.pageUrl" :disabled="readonly || !binding"
      clearable aria-label="业务页面地址" placeholder="使用原业务视图" @update:model-value="setPage">
      <el-option v-for="route in routes" :key="route.pageUrl" :value="route.pageUrl" :label="route.pageUrl" />
    </el-select>
    <template v-if="modelValue?.presentation">
      <el-checkbox :model-value="!!modelValue.presentation.query?.projectId" :disabled="readonly"
        @update:model-value="setParameter('projectId', $event === true)">页面携带当前项目标识</el-checkbox>
      <el-checkbox :model-value="!!modelValue.presentation.query?.objectId" :disabled="readonly"
        @update:model-value="setParameter('objectId', $event === true)">页面携带当前业务对象标识</el-checkbox>
    </template>
    <p class="field-hint">页面参数用于展示；业务权限和提交由原业务入口校验。</p>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { TemplateDesignerDocument, WorkBindingSpec } from '@/api/pms/project/project-templates'
import { getBusinessOperationCatalog, type BusinessOperationDescriptor, type OperationWorkBindingSpec } from '@/api/pms/project/project-templates/operations'
import { getBusinessResultCatalog, type BusinessResultDescriptor, type ExecutionSubscription, type ExecutionEvidencePolicy, type NodeExecutionConfiguration } from '@/api/pms/project/project-templates/execution'
import { businessPageRoutes } from '@/components/BusinessView/presentationRoute'
import RuleSlotEditor from './RuleSlotEditor.vue'

const props = defineProps<{ modelValue?: NodeExecutionConfiguration; binding?: WorkBindingSpec; document: TemplateDesignerDocument; readonly?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: NodeExecutionConfiguration]; 'pure-subscription': [] }>()
const operations = ref<BusinessOperationDescriptor[]>([])
const results = ref<BusinessResultDescriptor[]>([])
const error = ref(''), loading = ref(false)
let generation = 0
const legacyOperations = computed(() => (props.binding as OperationWorkBindingSpec | undefined)?.operationContract !== undefined && !props.modelValue?.operations?.length)
const ambiguousOperations = computed(() => operations.value.filter(item => operations.value.filter(other => other.operationCode === item.operationCode).length > 1))
const operationChoices = computed(() => operations.value.filter(item => item.permissionCode
  && operations.value.filter(other => other.operationCode === item.operationCode).length === 1
  && !props.modelValue?.operations?.some(chosen => chosen.operationCode === item.operationCode)))
const routes = computed(() => Object.values(businessPageRoutes).filter(route => route.ownerContext === props.binding?.targetContextCode
  && route.entityType === props.binding?.targetObjectType && route.componentKey === props.binding?.componentKey))
const sourceKey = (item: { ownerContext: string; entityType: string; resultType: string }) => JSON.stringify([item.ownerContext, item.entityType, item.resultType])
const source = (item: ExecutionSubscription) => results.value.find(candidate => sourceKey(candidate) === sourceKey(item))
const resultNames: Record<string, string> = { SURVEY_CONFIRMED: '工勘确认结果', REQUIREMENT_ANALYSIS_COMPLETED: '需求分析完成结果', REPORT_VERSION_PUBLISHED: '验收报告发布结果' }
const resultLabel = (item: { entityType: string; resultType: string }) => resultNames[item.resultType] ?? `${item.entityType} · ${item.resultType}`
const operationLabel = (code?: string) => operations.value.find(item => item.operationCode === code)?.label ?? code ?? '权限对应动作'
// 多源watch逐值比较：父组件以新对象字面量传入结构相同的binding时不得重置已加载目录。
watch([() => props.binding?.targetContextCode, () => props.binding?.targetObjectType, () => props.readonly],
  async ([owner, type, readonly]) => {
  const current = ++generation
  loading.value = true; error.value = ''; operations.value = []; results.value = []
  if (readonly) { loading.value = false; return }
  const responses = await Promise.allSettled([owner && type ? getBusinessOperationCatalog(owner, type) : Promise.resolve([]), getBusinessResultCatalog()])
  if (current !== generation) return
  if (responses[0].status === 'fulfilled') operations.value = responses[0].value
  if (responses[1].status === 'fulfilled') results.value = responses[1].value
  if (responses.some(response => response.status === 'rejected')) error.value = '部分配置目录读取失败；已有配置保留，可重开页面后重试。'
  loading.value = false
}, { immediate: true })
onBeforeUnmount(() => { generation++ })
const change = (apply: (next: NodeExecutionConfiguration) => void) => {
  if (props.readonly) return
  const next: NodeExecutionConfiguration = JSON.parse(JSON.stringify(props.modelValue ?? {}))
  apply(next); emit('update:modelValue', next)
}
const addOperation = (code: string) => {
  const item = operationChoices.value.find(candidate => candidate.operationCode === code)
  if (!item?.permissionCode || !item.runtimeAvailable || !props.binding?.targetContextCode || !props.binding.targetObjectType) return
  const operation = { ownerContext: props.binding.targetContextCode, entityType: props.binding.targetObjectType,
    permissionCode: item.permissionCode, operationCode: item.operationCode, pre: { mode: 'NONE' as const }, post: { mode: 'NONE' as const } }
  change(next => { next.operations = [...(next.operations ?? []), operation] })
}
const removeOperation = (index: number) => change(next => { next.operations?.splice(index, 1) })
const setCheck = (index: number, checkpoint: 'pre' | 'post', ruleKey?: string) => change(next => {
  if (next.operations?.[index]) next.operations[index][checkpoint] = ruleKey ? { mode: 'RULE', ruleKey } : { mode: 'NONE' }
})
const addSubscription = (key: string) => {
  const item = results.value.find(candidate => sourceKey(candidate) === key)
  if (!item?.changes || !item.commitBarrier || !(item.exactLookup || item.inventory && item.currentLookup)) return
  change(next => {
    let number = 1
    while (next.subscriptions?.some(subscription => subscription.key === `result_${number}`)) number++
    const policy: ExecutionEvidencePolicy = item.inventory && item.currentLookup
      ? { acquisition: 'REUSE_EXISTING', validity: 'CURRENT_VALID', selection: 'EXACT_ONE' }
      : { acquisition: 'PINNED_RESULT', pinnedResultId: '', validity: 'CURRENT_VALID', selection: 'EXACT_ONE' }
    next.subscriptions = [...(next.subscriptions ?? []), { key: `result_${number}`, ownerContext: item.ownerContext,
      entityType: item.entityType, resultType: item.resultType, scope: { mode: 'PROJECT' },
      policy }]
  })
}
const removeSubscription = (index: number) => change(next => { next.subscriptions?.splice(index, 1) })
const setSubscription = (index: number, value: Partial<ExecutionSubscription>) => change(next => {
  if (next.subscriptions?.[index]) Object.assign(next.subscriptions[index], value)
})
const setPolicy = (index: number, value: Partial<ExecutionEvidencePolicy>) => change(next => {
  if (next.subscriptions?.[index]) Object.assign(next.subscriptions[index].policy, value)
})
const setScope = (index: number, mode: 'PROJECT' | 'OBJECTS') => change(next => {
  const item = next.subscriptions?.[index]; if (!item) return
  item.scope = mode === 'PROJECT' ? { mode } : { mode, objectIds: [] }
  if (mode === 'PROJECT' && item.policy.selection === 'ALL_EXPECTED') item.policy.selection = 'EXACT_ONE'
})
const setObjects = (index: number, value: string[]) => setSubscription(index, { scope: { mode: 'OBJECTS', objectIds: [...value] } })
const setAcquisition = (index: number, acquisition: ExecutionEvidencePolicy['acquisition']) => change(next => {
  const item = next.subscriptions?.[index]; if (!item) return
  item.policy.acquisition = acquisition
  if (acquisition === 'PINNED_RESULT') item.policy.pinnedResultId = ''
  else delete item.policy.pinnedResultId
})
const setPage = (pageUrl?: string) => change(next => {
  if (pageUrl) next.presentation = { pageUrl, query: next.presentation?.query ?? {} }
  else delete next.presentation
})
const setParameter = (key: 'projectId' | 'objectId', enabled: boolean) => change(next => {
  if (!next.presentation) return
  const query = next.presentation.query ??= {}
  if (enabled) query[key] = key === 'projectId' ? '$project.id' : '$object.id'
  else delete query[key]
})
</script>

<style scoped>
.configuration-row { padding: 12px; margin: 12px 0; border: 1px solid var(--el-border-color-lighter); border-radius: 4px; }
.configuration-row header { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.field-hint { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
.execution-editor :deep(.el-select) { width: 100%; }
</style>

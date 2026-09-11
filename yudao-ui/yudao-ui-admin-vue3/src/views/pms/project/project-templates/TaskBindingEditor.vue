<template>
  <section class="task-binding" aria-label="任务办理配置">
    <el-alert v-if="failure" :title="failure" type="error" :closable="false" />
    <el-button v-if="failure" link @click="load">重新加载办理选择</el-button>
    <el-form label-position="top" :disabled="readonly || loading">
      <el-form-item label="办理页面 / 表单">
        <el-select
          :model-value="selectedKey"
          placeholder="保留当前办理方式，或选择已发布页面 / 表单"
          filterable
          :disabled="!canBind"
          @update:model-value="chooseView"
        >
          <el-option v-for="view in views" :key="String(view.id)" :value="view.id" :label="viewLabel(view)" />
          <el-option
            v-if="currentView && !views.some((view) => sameBusinessViewId(view.id, currentView?.id))"
            :value="currentView.id"
            :label="`${viewLabel(currentView)}（冻结快照）`"
            disabled
          />
        </el-select>
        <p class="help">{{ currentDescription }}。这里只配置办理入口；实际业务对象、命令和权限仍由对应 Owner 管理。</p>
      </el-form-item>

      <template v-if="selectedMetadata">
        <div class="entity-summary">
          <div><span>业务实体</span><strong>{{ entityLabel(selectedMetadata.entityType) }}</strong></div>
          <div><span>业务归属</span><strong>{{ ownerLabel(selectedMetadata.ownerContext) }}</strong></div>
        </div>
        <el-form-item label="业务对象关联方式">
          <el-select :model-value="source" :disabled="!modelValue || !canBind" @update:model-value="changeSource">
            <el-option value="REFERENCE_EXISTING" label="关联本项目业务" />
            <el-option value="READ_ONLY_AGGREGATE" label="只读查看本项目业务" />
            <el-option value="CREATE_ON_FIRST_ACTION" label="首次办理时创建（当前未接入创建服务）" disabled />
          </el-select>
          <p class="help">按项目上下文解析，不把某个项目实例 ID 固化到模板。</p>
        </el-form-item>
      </template>

      <el-form-item v-if="needsRequirementSource(selectedMetadata)" label="需求分析表单" required>
        <el-select :model-value="requirementSourceId" filterable placeholder="选择已发布需求分析表单"
          :disabled="readonly || !canBind" @update:model-value="chooseRequirementSource">
          <el-option v-for="form in forms" :key="String(form.currentPublishedRevisionId)" :value="form.currentPublishedRevisionId"
            :label="`${form.templateName} · 第${form.currentPublishedRevisionNo}版`" />
        </el-select>
        <p class="help">所选表单修订作为 WorkBinding 参数冻结，发布时重新校验，不按“最新版本”漂移。</p>
      </el-form-item>

      <el-form-item label="完成依据">
        <el-select
          v-if="modelValue && completionOptions.length"
          :model-value="modelValue.completion?.factCode"
          placeholder="选择领域真实完成依据"
          @update:model-value="chooseCompletion"
        >
          <el-option v-for="option in completionOptions" :key="option.code" :value="option.code" :label="option.label" />
        </el-select>
        <el-select v-if="modelValue?.completion" :model-value="modelValue.completion.quantifier" @update:model-value="changeQuantifier">
          <el-option value="ALL" label="全部关联记录满足" /><el-option value="ANY" label="至少一条关联记录满足" />
        </el-select>
        <div class="completion" :class="{ pending: completionPending }">
          <strong>{{ completionPending ? '完成规则待配置' : completionLabel }}</strong>
          <p class="help">{{ completionPending
            ? '已选择外部业务办理界面，但当前仍是任务原生完成规则。发布前必须改成真实业务完成事实。'
            : '完成规则直接保存在 TaskNode 中，Compiler 发布时冻结；模板配置不会授予业务对象权限。' }}</p>
        </div>
      </el-form-item>

      <el-button v-if="modelValue && !readonly" link @click="emit('update:modelValue', undefined)">撤销本次办理方式调整</el-button>
      <el-button v-if="canRegister && canBind && !readonly" plain @click="registerOpen = !registerOpen">
        {{ registerOpen ? '收起登记' : '使用其他已接入页面或表单' }}
      </el-button>

      <section v-if="registerOpen && canRegister && !readonly" class="registration">
        <h4>选择已接入的办理功能</h4>
        <el-form-item label="页面或表单类型">
          <el-select v-model="componentKey" placeholder="选择实际已接入的功能" @update:model-value="formId = undefined">
            <el-option v-for="component in components" :key="catalogKey(component)" :value="catalogKey(component)" :label="componentLabel(component)" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="selectedComponent?.viewSource === 'DYNAMIC_FORM'" label="已发布表单">
          <el-select v-model="formId" filterable placeholder="按名称选择已发布表单">
            <el-option v-for="form in forms" :key="String(form.currentPublishedRevisionId)" :value="form.currentPublishedRevisionId"
              :label="`${form.templateName} · 第${form.currentPublishedRevisionNo}版`" />
          </el-select>
        </el-form-item>
        <p class="help">保存草稿时只登记/发布 BusinessView；模板本身直接保存 WorkBinding，不再生成 TASK/WORK_BINDING/COMPLETION_RULE 定义修订。</p>
        <el-button :disabled="!selectedComponent || (selectedComponent.viewSource === 'DYNAMIC_FORM' && !formId)" @click="chooseComponent">用于此任务</el-button>
      </section>
      <p v-if="!canBind && !readonly" class="help">调整办理方式需要模板维护权限；已有冻结配置仍可查看。</p>
    </el-form>
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { hasPermission } from '@/directives/permission/hasPermi'
import * as Views from '@/api/pms/platform/business-view'
import type { BusinessViewComponentVO, BusinessViewId, BusinessViewRegistrationVO } from '@/api/pms/platform/business-view'
import { sameBusinessViewId } from '@/api/pms/platform/business-view/ids'
import { getTemplateSelection, type DynamicFormSelectionVO } from '@/api/pms/platform/dynamic-form'
import * as TemplateApi from '@/api/pms/project/project-templates'
import type { CompletionFactCatalogVO, DesignerTaskNode } from '@/api/pms/project/project-templates'
import {
  bindingContextMapping,
  containsNativeCompletion,
  type BindingSelection,
  type EntitySource
} from '@/api/pms/project/project-templates/directBinding'
import { errorText } from './editorModel'
import { needsRequirementSource, savedRequirementSource } from '@/api/pms/project/project-templates/requirementBinding'

const props = defineProps<{ task: DesignerTaskNode; modelValue?: BindingSelection; readonly?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: BindingSelection | undefined] }>()
const canRegister = computed(() => hasPermission(['pms:business-view:manage']))
const canBind = computed(() => hasPermission(['pms:project-template:update']))
const views = ref<BusinessViewRegistrationVO[]>([])
const components = ref<BusinessViewComponentVO[]>([])
const forms = ref<DynamicFormSelectionVO[]>([])
const currentView = ref<BusinessViewRegistrationVO>()
const loading = ref(false)
const failure = ref('')
const registerOpen = ref(false)
const componentKey = ref('')
const formId = ref<BusinessViewId>()
const factCatalog = ref<CompletionFactCatalogVO[]>([])

const catalogKey = (row: BusinessViewComponentVO) => `${row.componentKey}@${row.componentVersion}`
const componentLabel = (row: BusinessViewComponentVO) => row.displayName ?? catalogKey(row)
const entityLabel = (type: string) => ({ SITE_SURVEY: '现场工勘记录', ACCEPTANCE: '验收活动', REQUIREMENT_ANALYSIS: '需求分析', DYNAMIC_FORM_INSTANCE: '业务表单' })[type] ?? '关联业务'
const ownerLabel = (owner: string) => ({ SOL: '方案与准备', PLATFORM: '平台表单' })[owner] ?? '原业务模块'
const viewLabel = (row: BusinessViewRegistrationVO) => {
  const form = forms.value.find((item) => sameBusinessViewId(item.currentPublishedRevisionId, row.dynamicFormRevisionId))
  return `${form?.templateName ?? componentLabel(row)} · ${row.viewKey} · 第${row.revisionNo}版`
}
const selectedComponent = computed(() => components.value.find((row) => catalogKey(row) === componentKey.value))
const selectedMetadata = computed(() => props.modelValue
  ? ('view' in props.modelValue ? props.modelValue.view : props.modelValue.component)
  : currentView.value)
const requirementSourceId = computed(() => props.modelValue?.requirementFormRevisionId ?? savedRequirementSource(props.task))
const chooseRequirementSource = (revisionId: BusinessViewId) => {
  if (props.readonly || !canBind.value) return
  const selection = props.modelValue ?? (currentView.value ? { view: currentView.value, strategy: source.value as EntitySource } : undefined)
  if (selection) emit('update:modelValue', { ...selection, requirementFormRevisionId: revisionId })
}
const selectedKey = computed(() => props.modelValue && 'view' in props.modelValue
  ? props.modelValue.view.id
  : props.modelValue ? undefined : currentView.value?.id)
const source = computed(() => props.modelValue?.strategy ?? String(props.task.workBinding.parameters?.instanceResolutionStrategy ?? 'REFERENCE_EXISTING'))
const currentDescription = computed(() => props.modelValue
  ? ('view' in props.modelValue ? `待保存：${viewLabel(props.modelValue.view)}` : `待保存：${componentLabel(props.modelValue.component)}`)
  : currentView.value ? `当前冻结：${viewLabel(currentView.value)}`
    : props.task.workBinding.type === 'TASK_NATIVE' ? '当前：项目任务办理' : '当前为已冻结业务绑定')

const completionOptions = computed(() => {
  const type = selectedMetadata.value?.entityType
  if (!type) return []
  return factCatalog.value.filter((fact) => fact.objectType === type)
    .map((fact) => ({ code: fact.factCode, label: fact.label, ownerContext: fact.ownerContext }))
})
const chooseCompletion = (factCode: string) => {
  if (props.modelValue && !props.readonly && completionOptions.value.some((option) => option.code === factCode))
    emit('update:modelValue', { ...props.modelValue, completion: { factCode, quantifier: 'ALL' } })
}
const changeQuantifier = (quantifier: 'ALL' | 'ANY') => {
  if (props.modelValue?.completion && !props.readonly)
    emit('update:modelValue', { ...props.modelValue, completion: { ...props.modelValue.completion, quantifier } })
}
const completionPending = computed(() => !props.modelValue?.completion && Boolean(selectedMetadata.value)
  && containsNativeCompletion(props.task.completionRule.expression as Record<string, any>))
const completionLabel = computed(() => {
  if (props.modelValue?.completion)
    return `${props.modelValue.completion.quantifier === 'ALL' ? '全部' : '至少一条'}关联记录：${completionOptions.value.find((option) => option.code === props.modelValue?.completion?.factCode)?.label || props.modelValue.completion.factCode}`
  const rule = props.task.completionRule.expression as Record<string, any>
  if (containsNativeCompletion(rule)) return '任务自身状态与必填信息'
  if (rule?.predicate === 'BUSINESS_FACT') return `${rule.parameters?.quantifier === 'ALL' ? '全部' : '至少一条'}关联记录：${completionOptions.value.find((option) => option.code === rule.parameters?.factCode)?.label || rule.parameters?.factCode}`
  return ({ ALL: '全部业务条件满足', ANY: '满足任一已配置业务条件', APPROVAL: '实际审批结果', PROCESS: '实际流程结果', DELIVERABLE: '必要交付物满足情况', TASK: '关联任务结果', STATE: '业务阶段结果' })[rule?.operator ?? rule?.predicate] ?? '已配置业务事实规则'
})

const readCurrentView = async () => {
  currentView.value = undefined
  const snapshot = props.task.workBinding.businessViewSnapshot as any
  if (snapshot?.id && snapshot?.componentKey && snapshot?.entityType) {
    currentView.value = snapshot as BusinessViewRegistrationVO
    return
  }
  const id = props.task.workBinding.parameters?.businessViewRevisionId as BusinessViewId | undefined
  if (!id) return
  try { currentView.value = await Views.getBusinessView(id) } catch { /* frozen task remains readable without live registry */ }
}
const load = async () => {
  loading.value = true; failure.value = ''
  try {
    components.value = await Views.getBusinessViewComponents()
    const registered: BusinessViewRegistrationVO[] = []
    let pageNo = 1
    while (true) {
      const page = await Views.getBusinessViewPage({ pageNo: pageNo++, pageSize: 100 })
      registered.push(...page.list)
      if (!page.list.length || registered.length >= page.total) break
    }
    views.value = registered.filter((row) => row.status === 'PUBLISHED' && !row.disabledAt && components.value.some((component) => catalogKey(component) === catalogKey(row)))
    try { factCatalog.value = await TemplateApi.getCompletionFactCatalog() } catch { factCatalog.value = [] }
    if (canRegister.value || canBind.value) {
      const selections: DynamicFormSelectionVO[] = []
      let formPage = 1
      while (true) {
        const page = await getTemplateSelection({ pageNo: formPage++, pageSize: 100 })
        selections.push(...page.list)
        if (!page.list.length || selections.length >= page.total) break
      }
      forms.value = selections
    }
    await readCurrentView()
  } catch (error) { failure.value = errorText(error) }
  finally { loading.value = false }
}
const chooseView = (id: BusinessViewId) => {
  const view = views.value.find((row) => sameBusinessViewId(row.id, id))
  if (!view || props.readonly || !canBind.value) return
  try {
    bindingContextMapping(view)
    emit('update:modelValue', { view, strategy: 'REFERENCE_EXISTING', requirementFormRevisionId: savedRequirementSource(props.task) })
    failure.value = ''
  } catch (error) { failure.value = errorText(error) }
}
const changeSource = (strategy: EntitySource) => {
  if (props.modelValue && !props.readonly && canBind.value) emit('update:modelValue', { ...props.modelValue, strategy })
}
const chooseComponent = () => {
  if (!selectedComponent.value || !canRegister.value || !canBind.value || props.readonly) return
  try {
    bindingContextMapping(selectedComponent.value)
    emit('update:modelValue', { component: selectedComponent.value, dynamicFormRevisionId: formId.value, strategy: 'REFERENCE_EXISTING' })
    registerOpen.value = false
  } catch (error) { failure.value = errorText(error) }
}
watch(() => [props.task.nodeKey, props.task.workBinding, props.task.completionRule], readCurrentView, { deep: true })
onMounted(load)
</script>
<style scoped>
.task-binding :deep(.el-select) { width: 100%; min-width: 0; }
.help { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.65; }
.entity-summary { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; padding: 12px; margin-bottom: 16px; background: var(--el-fill-color-light); border-radius: 6px; }
.entity-summary span { display: block; color: var(--el-text-color-secondary); font-size: 12px; margin-bottom: 4px; }
.entity-summary strong, .completion strong { font-size: 14px; font-weight: 500; }
.completion { padding: 12px; border-left: 3px solid var(--el-color-info-light-5); background: var(--el-fill-color-light); width: 100%; }
.completion.pending { border-color: var(--el-color-warning); background: var(--el-color-warning-light-9); }
.registration { padding: 16px; margin-top: 12px; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; }.registration h4 { margin: 0 0 16px; }
@media (max-width: 480px) { .entity-summary { grid-template-columns: 1fr; }.registration { padding: 10px; } }
</style>
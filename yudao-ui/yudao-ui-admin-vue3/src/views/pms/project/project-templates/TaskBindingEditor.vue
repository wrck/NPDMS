<template>
  <section class="task-binding" aria-label="任务办理配置">
    <el-alert v-if="failure" :title="failure" type="error" :closable="false" />
    <el-button v-if="failure" link @click="load">重新加载办理选择</el-button>
    <el-form label-position="top" :disabled="readonly || loading">
      <el-form-item label="办理页面 / 表单">
        <el-select :model-value="selectedKey" placeholder="保留当前办理方式，或选择已发布页面 / 表单" filterable :disabled="!contract || !canBind" @update:model-value="chooseView">
          <el-option v-for="view in views" :key="view.id" :value="view.id" :label="viewLabel(view)" />
          <el-option v-if="currentView && !views.some((view) => sameBusinessViewId(view.id, currentView?.id))" :value="currentView.id" :label="`${viewLabel(currentView)}（仅保留历史）`" disabled />
        </el-select>
        <p class="help">{{ currentDescription }}。办理中的提交、审批与文件操作保留在同一任务内。</p>
        <p v-if="!loading && !views.length" class="help">暂无可选的已发布办理界面。{{ canRegister ? '可在下方从真实已接入功能登记页面或已发布表单。' : '请联系有业务视图管理权限的人员登记；现有绑定不会改变。' }}</p>
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
          <p class="help">按项目上下文关联业务，不固定某个项目的实例，也不会因打开页面创建业务。</p>
        </el-form-item>
      </template>
      <el-form-item label="完成依据">
        <div class="completion" :class="{ pending: completionPending }">
          <strong>{{ completionPending ? '完成规则待对接' : completionLabel }}</strong>
          <p class="help">{{ completionPending ? '办理界面可配置并保存；现有完成依据尚不能证明该业务完成，不能发布为可运行模板。' : '保留当前已发布完成依据，由服务端结合实际业务事实校验；选择页面不授予业务权限。' }}</p>
        </div>
      </el-form-item>
      <el-button v-if="modelValue && !readonly" link @click="emit('update:modelValue', undefined)">撤销本次办理方式调整</el-button>
      <el-button v-if="canRegister && canBind && !readonly && contract" plain @click="registerOpen = !registerOpen">{{ registerOpen ? '收起登记' : '使用其他已接入页面或表单' }}</el-button>
      <section v-if="registerOpen && canRegister && !readonly" class="registration">
        <h4>选择已接入的办理功能</h4>
        <el-form-item label="页面或表单类型">
          <el-select v-model="componentKey" placeholder="选择实际已接入的功能" @update:model-value="formId = undefined">
            <el-option v-for="component in components" :key="catalogKey(component)" :value="catalogKey(component)" :label="componentLabel(component)" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="selectedComponent?.viewSource === 'DYNAMIC_FORM'" label="已发布表单">
          <el-select v-model="formId" filterable placeholder="按名称选择已发布表单">
            <el-option v-for="form in forms" :key="form.currentPublishedRevisionId" :value="form.currentPublishedRevisionId" :label="`${form.templateName} · 第${form.currentPublishedRevisionNo}版`" />
          </el-select>
        </el-form-item>
        <p class="help">只登记配置，不复制业务页面或表单内容。保存草稿时登记并发布视图，再保存任务绑定；最终仍由服务端校验权限。</p>
        <el-button :disabled="!selectedComponent || (selectedComponent.viewSource === 'DYNAMIC_FORM' && !formId)" @click="chooseComponent">用于此任务</el-button>
      </section>
      <p v-if="!canBind && !readonly" class="help">调整办理界面需要模板维护及定义发布权限；已有配置仍可查看。</p>
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
import type { TaskDef } from '@/api/pms/project/project-templates'
import { bindingContextMapping, containsNativeCompletion, loadTaskContract, type BindingSelection, type EntitySource, type TaskContract } from '@/api/pms/project/project-templates/directBinding'
import { errorText } from './editorModel'
const props = defineProps<{ task: TaskDef; modelValue?: BindingSelection; readonly?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: BindingSelection | undefined] }>()
const canRegister = computed(() => hasPermission(['pms:business-view:manage']))
const canBind = computed(() => hasPermission(['pms:project-template:update']) && hasPermission(['pms:project-template:publish']))
const views = ref<BusinessViewRegistrationVO[]>([])
const components = ref<BusinessViewComponentVO[]>([])
const forms = ref<DynamicFormSelectionVO[]>([])
const contract = ref<TaskContract>()
const currentView = ref<BusinessViewRegistrationVO>()
const loading = ref(false)
const failure = ref('')
const registerOpen = ref(false)
const componentKey = ref('')
const formId = ref<BusinessViewId>()
// Labels only: availability always comes from the actual controlled directory.
const componentNames: Record<string, string> = { 'PROJ_REQUIREMENT_ANALYSIS@1': '需求分析页面', 'PLATFORM_DYNAMIC_FORM@1': '动态表单' }
const catalogKey = (row: BusinessViewComponentVO) => `${row.componentKey}@${row.componentVersion}`
const componentLabel = (row: BusinessViewComponentVO) => componentNames[catalogKey(row)] ?? '未接入的办理功能'
const entityLabel = (type: string) => ({ REQUIREMENT_ANALYSIS: '需求分析', DYNAMIC_FORM_INSTANCE: '业务表单' }[type] ?? '关联业务')
const ownerLabel = (owner: string) => ({ SOL: '方案与准备', PLATFORM: '平台表单' }[owner] ?? '原业务模块')
const viewLabel = (row: BusinessViewRegistrationVO) => {
  const form = forms.value.find((form) => sameBusinessViewId(form.currentPublishedRevisionId, row.dynamicFormRevisionId))
  return `${form?.templateName ?? componentLabel(row)} · ${row.viewKey} · 第${row.revisionNo}版`
}
const selectedComponent = computed(() => components.value.find((row) => catalogKey(row) === componentKey.value))
const selectedMetadata = computed(() => props.modelValue ? ('view' in props.modelValue ? props.modelValue.view : props.modelValue.component) : currentView.value)
const selectedKey = computed(() => props.modelValue && 'view' in props.modelValue ? props.modelValue.view.id : props.modelValue ? undefined : currentView.value?.id)
const source = computed(() => props.modelValue?.strategy ?? contract.value?.binding.payload.instanceResolutionStrategy)
const currentDescription = computed(() => props.modelValue ? ('view' in props.modelValue ? `待保存：${viewLabel(props.modelValue.view)}` : `待保存：${componentLabel(props.modelValue.component)}`) : currentView.value ? `当前：${viewLabel(currentView.value)}` : contract.value?.binding.payload.bindingType === 'TASK_NATIVE' ? '当前：项目任务办理' : '保留当前办理配置')
const completionPending = computed(() => Boolean(selectedMetadata.value) && (!contract.value || containsNativeCompletion(contract.value.completion.payload)))
const completionLabel = computed(() => {
  if (!contract.value) return '请先选择任务方案以读取完成依据'
  const rule = contract.value.completion.payload
  if (containsNativeCompletion(rule)) return '任务自身状态与必填信息'
  return ({ ALL: '全部业务条件满足', ANY: '满足任一已配置业务条件', APPROVAL: '实际审批结果', PROCESS: '实际流程结果', DELIVERABLE: '必要交付物满足情况', TASK: '关联任务结果', STATE: '业务阶段结果' }[rule.operator ?? rule.predicate] ?? '已配置业务事实规则')
})
let generation = 0
const readContract = async () => {
  const current = ++generation
  contract.value = undefined; currentView.value = undefined
  if (!props.task.definitionRevisionId) return
  try {
    const result = await loadTaskContract(props.task)
    const view = result.binding.payload.businessViewRevisionId ? await Views.getBusinessView(result.binding.payload.businessViewRevisionId) : undefined
    if (current !== generation) return
    contract.value = result; currentView.value = view
  } catch (error) { if (current === generation) failure.value = errorText(error) }
}
const load = async () => {
  loading.value = true; failure.value = ''
  try {
    components.value = (await Views.getBusinessViewComponents()).filter((row) => componentNames[catalogKey(row)])
    const registered: BusinessViewRegistrationVO[] = []
    let pageNo = 1
    while (true) {
      const page = await Views.getBusinessViewPage({ pageNo: pageNo++, pageSize: 100 })
      registered.push(...page.list)
      if (!page.list.length || registered.length >= page.total) break
    }
    views.value = registered.filter((row) => row.status === 'PUBLISHED' && !row.disabledAt && components.value.some((component) => catalogKey(component) === catalogKey(row)))
    if (canRegister.value) {
      const selections: DynamicFormSelectionVO[] = []
      let pageNo = 1
      while (true) {
        const page = await getTemplateSelection({ pageNo: pageNo++, pageSize: 100 })
        selections.push(...page.list)
        if (!page.list.length || selections.length >= page.total) break
      }
      forms.value = selections
    }
  } catch (error) { failure.value = errorText(error) }
  finally { loading.value = false }
}
const chooseView = (id: BusinessViewId) => {
  const view = views.value.find((row) => sameBusinessViewId(row.id, id))
  if (!view || props.readonly || !canBind.value) return
  try { bindingContextMapping(view); emit('update:modelValue', { view, strategy: 'REFERENCE_EXISTING' }); failure.value = '' }
  catch (error) { failure.value = errorText(error) }
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
watch(() => [props.task.definitionRevisionId, props.task.workBindingRevisionId, props.task.permissionPolicyRevisionId, props.task.completionRuleRevisionId], readContract, { immediate: true })
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
.registration { padding: 16px; margin-top: 12px; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; }
.registration h4 { margin: 0 0 16px; }
@media (max-width: 480px) { .entity-summary { grid-template-columns: 1fr; } .registration { padding: 10px; } }
</style>

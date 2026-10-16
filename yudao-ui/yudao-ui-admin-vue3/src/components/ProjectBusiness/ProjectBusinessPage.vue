<template>
  <component :is="dialogEditor ? 'div' : ContentWrap">
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button v-if="configurationFailed" :disabled="loading" @click="loadContext">重新读取配置</el-button></el-alert>
    <el-alert v-if="pending" title="有一次业务操作结果尚未确认" type="warning" :closable="false">
      <el-button :loading="executing" @click="recoverOperation">查询操作回执</el-button>
    </el-alert>
    <el-alert v-if="receipt" :title="receiptLabels[receipt.outcome]" :type="receipt.outcome === 'FAILED' ? 'error' : 'success'" :closable="false" />
    <h3 v-if="!dialogEditor">{{ title || model?.title || '业务办理' }}</h3>
    <div v-if="!editing" class="business-actions">
      <el-select v-if="profiles.length" aria-label="展示配置" :model-value="selectedProfile" :disabled="busy || bindingParameters?.configurationName != null" @change="selectProfile"><el-option value="" label="基础配置"/><el-option v-for="profile in profiles" :key="profile.key" :label="profile.name" :value="profile.key"/></el-select>
      <ProjectBusinessFieldConfiguration v-if="!dialogEditor" ref="fieldConfiguration" :api="api" :project-id="scopeProjectId" :disabled="readonly || !configurationOperation?.executable || executing" @changed="reloadConfiguration" />
    </div>
    <slot v-if="!editing || dialogEditor || workspace" name="list" :rows="scopedRows" :current="current" :total="restrictListToInitial ? scopedRows.length : total"
      :loading="loading" :page="state.page.value" :search="(filters:any[])=>loadPage(true,filters)" :reload="()=>loadPage(true)"
      :changePage="(page:number)=>loadPage(true,undefined,undefined,page)" :create="create" :open="edit" :actionsFor="actionsForRow" :action="rowAction" :execute="runAction" :busy="busy" :is-busy="isBusy">
      <BusinessEntityList :rows="scopedRows" :readable-fields="readableFields" :create-operation="createOperation"
        :enable-sorting="true" @sort="sorts => loadPage(true, undefined, sorts)" :list-loading="loading" :slice-complete="rows.length >= total" @reload="loadPage(true)" @search="filters => loadPage(true, filters)"
        @load-more="loadPage(false)" @create="create" @open="row => edit(row.ref.entityId)" />
    </slot>
    <component v-if="editing" :is="dialogEditor ? DialogEditor : 'section'"
      v-bind="dialogEditor ? {modelValue:editing,title:contentReadonly ? `${title}详情` : current ? `编辑${title}` : `新增${title}`,width:'min(960px, 95vw)','before-close':closeEditor} : {}"
      @update:model-value="value=>{if(!value)editing=false}">
      <el-alert v-if="dialogEditor && contentReadonly" title="当前业务内容只读，已确认、驳回和归档内容不会被编辑覆盖。" type="info" :closable="false" />
      <slot name="editor-heading" :current="current" :readonly="contentReadonly" />
      <ProjectBusinessForm ref="form" :api="api" :current="current" :scope-project-id="scopeProjectId" :preferred-form-revision-id="model?.formRevisionId" :form-component="formComponent" @action="(kind:string,sn?:string)=>emit('form-action',kind,sn)" :writable-fields="writableFields" :initial-values="current?.fieldValues || scopedInitial"
        :fields="model?.fields" :appearance="formAppearance" :presentation="presentation" :readonly="contentReadonly || !!current && versioned" :disabled="executing || confirming || formLoading || !saveOperation?.executable || contentReadonly || !!current && versioned || current?.available === false" />
      <slot name="business-fields" :current="current" :execute="runAction" :busy="busy" :actions="businessActions" :readonly="contentReadonly" />
      <Teleport :to="barTarget || 'body'" :disabled="!barTarget || dialogEditor">
      <div v-if="!dialogEditor" class="business-actions">
        <el-button v-if="!contentReadonly" type="primary" :loading="executing" :disabled="busy || readonly || !!current && versioned || !saveOperation?.executable || current?.available === false" @click="save">{{saveLabel || '保存'}}</el-button>
        <el-button v-if="current && deleteOperation && !contentReadonly" type="danger" :disabled="busy || readonly || !deleteOperation.executable" @click="remove">{{deleteOperation.name || '删除'}}</el-button>
        <slot name="actions" :current="current" :api="api" :reload="reloadCurrent" :busy="busy" :execute="runAction">
          <template v-if="current"><el-button v-for="action in businessActions.filter(action=>!hiddenActions.includes(action.code) && !action.code.startsWith('revision-'))" :key="action.code" :disabled="busy || readonly || !action.executable" @click="runAction(action.code)">{{ action.name }}</el-button></template>
        </slot>
        <el-button v-if="current" :disabled="busy" @click="reloadCurrent">重新读取</el-button>
        <el-button v-if="!initialEntityId" :disabled="busy" @click="back">返回列表</el-button>
      </div>
      </Teleport>
      <slot v-if="$slots['readonly-information']" name="readonly-information" :current="current" :readonly="contentReadonly" />
      <el-descriptions v-else-if="!dialogEditor && !workspace && current && readonlyFields.length" title="只读信息" :column="2" border>
        <el-descriptions-item v-for="field in readonlyFields" :key="field.code" :label="field.name">{{ readonlyValue(field) }}</el-descriptions-item>
      </el-descriptions>
      <ProjectBusinessHistory v-if="current && versioned" ref="history" :api="api" :current="current" :fields="model?.fields || []" :actions="businessActions" :busy="executing || confirming" :readonly="readonly" :execute="runAction" />
      <ProjectBusinessDeliveries v-if="current && model?.features?.attachments" ref="deliveries" :key="String(current.ref.entityId)" :api="api" :entity-id="current.ref.entityId"
        :readonly="deliveryReadonly" :deliverable-type="deliverableType" @changed="completion?.refresh()" />
      <ProjectBusinessCompletion v-if="current" ref="completion" :api="api" :entity-id="current.ref.entityId" :version="current.concurrencyBasis" />
      <ProjectBusinessApprovals v-if="current && current.concurrencyBasis!=null && model?.features?.approvals" ref="approvals" :api="api" :entity-id="current.ref.entityId" :version="current.concurrencyBasis" :fields="model?.fields" :readonly="approvalReadonly" :before-start="approvalReady" @changed="completion?.refresh()" />
      <slot name="details" :current="current" :api="api" />
      <template v-if="dialogEditor" #footer>
        <el-button :disabled="busy" @click="closeEditor(()=>editing=false)">{{contentReadonly ? '关闭' : '取消'}}</el-button>
        <el-button v-if="!contentReadonly" type="primary" :loading="executing" :disabled="busy || !saveOperation?.executable" @click="save">保存</el-button>
      </template>
    </component>
  </component>
</template>
<script setup lang="ts">
import { computed, ref, watch, nextTick, onBeforeUnmount, defineAsyncComponent, provide, type Component } from 'vue'
import { ContentWrap } from '@/components/ContentWrap'
import type { BusinessFormAppearance } from '../BusinessEntity/businessFormAppearance'
const DialogEditor=defineAsyncComponent(()=>import('@/components/Dialog/src/Dialog.vue').then(module=>module.default))
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { useMessage } from '@/hooks/web/useMessage'
import type { BusinessEntityData, BusinessEntityFormData, FieldVO, OperationVO, ReceiptOutcome } from '@/api/pms/platform/businessmodel'
import { formatDate } from '@/utils/formatTime'
import { createProjectBusinessApi, type BusinessId,type BusinessNamedConfiguration,type BusinessPageInitialData } from '@/api/pms/platform/business'
import BusinessEntityList from '../BusinessEntity/BusinessEntityList.vue'
import ProjectBusinessForm from './ProjectBusinessForm.vue'
import ProjectBusinessHistory from './ProjectBusinessHistory.vue'
import ProjectBusinessFieldConfiguration from './ProjectBusinessFieldConfiguration.vue'
import ProjectBusinessDeliveries from './ProjectBusinessDeliveries.vue'
import ProjectBusinessCompletion from './ProjectBusinessCompletion.vue'
import ProjectBusinessApprovals from './ProjectBusinessApprovals.vue'
import {BUSINESS_ACTION_BAR_TARGET,useBusinessActionBar} from '@/components/BusinessView/businessActionBar'
import { useProjectBusiness } from './useProjectBusiness'
import { initialBusinessPage } from './initialBusinessPage'
const props = withDefaults(defineProps<{ apiBase: string; bindingParameters?: Record<string, unknown>; title?: string; workspace?:boolean;saveLabel?:string;selectInitial?:(rows:BusinessEntityData[])=>BusinessId|undefined; dialogEditor?: boolean; formComponent?: Component; formAppearance?: BusinessFormAppearance; initialValues?: Record<string,unknown>; prepareCreate?:()=>Promise<Record<string,unknown>>; beforeLeave?:()=>Promise<boolean>; readonly?: boolean; initialEntityId?: BusinessId; scopeProjectId?: BusinessId; allowedActions?: string[]; deliverableType?: string; hiddenActions?: string[]; operationAllowed?: (code:string,current?:BusinessEntityData)=>boolean; initialization?:BusinessPageInitialData }>(), { deliverableType: 'ATTACHMENT', hiddenActions:()=>[] })
const {barTarget}=useBusinessActionBar(),completion=ref<InstanceType<typeof ProjectBusinessCompletion>>()
// Nested forms in an editor dialog keep their actions inside the dialog.
provide(BUSINESS_ACTION_BAR_TARGET,computed(()=>props.dialogEditor?null:barTarget?.value))
const emit=defineEmits<{'form-action':[kind:string,sn?:string]}>()
const api = computed(() => {
  const client = createProjectBusinessApi(props.apiBase)
  return props.initialization && String(props.initialization.request.projectId) === String(props.scopeProjectId)
    ? initialBusinessPage(client, props.initialization).api : client
}), message = useMessage()
const profiles=ref<BusinessNamedConfiguration[]>([]),selectedProfile=ref<string>()
const state = useProjectBusiness(() => api.value, () => props.scopeProjectId,()=>selectedProfile.value,()=>props.bindingParameters ?? {})
const { model, current, rows, total, error, loading, executing, receipt, pending, readableFields, writableFields: allWritableFields, loadPage } = state
const receiptLabels:Record<ReceiptOutcome,string>={DELETED:'已删除',SAVED:'已保存',ACCEPTED:'已受理',APPROVAL_PENDING:'审批中',EFFECTED:'已生效',FAILED:'操作失败'}
const writableFields=computed(()=>allWritableFields.value.filter(field=>props.scopeProjectId==null || field.code!=='projectId'))
const createInitial=ref<Record<string,unknown>>()
const scopedInitial=computed(()=>({...props.initialValues,...createInitial.value,...(model.value ? state.selectors() : {}),...(props.scopeProjectId==null?{}:{projectId:props.scopeProjectId})}))
// A dialog edits one row of the project list; its initial task link is not a list filter.
const restrictListToInitial=computed(()=>props.initialEntityId!=null && !props.dialogEditor && !props.workspace)
const scopedRows=computed(()=>!restrictListToInitial.value?rows.value:current.value && String(current.value.ref.entityId)===String(props.initialEntityId)?[current.value]:rows.value.filter(row=>String(row.ref.entityId)===String(props.initialEntityId)))
const viewOnly=ref(false),contentReadonly=computed(()=>props.readonly || viewOnly.value || !!current.value && !updateOperation.value?.executable)
const editing = ref(false), confirming = ref(false), formLoading=ref(false), rowActionLoading=ref(false)
const presentation=ref<BusinessEntityFormData>(), history=ref<InstanceType<typeof ProjectBusinessHistory>>()
const versioned=computed(()=>!!model.value?.capabilities.some(capability=>capability.type==='CONTENT_HISTORY' && capability.enabled))
let presentationGeneration=0
watch(current,async row=>{
  const generation=++presentationGeneration;presentation.value=undefined;formLoading.value=false
  if(!row?.available)return
  formLoading.value=true
  try{const value=await api.value.form(row.ref.entityId);if(generation===presentationGeneration)presentation.value=value}
  catch(failure){if(generation===presentationGeneration)error.value='业务表单读取失败，请重新读取后再保存'}
  finally{if(generation===presentationGeneration)formLoading.value=false}
})
const form = ref<{buildInput:()=>Promise<Record<string,unknown>>;isBusy?:()=>boolean}>(), deliveries = ref<InstanceType<typeof ProjectBusinessDeliveries>>()
const fieldConfiguration=ref<InstanceType<typeof ProjectBusinessFieldConfiguration>>()
const approvals=ref<InstanceType<typeof ProjectBusinessApprovals>>()
const busy = computed(() => executing.value || confirming.value || rowActionLoading.value || formLoading.value || !!form.value?.isBusy?.() || !!fieldConfiguration.value?.isBusy() || !!history.value?.isBusy() || !!deliveries.value?.isBusy() || !!approvals.value?.isBusy())
// Deferred table cells must observe the live ref, not a captured slot-prop snapshot.
const isBusy = () => busy.value
const effective = (action:OperationVO|undefined,row=current.value) => action ? {...action,executable:action.executable && (props.allowedActions==null || props.allowedActions.includes(action.code)) && (!props.operationAllowed || props.operationAllowed(action.code,row))} : undefined
const createOperation = computed(() => { const action=effective(state.operation('CREATE')); return props.readonly && action ? { ...action, executable:false } : action })
// Tenant metadata is not a current-record state action. Server configuration permission is authoritative.
const configurationOperation = computed(() => {const action=state.operation('CONFIGURE');return action?{...action,executable:action.executable && (props.allowedActions==null || props.allowedActions.includes(action.code))}:undefined})
const updateOperation = computed(() => effective(state.operation('UPDATE'))), deleteOperation = computed(() => effective(state.operation('DELETE')))
// Delivery uses the same write permission, but has its own lifecycle hook. A confirmed
// document may reject body edits while still accepting its required deliverables.
const deliveryReadonly = computed(() => {
  const permission = state.operation('UPDATE')
  return props.readonly || current.value?.available !== true || !permission?.executable
    || (props.allowedActions != null && !props.allowedActions.includes(permission.code))
    || (!!props.operationAllowed && !props.operationAllowed('delivery', current.value))
})
const businessActions = computed(()=>model.value?.operations.filter(action=>action.kind==='DOMAIN_COMMAND' && action.code!=='save-form').map(action=>effective(action)!) || [])
const approvalReadonly=computed(()=>props.readonly || viewOnly.value || !state.operation('UPDATE')?.executable || props.allowedActions!=null && !props.allowedActions.includes('save'))
const approvalReady=async()=>{
  if(executing.value || formLoading.value || approvalReadonly.value)return false
  if(!contentReadonly.value && !versioned.value){try{if(Object.keys(await form.value?.buildInput()||{}).length){message.warning('请先保存业务内容，再发起审批');return false}}catch{return false}}
  return true
}
const saveOperation = computed(() => current.value ? updateOperation.value : createOperation.value)
const readonlyFields = computed(() => readableFields.value.filter(field => !field.writable && !props.formAppearance?.fields?.[field.code]?.hidden))
const readonlyValue = (field:FieldVO) => {
  const value=current.value?.fieldValues[field.code]
  if(value==null || value==='')return '-'
  if(field.type==='DATE' || field.type==='DATETIME')return formatDate(value as string|number,field.type==='DATE'?'YYYY-MM-DD':'YYYY-MM-DD HH:mm:ss')
  if(field.type==='BOOLEAN')return value?'是':'否'
  if(Array.isArray(value))return value.map(item=>typeof item==='object'?JSON.stringify(item):String(item)).join('、')
  return typeof value==='object'?JSON.stringify(value):String(value)
}
let contextGeneration=0
let finishSavedRead:(()=>void)|undefined
onBeforeUnmount(()=>{contextGeneration++;finishSavedRead?.()})
const configurationFailed=ref(false)
const loadConfiguration=async(active:number)=>{
  const configuration=await api.value.fieldConfiguration()
  if(active!==contextGeneration)return
  profiles.value=configuration.profiles||[];selectedProfile.value=typeof props.bindingParameters?.configurationName === 'string' ? props.bindingParameters.configurationName : configuration.defaultProfile
}
const reloadConfiguration=async()=>{await loadConfiguration(contextGeneration);await state.load()}
const selectProfile=async(key:string)=>{if(busy.value)return;selectedProfile.value=key;await state.load()}
const loadContext=async () => {
  const active=++contextGeneration,id=props.initialEntityId
  configurationFailed.value=false
  editing.value=false;viewOnly.value=false;createInitial.value=undefined;profiles.value=[];selectedProfile.value=undefined
  try{await loadConfiguration(active)}catch{if(active===contextGeneration){error.value='展示配置读取失败，请重新读取';configurationFailed.value=true}return}
  if(active!==contextGeneration)return
  await state.load()
  const selected=id??props.selectInitial?.(rows.value)
  if(active===contextGeneration && selected!=null && await state.open(selected) && active===contextGeneration)editing.value=!props.dialogEditor
}
watch(() => [props.apiBase,props.scopeProjectId,props.initialEntityId,props.bindingParameters],loadContext,{immediate:true})
const create = async () => {
  if (busy.value || props.readonly || !createOperation.value?.executable) return
  if (editing.value && !await requestLeave()) return
  const active=contextGeneration;confirming.value=true
  try {
    const values=props.prepareCreate ? await props.prepareCreate() : {}
    if(active!==contextGeneration || props.readonly)return
    createInitial.value=values;current.value=undefined;viewOnly.value=false;editing.value=true
  }catch(failure){error.value=failure instanceof Error?failure.message:'新建业务默认值读取失败'}
  finally{confirming.value=false}
}
const edit = async (id: BusinessId,readOnly=false) => { if(busy.value || editing.value && !await requestLeave())return;if(await state.open(id)){viewOnly.value=readOnly;editing.value=true} }
const actionsForRow=(row?:BusinessEntityData)=>(model.value?.operations||[]).map(action=>{const value=effective(action,row)!;return {...value,executable:!props.readonly && value.executable}})
// List actions require a fresh authorized row, not its asynchronous form presentation.
const confirmAndExecute=async(code:string,selected:BusinessEntityData,values:Record<string,unknown>={})=>{
  const active=contextGeneration
  const action=()=>actionsForRow(selected).find(value=>value.code===code&&value.executable)
  if(!action() || !selected.available)return
  confirming.value=true
  try {
    try{await message.confirm(code==='delete'?'确定删除当前业务记录？有关联交付件或历史引用的记录不能删除。':`确定执行“${action()!.name}”？`)}catch{return}
    if(active!==contextGeneration || current.value!==selected || props.readonly || !action())return
    const result=await state.execute(code,values);if(result?.outcome==='DELETED')editing.value=false
    return result
  }finally{confirming.value=false}
}
const rowAction=async(row:BusinessEntityData,code:string,values:Record<string,unknown>={})=>{
  if(busy.value || editing.value || !actionsForRow(row).some(action=>action.code===code&&action.executable))return
  const active=contextGeneration;rowActionLoading.value=true
  try{
    if(!await state.open(row.ref.entityId) || active!==contextGeneration || !current.value)return
    viewOnly.value=false
    return await confirmAndExecute(code,current.value,values)
  }finally{rowActionLoading.value=false}
}
const closeEditor=async(done:()=>void)=>{if(await requestLeave()){done();loadPage(true)}}
const save = async () => {
  if (busy.value || contentReadonly.value || !saveOperation.value?.executable || current.value && versioned.value) return
  const active=contextGeneration
  try {
    const input=await form.value!.buildInput()
    if(!current.value)Object.assign(input,{...scopedInitial.value,...input})
    if(!current.value && props.scopeProjectId!=null)input.projectId=props.scopeProjectId
    if(current.value && !presentation.value){message.warning('请先重新读取业务表单');return}
    const result=await state.execute(current.value ? 'save' : 'create',input)
    if(result && result.outcome!=='FAILED'){
      editing.value=true
      // A caller may navigate immediately after saving. Let the current row's
      // form and approval reads settle before the normal leave guard runs.
      await nextTick()
      while(busy.value && active===contextGeneration){
        await new Promise<void>(resolve=>{
          const finish=()=>{stop();finishSavedRead=undefined;resolve()}
          const stop=watch(busy,blocked=>{if(!blocked)finish()},{flush:'post'})
          finishSavedRead=finish
        })
        await nextTick()
      }
    }
    return active===contextGeneration ? result : undefined
  }
  catch(failure) { error.value=failure instanceof Error ? failure.message : '表单校验未通过，请检查字段内容' }
}
const runAction = async (code:string,values:Record<string,unknown> = {}) => {
  const blocked=code.startsWith('revision-') ? executing.value || confirming.value || formLoading.value || !!deliveries.value?.isBusy() : busy.value
  if(blocked || props.readonly || viewOnly.value || !current.value)return
  const action=businessActions.value.find(value=>value.code===code);if(!action?.executable)return
  const changes=versioned.value?undefined:await form.value?.buildInput();if(changes && Object.keys(changes).length){message.warning('请先保存当前修改，再执行业务操作');return}
  return confirmAndExecute(code,current.value,values)
}
const remove = async () => {
  if (busy.value || props.readonly || viewOnly.value || !current.value || !deleteOperation.value?.executable) return
  return confirmAndExecute('delete',current.value)
}
const recoverOperation = async () => { const result=await state.recover();if(result)editing.value=result.outcome!=='DELETED' && !!current.value }
const back = async () => { if(await requestLeave()){editing.value=false;loadPage(true)} }
const reloadCurrent = async () => { if(current.value && await requestLeave())await state.open(current.value.ref.entityId) }
const requestLeave = async () => {
  if(busy.value || fieldConfiguration.value && !await fieldConfiguration.value.requestLeave())return false
  if(props.beforeLeave && !await props.beforeLeave())return false
  if(history.value && !await history.value.requestLeave())return false
  if(!editing.value || props.readonly || current.value && versioned.value || !form.value)return true
  try{if(!Object.keys(await form.value.buildInput()).length)return true}catch{/* Invalid unsaved input still requires an explicit discard. */}
  confirming.value=true
  try{await message.confirm('当前业务内容尚未保存，确定离开？');return true}catch{return false}finally{confirming.value=false}
}
onBeforeRouteLeave(requestLeave);onBeforeRouteUpdate(requestLeave)
defineExpose({ requestLeave, reload:state.load, save, open:edit,current:()=>current.value })
</script>
<style scoped>.business-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px;margin:12px 0}</style>

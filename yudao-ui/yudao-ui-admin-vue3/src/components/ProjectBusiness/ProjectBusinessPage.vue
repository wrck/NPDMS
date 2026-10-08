<template>
  <component :is="dialogEditor ? 'div' : ContentWrap">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="pending" title="有一次业务操作结果尚未确认" type="warning" :closable="false">
      <el-button :loading="executing" @click="recoverOperation">查询操作回执</el-button>
    </el-alert>
    <el-alert v-if="receipt" :title="`操作结果：${receipt.outcome}`" :type="receipt.outcome === 'FAILED' ? 'error' : 'success'" :closable="false" />
    <h3 v-if="!dialogEditor">{{ title || model?.title || '业务办理' }}</h3>
    <ProjectBusinessFieldConfiguration v-if="!editing && !dialogEditor" ref="fieldConfiguration" :api="api" :disabled="readonly || !configurationOperation?.executable || executing" @changed="state.load" />
    <slot v-if="!editing || dialogEditor" name="list" :rows="scopedRows" :total="initialEntityId ? scopedRows.length : total"
      :loading="loading" :page="state.page.value" :search="(filters:any[])=>loadPage(true,filters)" :reload="()=>loadPage(true)"
      :changePage="(page:number)=>loadPage(true,undefined,undefined,page)" :create="create" :open="edit" :actionsFor="actionsForRow" :action="rowAction">
      <BusinessEntityList :rows="scopedRows" :readable-fields="readableFields" :create-operation="createOperation"
        :enable-sorting="true" @sort="sorts => loadPage(true, undefined, sorts)" :list-loading="loading" :slice-complete="rows.length >= total" @reload="loadPage(true)" @search="filters => loadPage(true, filters)"
        @load-more="loadPage(false)" @create="create" @open="row => edit(row.ref.entityId)" />
    </slot>
    <component v-if="editing" :is="dialogEditor ? DialogEditor : 'section'"
      v-bind="dialogEditor ? {modelValue:editing,title:contentReadonly ? `${title}详情` : current ? `编辑${title}` : `新增${title}`,width:'min(960px, 95vw)','before-close':closeEditor} : {}"
      @update:model-value="value=>{if(!value)editing=false}">
      <el-alert v-if="dialogEditor && contentReadonly" title="当前业务内容只读，已确认、驳回和归档内容不会被编辑覆盖。" type="info" :closable="false" />
      <component :is="formComponent || ProjectBusinessContentForm" ref="form" v-bind="formComponent ? {api,current,scopeProjectId,execute:runAction} : {}" @action="(kind:string,sn?:string)=>emit('form-action',kind,sn)" :writable-fields="writableFields" :initial-values="current?.fieldValues || scopedInitial"
        :fields="model?.fields" :appearance="formAppearance" :presentation="presentation" :disabled="busy || !saveOperation?.executable || contentReadonly || !!current && versioned || current?.available === false" />
      <slot name="business-fields" :current="current" :execute="runAction" :busy="busy" :actions="businessActions" />
      <div v-if="!dialogEditor" class="business-actions">
        <el-button type="primary" :loading="executing" :disabled="busy || readonly || !!current && versioned || !saveOperation?.executable || current?.available === false" @click="save">保存</el-button>
        <el-button v-if="current && deleteOperation" type="danger" :disabled="busy || readonly || !deleteOperation.executable" @click="remove">删除</el-button>
        <slot name="actions" :current="current" :api="api" :reload="reloadCurrent" :busy="busy" :execute="runAction">
          <template v-if="current"><el-button v-for="action in businessActions.filter(action=>!hiddenActions.includes(action.code) && !action.code.startsWith('revision-'))" :key="action.code" :disabled="busy || readonly || !action.executable" @click="runAction(action.code)">{{ action.name }}</el-button></template>
        </slot>
        <el-button v-if="current" :disabled="busy" @click="reloadCurrent">重新读取</el-button>
        <el-button v-if="!initialEntityId" :disabled="busy" @click="back">返回列表</el-button>
      </div>
      <el-descriptions v-if="!dialogEditor && current && readonlyFields.length" title="只读信息" :column="2" border>
        <el-descriptions-item v-for="field in readonlyFields" :key="field.code" :label="field.name">{{ current.fieldValues[field.code] ?? '-' }}</el-descriptions-item>
      </el-descriptions>
      <ProjectBusinessHistory v-if="current && versioned" ref="history" :api="api" :current="current" :fields="model?.fields || []" :actions="businessActions" :busy="executing || confirming" :readonly="readonly" :execute="runAction" />
      <ProjectBusinessDeliveries v-if="current" ref="deliveries" :key="String(current.ref.entityId)" :api="api" :entity-id="current.ref.entityId"
        :readonly="deliveryReadonly" :deliverable-type="deliverableType" />
      <slot name="details" :current="current" :api="api" />
      <template v-if="dialogEditor" #footer>
        <el-button :disabled="busy" @click="closeEditor(()=>editing=false)">{{contentReadonly ? '关闭' : '取消'}}</el-button>
        <el-button v-if="!contentReadonly" type="primary" :loading="executing" :disabled="busy || !saveOperation?.executable" @click="save">保存</el-button>
      </template>
    </component>
  </component>
</template>
<script setup lang="ts">
import { computed, ref, watch, defineAsyncComponent, type Component } from 'vue'
import { ContentWrap } from '@/components/ContentWrap'
import type { BusinessFormAppearance } from '../BusinessEntity/businessFormAppearance'
const DialogEditor=defineAsyncComponent(()=>import('@/components/Dialog/src/Dialog.vue').then(module=>module.default))
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { useMessage } from '@/hooks/web/useMessage'
import type { BusinessEntityData, BusinessEntityFormData, OperationVO } from '@/api/pms/platform/businessmodel'
import { createProjectBusinessApi, type BusinessId } from '@/api/pms/platform/business'
import BusinessEntityList from '../BusinessEntity/BusinessEntityList.vue'
import ProjectBusinessContentForm from './ProjectBusinessContentForm.vue'
import ProjectBusinessHistory from './ProjectBusinessHistory.vue'
import ProjectBusinessFieldConfiguration from './ProjectBusinessFieldConfiguration.vue'
import ProjectBusinessDeliveries from './ProjectBusinessDeliveries.vue'
import { useProjectBusiness } from './useProjectBusiness'
const props = withDefaults(defineProps<{ apiBase: string; title?: string; dialogEditor?: boolean; formComponent?: Component; formAppearance?: BusinessFormAppearance; initialValues?: Record<string,unknown>; prepareCreate?:()=>Promise<Record<string,unknown>>; readonly?: boolean; initialEntityId?: BusinessId; scopeProjectId?: BusinessId; allowedActions?: string[]; deliverableType?: string; hiddenActions?: string[]; operationAllowed?: (code:string,current?:BusinessEntityData)=>boolean }>(), { deliverableType: 'ATTACHMENT', hiddenActions:()=>[] })
const emit=defineEmits<{'form-action':[kind:string,sn?:string]}>()
const api = computed(() => createProjectBusinessApi(props.apiBase)), message = useMessage()
const state = useProjectBusiness(() => api.value, () => props.scopeProjectId)
const { model, current, rows, total, error, loading, executing, receipt, pending, readableFields, writableFields: allWritableFields, loadPage } = state
const writableFields=computed(()=>allWritableFields.value.filter(field=>props.scopeProjectId==null || field.code!=='projectId'))
const createInitial=ref<Record<string,unknown>>()
const scopedInitial=computed(()=>({...props.initialValues,...createInitial.value,...(props.scopeProjectId==null?{}:{projectId:props.scopeProjectId})}))
const scopedRows=computed(()=>props.initialEntityId==null?rows.value:current.value && String(current.value.ref.entityId)===String(props.initialEntityId)?[current.value]:rows.value.filter(row=>String(row.ref.entityId)===String(props.initialEntityId)))
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
const busy = computed(() => executing.value || confirming.value || rowActionLoading.value || formLoading.value || !!form.value?.isBusy?.() || !!fieldConfiguration.value?.isBusy() || !!history.value?.isBusy() || !!deliveries.value?.isBusy())
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
const saveOperation = computed(() => current.value ? updateOperation.value : createOperation.value)
const readonlyFields = computed(() => readableFields.value.filter(field => !field.writable))
let contextGeneration=0
watch(() => [props.apiBase,props.scopeProjectId,props.initialEntityId], async () => {
  const active=++contextGeneration,id=props.initialEntityId
  editing.value=false;viewOnly.value=false;createInitial.value=undefined;await state.load()
  if(active===contextGeneration && id!=null && await state.open(id) && active===contextGeneration)editing.value=!props.dialogEditor
}, { immediate:true })
const create = async () => {
  if (busy.value || props.readonly || !createOperation.value?.executable) return
  const active=contextGeneration;confirming.value=true
  try {
    const values=props.prepareCreate ? await props.prepareCreate() : {}
    if(active!==contextGeneration || props.readonly)return
    createInitial.value=values;current.value=undefined;viewOnly.value=false;editing.value=true
  }catch(failure){error.value=failure instanceof Error?failure.message:'新建业务默认值读取失败'}
  finally{confirming.value=false}
}
const edit = async (id: BusinessId,readOnly=false) => { if (!busy.value && await state.open(id)){viewOnly.value=readOnly;editing.value=true} }
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
const rowAction=async(row:BusinessEntityData,code:string)=>{
  if(busy.value || editing.value || !actionsForRow(row).some(action=>action.code===code&&action.executable))return
  const active=contextGeneration;rowActionLoading.value=true
  try{
    if(!await state.open(row.ref.entityId) || active!==contextGeneration || !current.value)return
    viewOnly.value=false
    await confirmAndExecute(code,current.value)
  }finally{rowActionLoading.value=false}
}
const closeEditor=async(done:()=>void)=>{if(await requestLeave()){done();loadPage(true)}}
const save = async () => {
  if (busy.value || contentReadonly.value || !saveOperation.value?.executable || current.value && versioned.value) return
  try {
    const input=await form.value!.buildInput()
    if(!current.value)Object.assign(input,{...scopedInitial.value,...input})
    if(!current.value && props.scopeProjectId!=null)input.projectId=props.scopeProjectId
    if(current.value && !presentation.value){message.warning('请先重新读取业务表单');return}
    const formSave=!!current.value && (!!presentation.value?.layout || !!input.$extensions || !!input.$binding || !!input.$business)
    const result=await state.execute(current.value ? formSave?'save-form':'save' : 'create',formSave?{values:input}:input)
    if(result && result.outcome!=='FAILED')editing.value=true
    return result
  }
  catch { /* Field controls retain their validation messages. */ }
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
  if(history.value && !await history.value.requestLeave())return false
  if(!editing.value || props.readonly || current.value && versioned.value || !form.value)return true
  try{if(!Object.keys(await form.value.buildInput()).length)return true}catch{/* Invalid unsaved input still requires an explicit discard. */}
  confirming.value=true
  try{await message.confirm('当前业务内容尚未保存，确定离开？');return true}catch{return false}finally{confirming.value=false}
}
onBeforeRouteLeave(requestLeave);onBeforeRouteUpdate(requestLeave)
defineExpose({ requestLeave, reload:state.load, save, current:()=>current.value })
</script>
<style scoped>.business-actions{display:flex;gap:8px;margin:12px 0}</style>

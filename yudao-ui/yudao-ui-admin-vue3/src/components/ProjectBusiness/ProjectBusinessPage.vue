<template>
  <ContentWrap>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="pending" title="有一次业务操作结果尚未确认" type="warning" :closable="false">
      <el-button :loading="executing" @click="recoverOperation">查询操作回执</el-button>
    </el-alert>
    <el-alert v-if="receipt" :title="`操作结果：${receipt.outcome}`" :type="receipt.outcome === 'FAILED' ? 'error' : 'success'" :closable="false" />
    <h3>{{ title || model?.title || '业务办理' }}</h3>
    <ProjectBusinessFieldConfiguration v-if="!editing" ref="fieldConfiguration" :api="api" :disabled="readonly || !configurationOperation?.executable || executing" @changed="state.load" />
    <BusinessEntityList v-if="!editing" :rows="rows" :readable-fields="readableFields" :create-operation="createOperation"
      :enable-sorting="true" @sort="sorts => loadPage(true, undefined, sorts)" :list-loading="loading" :slice-complete="rows.length >= total" @reload="loadPage(true)" @search="filters => loadPage(true, filters)"
      @load-more="loadPage(false)" @create="create" @open="row => edit(row.ref.entityId)" />
    <template v-else>
      <ProjectBusinessContentForm ref="form" :writable-fields="writableFields" :initial-values="current?.fieldValues"
        :fields="model?.fields" :presentation="presentation" :disabled="busy || !saveOperation?.executable || readonly || !!current && versioned || current?.available === false" />
      <slot name="business-fields" :current="current" :execute="runAction" :busy="busy" :actions="businessActions" />
      <div class="business-actions">
        <el-button type="primary" :loading="executing" :disabled="busy || readonly || !!current && versioned || !saveOperation?.executable || current?.available === false" @click="save">保存</el-button>
        <el-button v-if="current && deleteOperation" type="danger" :disabled="busy || readonly || !deleteOperation.executable" @click="remove">删除</el-button>
        <slot name="actions" :current="current" :api="api" :reload="reloadCurrent" :busy="busy" :execute="runAction">
          <template v-if="current"><el-button v-for="action in businessActions.filter(action=>!hiddenActions.includes(action.code) && !action.code.startsWith('revision-'))" :key="action.code" :disabled="busy || readonly || !action.executable" @click="runAction(action.code)">{{ action.name }}</el-button></template>
        </slot>
        <el-button v-if="current" :disabled="busy" @click="reloadCurrent">重新读取</el-button>
        <el-button :disabled="busy" @click="back">返回列表</el-button>
      </div>
      <el-descriptions v-if="current && readonlyFields.length" title="只读信息" :column="2" border>
        <el-descriptions-item v-for="field in readonlyFields" :key="field.code" :label="field.name">{{ current.fieldValues[field.code] ?? '-' }}</el-descriptions-item>
      </el-descriptions>
      <ProjectBusinessHistory v-if="current && versioned" ref="history" :api="api" :current="current" :fields="model?.fields || []" :actions="businessActions" :busy="executing || confirming" :readonly="readonly" :execute="runAction" />
      <ProjectBusinessDeliveries v-if="current" ref="deliveries" :key="String(current.ref.entityId)" :api="api" :entity-id="current.ref.entityId"
        :readonly="readonly || !updateOperation?.executable" :deliverable-type="deliverableType" />
      <slot name="details" :current="current" :api="api" />
    </template>
  </ContentWrap>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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
const props = withDefaults(defineProps<{ apiBase: string; title?: string; readonly?: boolean; deliverableType?: string; hiddenActions?: string[]; operationAllowed?: (code:string,current?:BusinessEntityData)=>boolean }>(), { deliverableType: 'ATTACHMENT', hiddenActions:()=>[] })
const api = computed(() => createProjectBusinessApi(props.apiBase)), message = useMessage()
const state = useProjectBusiness(() => api.value)
const { model, current, rows, total, error, loading, executing, receipt, pending, readableFields, writableFields, loadPage } = state
const editing = ref(false), confirming = ref(false), formLoading=ref(false)
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
const form = ref<InstanceType<typeof ProjectBusinessContentForm>>(), deliveries = ref<InstanceType<typeof ProjectBusinessDeliveries>>()
const fieldConfiguration=ref<InstanceType<typeof ProjectBusinessFieldConfiguration>>()
const busy = computed(() => executing.value || confirming.value || formLoading.value || !!fieldConfiguration.value?.isBusy() || !!history.value?.isBusy() || !!deliveries.value?.isBusy())
const effective = (action:OperationVO|undefined) => action ? {...action,executable:action.executable && (!props.operationAllowed || props.operationAllowed(action.code,current.value))} : undefined
const createOperation = computed(() => { const action=effective(state.operation('CREATE')); return props.readonly && action ? { ...action, executable:false } : action })
// Tenant metadata is not a current-record state action. Server configuration permission is authoritative.
const configurationOperation = computed(() => state.operation('CONFIGURE'))
const updateOperation = computed(() => effective(state.operation('UPDATE'))), deleteOperation = computed(() => effective(state.operation('DELETE')))
const businessActions = computed(()=>model.value?.operations.filter(action=>action.kind==='DOMAIN_COMMAND' && action.code!=='save-form').map(action=>effective(action)!) || [])
const saveOperation = computed(() => current.value ? updateOperation.value : createOperation.value)
const readonlyFields = computed(() => readableFields.value.filter(field => !field.writable))
watch(() => props.apiBase, async () => { editing.value=false; await state.load() }, { immediate:true })
const create = () => { if (busy.value || props.readonly || !createOperation.value?.executable) return; current.value=undefined;editing.value=true }
const edit = async (id: BusinessId) => { if (!busy.value && await state.open(id)) editing.value=true }
const save = async () => {
  if (busy.value || props.readonly || current.value && versioned.value) return
  try {
    const input=await form.value!.buildInput()
    if(current.value && !presentation.value){message.warning('请先重新读取业务表单');return}
    const formSave=!!current.value && (!!presentation.value?.layout || !!input.$extensions)
    const result=await state.execute(current.value ? formSave?'save-form':'save' : 'create',formSave?{values:input}:input)
    if(result && result.outcome!=='FAILED')editing.value=true
  }
  catch { /* Field controls retain their validation messages. */ }
}
const runAction = async (code:string,values:Record<string,unknown> = {}) => {
  const blocked=code.startsWith('revision-') ? executing.value || confirming.value || formLoading.value || !!deliveries.value?.isBusy() : busy.value
  if(blocked || props.readonly || !current.value)return
  const action=businessActions.value.find(value=>value.code===code);if(!action?.executable)return
  const changes=versioned.value?undefined:await form.value?.buildInput();if(changes && Object.keys(changes).length){message.warning('请先保存当前修改，再执行业务操作');return}
  const selected=current.value;confirming.value=true
  try {
    try{await message.confirm(`确定执行“${action.name}”？`)}catch{return}
    if(current.value!==selected || props.readonly)return
    const result=await state.execute(code,values);if(result?.outcome==='DELETED')editing.value=false
    return result
  }finally{confirming.value=false}
}
const remove = async () => {
  if (busy.value || props.readonly || !current.value || !deleteOperation.value?.executable) return
  const selected=current.value;confirming.value=true
  try {
    try { await message.confirm('确定删除当前业务记录？有关联交付件或历史引用的记录不能删除。') } catch { return }
    if(current.value!==selected || props.readonly)return
    const result=await state.execute('delete');if(result?.outcome==='DELETED')editing.value=false
  } finally {confirming.value=false}
}
const recoverOperation = async () => { const result=await state.recover();if(result)editing.value=result.outcome!=='DELETED' && !!current.value }
const back = async () => { if(await requestLeave() && (!history.value || await history.value.requestLeave())){editing.value=false;loadPage(true)} }
const reloadCurrent = async () => { if(current.value && await requestLeave() && (!history.value || await history.value.requestLeave()))await state.open(current.value.ref.entityId) }
const requestLeave = async () => {
  if(busy.value || fieldConfiguration.value && !await fieldConfiguration.value.requestLeave())return false
  if(!editing.value || props.readonly || current.value && versioned.value || !form.value)return true
  try{if(!Object.keys(await form.value.buildInput()).length)return true}catch{/* Invalid unsaved input still requires an explicit discard. */}
  confirming.value=true
  try{await message.confirm('当前业务内容尚未保存，确定离开？');return true}catch{return false}finally{confirming.value=false}
}
onBeforeRouteLeave(requestLeave);onBeforeRouteUpdate(requestLeave)
defineExpose({ requestLeave, reload:state.load })
</script>
<style scoped>.business-actions{display:flex;gap:8px;margin:12px 0}</style>

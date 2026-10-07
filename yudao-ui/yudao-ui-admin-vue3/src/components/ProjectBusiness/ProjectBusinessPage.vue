<template>
  <ContentWrap>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="pending" title="有一次业务操作结果尚未确认" type="warning" :closable="false">
      <el-button :loading="executing" @click="recoverOperation">查询操作回执</el-button>
    </el-alert>
    <el-alert v-if="receipt" :title="`操作结果：${receipt.outcome}`" :type="receipt.outcome === 'FAILED' ? 'error' : 'success'" :closable="false" />
    <h3>{{ title || model?.title || '业务办理' }}</h3>
    <BusinessEntityList v-if="!editing" :rows="rows" :readable-fields="readableFields" :create-operation="createOperation"
      :list-loading="loading" :slice-complete="rows.length >= total" @reload="loadPage(true)" @search="filters => loadPage(true, filters)"
      @load-more="loadPage(false)" @create="create" @open="row => edit(row.ref.entityId)" />
    <template v-else>
      <BusinessEntityForm ref="form" :writable-fields="writableFields" :initial-values="current?.fieldValues"
        :fields="model?.fields" lossless-numbers :disabled="busy || !saveOperation?.executable || readonly || current?.available === false" />
      <div class="business-actions">
        <el-button type="primary" :loading="executing" :disabled="busy || readonly || !saveOperation?.executable || current?.available === false" @click="save">保存</el-button>
        <el-button v-if="current && deleteOperation" type="danger" :disabled="busy || readonly || !deleteOperation.executable" @click="remove">删除</el-button>
        <slot name="actions" :current="current" :api="api" :reload="reloadCurrent" :busy="busy" :execute="runAction">
          <template v-if="current"><el-button v-for="action in businessActions" :key="action.code" :disabled="busy || readonly || !action.executable" @click="runAction(action.code)">{{ action.name }}</el-button></template>
        </slot>
        <el-button v-if="current" :disabled="busy" @click="reloadCurrent">重新读取</el-button>
        <el-button :disabled="busy" @click="back">返回列表</el-button>
      </div>
      <el-descriptions v-if="current && readonlyFields.length" title="只读信息" :column="2" border>
        <el-descriptions-item v-for="field in readonlyFields" :key="field.code" :label="field.name">{{ current.fieldValues[field.code] ?? '-' }}</el-descriptions-item>
      </el-descriptions>
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
import { createProjectBusinessApi, type BusinessId } from '@/api/pms/platform/business'
import BusinessEntityList from '../BusinessEntity/BusinessEntityList.vue'
import BusinessEntityForm from '../BusinessEntity/BusinessEntityForm.vue'
import ProjectBusinessDeliveries from './ProjectBusinessDeliveries.vue'
import { useProjectBusiness } from './useProjectBusiness'
const props = withDefaults(defineProps<{ apiBase: string; title?: string; readonly?: boolean; deliverableType?: string }>(), { deliverableType: 'ATTACHMENT' })
const api = computed(() => createProjectBusinessApi(props.apiBase)), message = useMessage()
const state = useProjectBusiness(() => api.value)
const { model, current, rows, total, error, loading, executing, receipt, pending, readableFields, writableFields, loadPage } = state
const editing = ref(false), confirming = ref(false)
const form = ref<InstanceType<typeof BusinessEntityForm>>(), deliveries = ref<InstanceType<typeof ProjectBusinessDeliveries>>()
const busy = computed(() => executing.value || confirming.value || !!deliveries.value?.isBusy())
const createOperation = computed(() => { const action=state.operation('CREATE'); return props.readonly && action ? { ...action, executable:false } : action })
const updateOperation = computed(() => state.operation('UPDATE')), deleteOperation = computed(() => state.operation('DELETE'))
const businessActions = computed(()=>model.value?.operations.filter(action=>action.kind==='DOMAIN_COMMAND') || [])
const saveOperation = computed(() => current.value ? updateOperation.value : createOperation.value)
const readonlyFields = computed(() => readableFields.value.filter(field => !field.writable))
watch(() => props.apiBase, async () => { editing.value=false; await state.load() }, { immediate:true })
const create = () => { if (busy.value || props.readonly || !createOperation.value?.executable) return; current.value=undefined;editing.value=true }
const edit = async (id: BusinessId) => { if (!busy.value && await state.open(id)) editing.value=true }
const save = async () => {
  if (busy.value || props.readonly) return
  try { const input=await form.value!.buildInput(); const result=await state.execute(current.value ? 'save' : 'create',input); if(result && result.outcome!=='FAILED')editing.value=true }
  catch { /* Field controls retain their validation messages. */ }
}
const runAction = async (code:string,values:Record<string,unknown> = {}) => {
  if(busy.value || props.readonly || !current.value)return
  const action=businessActions.value.find(value=>value.code===code);if(!action?.executable)return
  const changes=await form.value?.buildInput();if(changes && Object.keys(changes).length){message.warning('请先保存当前修改，再执行业务操作');return}
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
const back = () => { if(!busy.value){editing.value=false;loadPage(true)} }
const reloadCurrent = async () => { if(current.value)await state.open(current.value.ref.entityId) }
const requestLeave = () => !busy.value
onBeforeRouteLeave(requestLeave);onBeforeRouteUpdate(requestLeave)
defineExpose({ requestLeave, reload:state.load })
</script>
<style scoped>.business-actions{display:flex;gap:8px;margin:12px 0}</style>

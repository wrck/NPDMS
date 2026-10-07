<template>
  <el-card class="project-business-history" shadow="never">
    <template #header>内容版本</template>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-input v-model="reason" aria-label="修订原因" placeholder="修订原因" :disabled="blocked" />
    <el-button :disabled="blocked || !allowed('revision-create') || rows.some(row=>row.state==='DRAFT')" @click="perform('revision-create')">发起修订</el-button>
    <el-button :disabled="blocked" @click="refresh">刷新修订</el-button>
    <el-table :data="rows" row-key="ref.revisionId" border>
      <el-table-column prop="revisionNo" label="修订号" width="85" />
      <el-table-column prop="state" label="状态" width="100" />
      <el-table-column prop="reason" label="原因" />
      <el-table-column label="生效" width="80"><template #default="{row}">{{row.effective?'是':'否'}}</template></el-table-column>
      <el-table-column label="操作" width="80"><template #default="{row}"><el-button :disabled="blocked" @click="choose(row)">查看</el-button></template></el-table-column>
    </el-table>
    <template v-if="selected && values && presentation">
      <h4>修订 #{{selected.revisionNo}} · {{selected.state==='DRAFT'?'草稿':'冻结只读'}}</h4>
      <ProjectBusinessContentForm ref="editor" :fields="fields" :writable-fields="writableFields" :presentation="presentation" :initial-values="values"
        :disabled="blocked || readonly || selected.state!=='DRAFT' || !allowed('revision-save')" />
      <el-button v-if="selected.state==='DRAFT'" :disabled="blocked || !allowed('revision-save')" @click="perform('revision-save')">保存修订</el-button>
      <el-button v-if="selected.state==='DRAFT'" :disabled="blocked || !allowed('revision-complete')" @click="perform('revision-complete')">冻结并生效</el-button>
      <el-button v-if="selected.state==='DRAFT'" :disabled="blocked || !allowed('revision-discard')" @click="perform('revision-discard')">放弃修订</el-button>
      <el-button v-if="selected.state==='FROZEN'" :disabled="blocked || rows.some(row=>row.state==='DRAFT') || !allowed('revision-create')" @click="perform('revision-create',selected.ref.revisionId)">从此版本复制</el-button>
    </template>
    <el-form inline class="mt-12px">
      <el-form-item label="比较版本"><el-select v-model="left" aria-label="比较左版本" :disabled="blocked"><el-option v-for="row in rows" :key="String(row.ref.revisionId)" :label="`#${row.revisionNo}`" :value="String(row.ref.revisionId)" /></el-select></el-form-item>
      <el-form-item><el-select v-model="right" aria-label="比较右版本" :disabled="blocked"><el-option v-for="row in rows" :key="String(row.ref.revisionId)" :label="`#${row.revisionNo}`" :value="String(row.ref.revisionId)" /></el-select></el-form-item>
      <el-button :disabled="blocked || !left || !right" @click="compare">比较</el-button>
    </el-form>
    <el-table v-if="differences" :data="differences" border><el-table-column prop="fieldCode" label="字段"/><el-table-column label="之前"><template #default="{row}">{{format(row.before)}}</template></el-table-column><el-table-column label="之后"><template #default="{row}">{{format(row.after)}}</template></el-table-column></el-table>
  </el-card>
</template>
<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router'
import {useMessage} from '@/hooks/web/useMessage'
import type {BusinessEntityData,BusinessEntityFormData,BusinessOperationReceipt,FieldVO,OperationVO} from '@/api/pms/platform/businessmodel'
import type {BusinessId,DirectBusinessRevision,ProjectBusinessApi,RevisionFieldValue} from '@/api/pms/platform/business'
import ProjectBusinessContentForm from './ProjectBusinessContentForm.vue'
const props=defineProps<{api:ProjectBusinessApi;current:BusinessEntityData;fields:FieldVO[];actions:OperationVO[];busy:boolean;readonly?:boolean;execute:(code:string,values:Record<string,unknown>)=>Promise<BusinessOperationReceipt|undefined>}>()
const message=useMessage(),rows=ref<DirectBusinessRevision[]>([]),selected=ref<DirectBusinessRevision>(),values=ref<Record<string,unknown>>(),presentation=ref<BusinessEntityFormData>(),editor=ref<InstanceType<typeof ProjectBusinessContentForm>>()
const loading=ref(false),working=ref(false),error=ref(''),reason=ref(''),left=ref(''),right=ref('')
const differences=ref<Array<{fieldCode:string;before:RevisionFieldValue;after:RevisionFieldValue}>>()
let generation=0
const writableFields=computed(()=>props.fields.filter(field=>field.writable))
const blocked=computed(()=>props.busy||loading.value||working.value)
const allowed=(code:string)=>!props.readonly && props.actions.some(action=>action.code===code&&action.executable)
const format=(value:RevisionFieldValue)=>value.readable?JSON.stringify(value.value):'不可读'
const discardChanges=async()=>{
  if(selected.value?.state!=='DRAFT' || !editor.value)return true
  try{const input=await editor.value.buildInput();if(!Object.keys(input).length)return true}catch{/* Invalid unsaved fields still allow an explicit discard confirmation. */}
  try{await message.confirm('当前修订尚未保存，确定离开？');return true}catch{return false}
}
const load=async(preferred?:BusinessId)=>{
  const active=++generation,api=props.api,id=props.current.ref.entityId;loading.value=true;error.value=''
  try{
    const list=await api.revisions(id);if(active!==generation)return
    rows.value=list
    const next=list.find(row=>String(row.ref.revisionId)===String(preferred??selected.value?.ref.revisionId))||list.find(row=>row.state==='DRAFT')||list[0]
    selected.value=next;values.value=undefined;presentation.value=undefined
    if(next){const [data,form]=await Promise.all([api.revisionValues(id,next.ref.revisionId),api.revisionForm(id,next.ref.revisionId)]);if(active!==generation)return
      values.value=Object.fromEntries(Object.entries(data).filter(([,field])=>field.readable).map(([key,field])=>[key,field.value]));presentation.value=form}
  }catch(failure){if(active===generation)error.value=failure instanceof Error?failure.message:'修订读取失败'}
  finally{if(active===generation)loading.value=false}
}
let identity=''
watch(()=>[props.api.base,String(props.current.ref.entityId),props.current.concurrencyBasis],()=>{
  const next=`${props.api.base}:${props.current.ref.entityId}`
  if(next!==identity){identity=next;selected.value=undefined;values.value=undefined;presentation.value=undefined;left.value='';right.value='';differences.value=undefined;reason.value=''}
  load()
},{immediate:true})
const refresh=async()=>{if(!blocked.value && await discardChanges())await load()}
const choose=async(row:DirectBusinessRevision)=>{if(blocked.value || !await discardChanges())return;await load(row.ref.revisionId)}
const perform=async(code:string,source?:BusinessId)=>{
  if(blocked.value || !allowed(code))return
  working.value=true;error.value=''
  try{
    const input=selected.value?.state==='DRAFT'?await editor.value?.buildInput()||{}:{}
    if(code==='revision-complete' && Object.keys(input).length){message.warning('请先保存修订修改');return}
    const payload:Record<string,unknown>=code==='revision-create'?{reason:reason.value,sourceRevisionId:source}:{revisionId:selected.value?.ref.revisionId,revisionVersion:selected.value?.version,...(code==='revision-save'?{values:input}:{})}
    const result=await props.execute(code,payload)
    if(result && result.outcome!=='FAILED')await load(code==='revision-discard'?undefined:result.references?.find(ref=>ref.kind==='RESULT')?.value)
  }catch(failure){error.value=failure instanceof Error?failure.message:'修订操作失败'}finally{working.value=false}
}
const compare=async()=>{
  if(blocked.value || !left.value || !right.value)return
  const active=++generation;loading.value=true;error.value=''
  try{const result=await props.api.compareRevisions(props.current.ref.entityId,left.value,right.value);if(active===generation)differences.value=result}
  catch(failure){if(active===generation)error.value=failure instanceof Error?failure.message:'版本比较失败'}finally{if(active===generation)loading.value=false}
}
const leave=()=>blocked.value?false:discardChanges();onBeforeRouteLeave(leave);onBeforeRouteUpdate(leave)
defineExpose({isBusy:()=>loading.value||working.value,requestLeave:leave})
</script>

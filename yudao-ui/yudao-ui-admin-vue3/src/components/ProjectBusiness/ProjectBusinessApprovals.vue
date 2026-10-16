<template>
  <section class="business-approvals">
    <div class="approval-heading"><h3>业务审批</h3><el-button :disabled="busy" @click="load">刷新</el-button><el-button v-if="!readonly" type="primary" :disabled="busy || attempts.some(row=>row.status==='PENDING')" @click="openStart">发起审批</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table :data="attempts" v-loading="loading" border empty-text="暂无审批记录">
      <el-table-column label="提交时间" min-width="170"><template #default="{row}">{{formatDate(row.submittedAt)}}</template></el-table-column>
      <el-table-column label="审批状态" min-width="160"><template #default="{row}"><el-tag>{{statusNames[row.status]}}</el-tag><span v-if="!row.currentContent" class="basis-warning">内容已更新</span></template></el-table-column>
      <el-table-column label="提交版本" prop="submittedVersion" width="100" />
      <el-table-column label="操作" min-width="180"><template #default="{row}">
        <el-button link type="primary" :disabled="busy" @click="openDetail(row)">审批详情</el-button>
        <el-button v-if="!readonly && row.status==='PENDING' && row.creator===String(userId)" link type="danger" :disabled="busy" @click="cancel(row)">撤回</el-button>
      </template></el-table-column>
    </el-table>
    <Dialog v-model="starting" title="发起业务审批" width="min(900px, 95vw)" :before-close="closeStart">
      <el-alert v-if="startError" :title="startError" type="error" :closable="false" />
      <el-form label-width="100px" :disabled="sending || !!pending">
        <el-form-item label="审批流程" required><el-select v-model="definitionId" filterable placeholder="选择已发布流程" class="w-full" @change="selectDefinition"><el-option v-for="definition in definitions" :key="definition.id" :value="definition.id" :label="`${definition.name}（V${definition.version}）`" :disabled="!definition.available"><span>{{definition.name}}</span><small v-if="!definition.available"> · {{definition.unavailableReason}}</small></el-option></el-select></el-form-item>
      </el-form>
      <form-create v-if="definitionId && form.rule.length" :key="definitionId" :rule="form.rule" :option="form.option" v-model:api="formApi" v-model="form.value" />
      <el-form v-if="preview" label-width="120px" :disabled="sending || !!pending">
        <el-form-item v-for="node in preview.nodes.filter(node=>node.selectApprovers)" :key="node.id" :label="node.name" required><el-select v-model="selectedApprovers[node.id]" multiple filterable placeholder="选择审批人" class="w-full"><el-option v-for="user in users" :key="user.id" :value="user.id" :label="user.nickname" /></el-select></el-form-item>
      </el-form>
      <p v-if="preview" class="approval-route">流程节点：{{preview.nodes.map(node=>node.name).join(' → ')}}</p>
      <template #footer><el-button :disabled="sending || !!pending" @click="closeStart(()=>starting=false)">取消</el-button><el-button type="primary" :loading="sending" :disabled="!definitionId || previewLoading || !preview || readonly" @click="submit">{{pending ? '重试并确认发起结果' : '发起审批'}}</el-button></template>
    </Dialog>
    <Dialog v-model="showDetail" title="审批详情" width="min(960px, 95vw)">
      <template v-if="detail">
        <el-descriptions :column="2" border><el-descriptions-item label="流程">{{detail.process.name}}</el-descriptions-item><el-descriptions-item label="状态">{{statusNames[detail.attempt.status]}}</el-descriptions-item><el-descriptions-item label="提交版本">{{detail.attempt.submittedVersion}}</el-descriptions-item><el-descriptions-item label="结论时间">{{formatDate(detail.attempt.decidedAt) || '-'}}</el-descriptions-item></el-descriptions>
        <el-alert v-if="!detail.attempt.currentContent" title="业务内容已更新，此审批对应提交时的版本。" type="warning" :closable="false" class="mt-12px" />
        <h4>提交时的业务内容</h4><el-descriptions :column="2" border><el-descriptions-item v-for="field in snapshotFields" :key="field.code" :label="field.name">{{display(detail.process.submittedValues[field.code])}}</el-descriptions-item></el-descriptions>
        <el-descriptions v-if="snapshotExtensions.length" title="扩展内容" :column="2" border class="mt-12px"><el-descriptions-item v-for="field in snapshotExtensions" :key="field.code" :label="field.label">{{display(field.value)}}</el-descriptions-item></el-descriptions>
        <h4>办理意见</h4><el-table :data="detail.process.opinions" border empty-text="暂无办理意见"><el-table-column label="时间" min-width="170"><template #default="{row}">{{formatDate(row.time)}}</template></el-table-column><el-table-column prop="userId" label="办理人" width="100"/><el-table-column prop="comment" label="意见" min-width="240"/></el-table>
      </template>
      <template #footer><el-button @click="showDetail=false">关闭</el-button><el-button v-if="detail" type="primary" @click="openProcess">查看流程及办理</el-button></template>
    </Dialog>
  </section>
</template>
<script setup lang="ts">
import {computed,nextTick,onBeforeUnmount,ref,watch} from 'vue'
import {useRouter} from 'vue-router'
import type {Api as FormCreateApi} from '@form-create/element-ui'
import {Dialog} from '@/components/Dialog'
import {ElMessageBox} from 'element-plus'
import {getCurrentUserId} from '@/utils/auth'
import {getSimpleUserList,type UserVO} from '@/api/system/user'
import {setConfAndFields2} from '@/utils/formCreate'
import {generateUUID} from '@/utils'
import {formatDate} from '@/utils/formatTime'
import type {BusinessId,ProjectBusinessApi,BusinessApprovalAttempt,BusinessApprovalDefinition,BusinessApprovalPreview,BusinessApprovalStart,BusinessApprovalDetail} from '@/api/pms/platform/business'
const props=defineProps<{api:ProjectBusinessApi;entityId:BusinessId;version:number;fields?:Array<{code:string;name:string;readable:boolean}>;readonly?:boolean;beforeStart?:()=>Promise<boolean>}>()
const emit=defineEmits<{changed:[]}>(),router=useRouter(),userId=computed(()=>getCurrentUserId())
const statusNames:Record<string,string>={PENDING:'审批中',APPROVED:'已通过',REJECTED:'已驳回',WITHDRAWN:'已撤回'}
const attempts=ref<BusinessApprovalAttempt[]>([]),loading=ref(false),error=ref(''),sending=ref(false),starting=ref(false),startError=ref('')
const definitions=ref<BusinessApprovalDefinition[]>([]),definitionId=ref(''),preview=ref<BusinessApprovalPreview>(),previewLoading=ref(false),users=ref<UserVO[]>([])
const selectedApprovers=ref<Record<string,BusinessId[]>>({}),form=ref<any>({rule:[],option:{},value:{}}),formApi=ref<FormCreateApi>(),pending=ref<BusinessApprovalStart>()
const showDetail=ref(false),detail=ref<BusinessApprovalDetail>(),detailLoading=ref(false)
const busy=computed(()=>loading.value || sending.value || starting.value || detailLoading.value)
let generation=0,previewGeneration=0,openedVersion=0,previewTimer:ReturnType<typeof setTimeout>|undefined
const load=async()=>{const active=generation;loading.value=true;error.value='';try{const rows=await props.api.approvals(props.entityId);if(active===generation)attempts.value=rows}catch(failure){if(active===generation)error.value=failure instanceof Error?failure.message:'审批记录读取失败'}finally{if(active===generation)loading.value=false}}
watch(()=>[props.api.base,props.entityId],()=>{generation++;previewGeneration++;clearTimeout(previewTimer);loading.value=false;sending.value=false;detailLoading.value=false;previewLoading.value=false;starting.value=false;pending.value=undefined;detail.value=undefined;showDetail.value=false;definitionId.value='';definitions.value=[];preview.value=undefined;attempts.value=[];load()},{immediate:true})
watch(()=>props.version,()=>load())
const openStart=async()=>{
  if(props.readonly || busy.value || props.beforeStart && !await props.beforeStart())return
  const active=generation;loading.value=true;error.value=''
  try{const [flows,people]=await Promise.all([props.api.approvalDefinitions(props.entityId),getSimpleUserList()]);if(active!==generation || props.readonly)return
    definitions.value=flows;users.value=people;definitionId.value='';preview.value=undefined;selectedApprovers.value={};form.value={rule:[],option:{},value:{}};startError.value='';openedVersion=props.version;starting.value=true
  }catch(failure){if(active===generation)error.value=failure instanceof Error?failure.message:'审批流程读取失败'}finally{if(active===generation)loading.value=false}
}
const refreshPreview=async()=>{
  const active=generation,round=++previewGeneration,selected=definitionId.value;if(!selected)return
  previewLoading.value=true;startError.value=''
  try{const result=await props.api.approvalPreview(props.entityId,selected,form.value.value);if(active!==generation || round!==previewGeneration)return;preview.value=result
    const current=selectedApprovers.value;selectedApprovers.value=Object.fromEntries(result.nodes.filter(node=>node.selectApprovers).map(node=>[node.id,current[node.id]||[]]))
    for(const [field,permission] of Object.entries(result.fieldPermissions)){
      formApi.value?.disabled(permission!=='2',field);formApi.value?.hidden(permission==='3',field)
      if(permission!=='2'){const rule=formApi.value?.getRule(field);if(rule){rule.$required=false;rule.validate=[]}}
    }
  }catch(failure){if(active===generation && round===previewGeneration){preview.value=undefined;startError.value=failure instanceof Error?failure.message:'审批节点读取失败'}}finally{if(active===generation && round===previewGeneration)previewLoading.value=false}
}
const selectDefinition=async()=>{
  previewGeneration++;preview.value=undefined;selectedApprovers.value={};form.value={rule:[],option:{},value:{}}
  const selected=definitions.value.find(row=>row.id===definitionId.value);if(!selected?.available)return
  setConfAndFields2(form,selected.formConf||'{}',selected.formFields||[]);form.value.option.submitBtn=false;form.value.option.resetBtn=false
  await nextTick();await refreshPreview()
}
watch(()=>form.value.value,()=>{if(!starting.value || pending.value)return;clearTimeout(previewTimer);previewTimer=setTimeout(refreshPreview,300)},{deep:true})
const submit=async()=>{
  if(props.readonly || sending.value || !preview.value || previewLoading.value)return
  const active=generation
  if(!pending.value){
    if(openedVersion!==props.version){startError.value='业务版本已更新，请关闭后重新发起';return}
    if(props.beforeStart && !await props.beforeStart())return
    try{await formApi.value?.validate()}catch{return}
    if(preview.value.nodes.some(node=>node.selectApprovers && !selectedApprovers.value[node.id]?.length)){startError.value='请补全审批人';return}
    pending.value=JSON.parse(JSON.stringify({definitionId:definitionId.value,version:openedVersion,idempotencyKey:generateUUID(),variables:form.value.value,selectedApprovers:selectedApprovers.value}))
  }
  sending.value=true;startError.value=''
  try{await props.api.startApproval(props.entityId,pending.value!);if(active!==generation)return;pending.value=undefined;starting.value=false;await load();emit('changed')}
  catch(failure){if(active===generation){if(failure==='error')pending.value=undefined;startError.value=pending.value?'发起结果尚未确认，请重试同一次提交。':failure instanceof Error?failure.message:'审批未发起，请检查流程配置与权限'}}
  finally{if(active===generation)sending.value=false}
}
const closeStart=(done:()=>void)=>{if(sending.value || pending.value)return;previewGeneration++;clearTimeout(previewTimer);done()}
const cancel=async(row:BusinessApprovalAttempt)=>{
  if(props.readonly || busy.value)return;const active=generation;detailLoading.value=true
  try{const result=await ElMessageBox.prompt('请输入撤回原因','撤回审批',{inputValidator:(value:string)=>!!value?.trim()||'请填写撤回原因'});if(active!==generation || props.readonly)return;await props.api.cancelApproval(props.entityId,row.id,result.value);if(active===generation){await load();emit('changed')}}
  catch(failure){if(active===generation && failure instanceof Error)error.value=failure.message}finally{if(active===generation)detailLoading.value=false}
}
const openDetail=async(row:BusinessApprovalAttempt)=>{if(busy.value)return;const active=generation;detailLoading.value=true;try{const value=await props.api.approval(props.entityId,row.id);if(active===generation){detail.value=value;showDetail.value=true}}catch(failure){if(active===generation)error.value=failure instanceof Error?failure.message:'审批详情读取失败'}finally{if(active===generation)detailLoading.value=false}}
const snapshotFields=computed(()=>(props.fields||[]).filter(field=>field.readable && Object.hasOwn(detail.value?.process.submittedValues||{},field.code)))
const snapshotExtensions=computed(()=>{const values=detail.value?.process.submittedValues||{},fields=values.$extensions as Record<string,unknown>|undefined,definitions=values.$extensionDefinitions as Array<{code:string;label:string}>|undefined;return (definitions||[]).map(field=>({...field,value:fields?.[field.code]}))})
const display=(value:unknown)=>value==null?'-':typeof value==='object'?JSON.stringify(value):String(value)
const openProcess=()=>{if(detail.value)router.push({name:'BpmProcessInstanceDetail',query:{id:detail.value.attempt.instanceId}})}
onBeforeUnmount(()=>{generation++;previewGeneration++;clearTimeout(previewTimer)})
defineExpose({isBusy:()=>busy.value,requestLeave:async()=>!busy.value,refresh:load})
</script>
<style scoped>.business-approvals{margin-top:20px}.approval-heading{display:flex;align-items:center;gap:8px;margin-bottom:8px}.approval-heading h3{margin-right:auto}.basis-warning{margin-left:8px;color:var(--el-color-warning)}.approval-route{color:var(--el-text-color-secondary);line-height:1.7}</style>

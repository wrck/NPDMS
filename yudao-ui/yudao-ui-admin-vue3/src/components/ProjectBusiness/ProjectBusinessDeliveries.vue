<template>
  <section class="project-business-deliveries" :class="{embedded}" aria-label="附件">
    <header class="attachment-toolbar">
      <div class="attachment-heading"><h3 v-if="!embedded">附件</h3><span v-else>{{total}} 个文件</span><span v-if="loaded && !embedded" class="attachment-count">{{total}} 个文件</span></div>
      <div class="attachment-actions">
        <el-button link :disabled="mutating" @click="historyPanel?.open()">上传记录</el-button>
        <el-button link :disabled="mutating || fetching" @click="refresh">刷新</el-button>
        <el-button v-if="!readonly" type="primary" :loading="mutating && !!pending" :disabled="mutating || fetching || !loaded || !scope" @click="fileInput?.click()"><Icon icon="ep:upload" class="mr-1" />上传文件</el-button>
      </div>
    </header>
    <input v-if="!readonly" ref="fileInput" class="file-input" type="file" tabindex="-1" aria-label="选择附件" :disabled="mutating || !scope" @change="selectFile" />
    <el-alert v-if="error || contextError" :title="error || contextError" type="error" show-icon :closable="false" />
    <div v-if="pending" class="pending-upload" role="status" aria-live="polite">
      <Icon icon="ep:document" /><span class="pending-name">{{pending.file.name}}</span><span>{{mutating ? '正在上传…' : '上传结果未确认'}}</span>
      <el-button v-if="!readonly && !mutating" link type="primary" @click="upload">重试上传</el-button>
    </div>
    <el-skeleton v-if="fetching && !rows.length" :rows="2" animated />
    <ul v-else-if="rows.length" class="attachment-list" :aria-busy="fetching || mutating">
      <li v-for="row in rows" :key="row.id" class="attachment-row">
        <Icon icon="ep:document" class="file-icon" aria-hidden="true" />
        <div class="file-info">
          <el-input v-if="editingId===row.id && !readonly" v-model="titles[row.id]" aria-label="附件名称" :disabled="mutating" maxlength="255" @keyup.enter="edit(row)" @keyup.esc="editingId=undefined" />
          <span v-else class="file-name" :title="row.title || row.fileName">{{row.title || row.fileName}}</span>
          <div class="file-meta"><span v-if="row.title && row.title!==row.fileName" :title="row.fileName">{{row.fileName}}</span><time>{{formatDate(row.uploadedAt, 'YYYY-MM-DD HH:mm')}}</time></div>
        </div>
        <div class="file-actions">
          <template v-if="editingId===row.id && !readonly"><el-button link type="primary" :disabled="mutating || !titles[row.id]?.trim()" @click="edit(row)">保存名称</el-button><el-button link :disabled="mutating" @click="editingId=undefined">取消</el-button></template>
          <template v-else>
            <el-button link :disabled="row.status!=='ACTIVE'" @click="download(row)">下载</el-button>
            <template v-if="!readonly && row.editable!==false"><el-button link :disabled="mutating" @click="titles[row.id]=row.title;editingId=row.id">重命名</el-button><el-button link type="danger" :disabled="mutating" @click="remove(row)">删除</el-button></template>
          </template>
        </div>
      </li>
    </ul>
    <div v-else-if="!error" class="attachment-empty"><Icon icon="ep:paperclip" /><span>暂无附件</span><span v-if="!readonly" class="empty-hint">点击“上传文件”添加</span></div>
    <nav v-if="total > 20" class="attachment-pagination" aria-label="附件分页"><el-button :disabled="page===1 || fetching || mutating" @click="page--;refresh()">上一页</el-button><span>第 {{page}} 页</span><el-button :disabled="page * 20 >= total || fetching || mutating" @click="page++;refresh()">下一页</el-button></nav>
    <ProjectBusinessDeliveryHistory ref="historyPanel" :api="api" :entity-id="entityId" :deliverable-type="type" />
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { formatDate } from '@/utils/formatTime'
import ProjectBusinessDeliveryHistory from './ProjectBusinessDeliveryHistory.vue'
import type { ProjectBusinessApi, BusinessId } from '@/api/pms/platform/business'
import type { DeliveryRecord, DeliveryScope } from '@/api/pms/platform/businessmodel/delivery'
const props = withDefaults(defineProps<{ api: ProjectBusinessApi; entityId: BusinessId; readonly?: boolean; deliverableType?: string; embedded?:boolean }>(), { deliverableType: 'ATTACHMENT' })
const emit=defineEmits<{changed:[];'busy-change':[boolean]}>()
const historyPanel=ref<InstanceType<typeof ProjectBusinessDeliveryHistory>>(),editingId=ref<string>()
const message = useMessage(), type = computed(()=>props.deliverableType)
const scope = ref<DeliveryScope>(), rows = ref<DeliveryRecord[]>([]), titles = ref<Record<string,string>>({})
const loaded = ref(false), error = ref(''), contextError = ref(''), fetching = ref(false), mutating = ref(false), page = ref(1), total = ref(0)
const fileInput = ref<HTMLInputElement>(), pending = ref<{ file:File;scope:DeliveryScope;key:string }>()
let generation = 0
const failureMessage = (failure:any) => failure?.response?.data?.msg || failure?.message || '交付件操作失败'
const refresh = async () => {
  const active=++generation, client=props.api, id=props.entityId, deliverableType=type.value,currentPage=page.value
  fetching.value=true; error.value=''; contextError.value=''; scope.value=undefined; loaded.value=false
  if(!/^[A-Za-z0-9_.:-]{1,64}$/.test(deliverableType)){error.value='交付件类型不合法';fetching.value=false;return}
  // Listing is authorized by the entity endpoint. Upload context is independent and must not delay reading.
  const listRequest=client.deliveries(id,deliverableType,currentPage).then(list=>{
    if(active!==generation)return
    rows.value=list.list;total.value=list.total;loaded.value=true
    titles.value=Object.fromEntries(list.list.map(row=>[row.id,row.title]))
  }).catch(failure=>{if(active===generation){rows.value=[];error.value=failureMessage(failure)}})
    .finally(()=>{if(active===generation)fetching.value=false})
  const contextRequest=props.readonly?Promise.resolve():client.deliveryContext(id).then(context=>{
    if(active!==generation)return
    if(String(context.businessEntityKey)!==String(id) || !context.businessType || !/^[1-9][0-9]*$/.test(String(context.projectId)))throw new Error('交付件归属不匹配')
    scope.value={...context,deliverableType}
  }).catch(failure=>{if(active===generation)contextError.value=failureMessage(failure)})
  await Promise.all([listRequest,contextRequest])
}
const selectFile = (event:Event) => {
  const file=(event.target as HTMLInputElement).files?.[0]
  if(!file || !scope.value || props.readonly || mutating.value)return
  pending.value={file,scope:{...scope.value},key:crypto.randomUUID()};upload()
}
const upload = async () => {
  if(!pending.value || props.readonly || mutating.value)return
  const attempt=pending.value, active=generation, client=props.api
  mutating.value=true;error.value=''
  try {
    await client.upload(attempt.scope,attempt.file,attempt.key)
    if(active===generation){pending.value=undefined;if(fileInput.value)fileInput.value.value='';await refresh();emit('changed')}
  } catch(failure){if(active===generation)error.value=failureMessage(failure)}
  finally{mutating.value=false}
}
const edit = async (row:DeliveryRecord) => {
  if(props.readonly || row.editable===false || mutating.value || !titles.value[row.id]?.trim())return
  const client=props.api,id=props.entityId,active=generation,title=titles.value[row.id];mutating.value=true;error.value=''
  try{await client.editDelivery(id,row,title);if(active===generation){editingId.value=undefined;await refresh()}}
  catch(failure){if(active===generation)error.value=failureMessage(failure)}finally{mutating.value=false}
}
const remove = async (row:DeliveryRecord) => {
  if(props.readonly || row.editable===false || mutating.value)return
  const client=props.api,id=props.entityId,active=generation;mutating.value=true;error.value=''
  try {
    try{await message.confirm(`确定删除附件“${row.title || row.fileName}”？`)}catch{return}
    if(active!==generation || props.readonly)return
    await client.removeDelivery(id,row);if(active===generation){await refresh();emit('changed')}
  }catch(failure){if(active===generation)error.value=failureMessage(failure)}finally{mutating.value=false}
}
const download=async(row:DeliveryRecord)=>{const active=generation,client=props.api,id=props.entityId;try{const ticket=await client.downloadDelivery(id,row);if(active===generation)window.open(ticket.shortLivedUrl,'_blank','noopener')}catch(failure){if(active===generation)error.value=failureMessage(failure)}}
watch(()=>[props.api.base,props.entityId,type.value,props.readonly],()=>{pending.value=undefined;editingId.value=undefined;page.value=1;rows.value=[];refresh()},{immediate:true})
watch(mutating,value=>emit('busy-change',value))
onBeforeUnmount(()=>++generation)
defineExpose({isBusy:()=>mutating.value,refresh})
</script>
<style scoped>
.project-business-deliveries{min-width:0;margin-top:20px;padding-top:16px;border-top:1px solid var(--el-border-color-lighter)}
.project-business-deliveries.embedded{margin:0;padding:0;border:0}
.attachment-toolbar,.attachment-heading,.attachment-actions,.file-actions,.file-meta,.pending-upload,.attachment-pagination{display:flex;align-items:center;gap:12px}
.attachment-toolbar{justify-content:space-between;gap:16px;margin-bottom:12px;flex-wrap:wrap}
.attachment-heading h3{margin:0;font-size:14px;font-weight:600;line-height:24px}
.attachment-count,.file-meta,.empty-hint{color:var(--el-text-color-secondary);font-size:12px}
.attachment-actions :deep(.el-button)+.el-button,.file-actions :deep(.el-button)+.el-button{margin-left:0}
.file-input{display:none}
.attachment-list{padding:0;margin:0;list-style:none;border-top:1px solid var(--el-border-color-lighter)}
.attachment-row{display:flex;align-items:center;gap:12px;padding:12px 0;border-bottom:1px solid var(--el-border-color-lighter)}
.file-icon{flex-shrink:0;color:var(--el-color-primary);font-size:24px}
.file-info{flex:1;min-width:0}
.file-name{font-weight:500;color:var(--el-text-color-primary);overflow-wrap:anywhere}
.file-meta{margin-top:4px;gap:8px;flex-wrap:wrap;line-height:18px}
.file-meta span{min-width:0;overflow-wrap:anywhere}
.file-meta time{white-space:nowrap}
.file-actions{flex-shrink:0}
.attachment-empty{display:flex;align-items:center;gap:8px;min-height:64px;color:var(--el-text-color-secondary);border-top:1px solid var(--el-border-color-lighter)}
.pending-upload{margin:12px 0;padding:12px;background:var(--el-fill-color-light);border-radius:var(--el-border-radius-base);font-size:13px;flex-wrap:wrap}
.pending-name{flex:1;min-width:0;overflow-wrap:anywhere}
.attachment-pagination{justify-content:flex-end;margin-top:12px;color:var(--el-text-color-secondary);font-size:12px}
@media (width <= 600px){.attachment-row{flex-wrap:wrap}.file-actions{width:100%;justify-content:flex-end}.attachment-actions{flex-wrap:wrap}.attachment-empty{flex-wrap:wrap}.empty-hint{width:100%}}
</style>

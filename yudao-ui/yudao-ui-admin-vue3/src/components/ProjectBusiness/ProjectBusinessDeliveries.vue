<template>
  <section class="project-business-deliveries">
    <h3>交付件</h3>
    <el-switch v-model="history" active-text="查看上传历史（只读）" :disabled="mutating" />
    <el-form inline>
      <el-form-item label="交付件类型"><el-input v-model="type" :disabled="mutating" /></el-form-item>
      <el-tag :type="completed ? 'success' : 'info'">{{ completed ? '已有最新有效上传' : '尚无有效上传' }}</el-tag>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <input v-if="!readonly && !history" ref="fileInput" type="file" aria-label="上传交付件" :disabled="mutating || !scope" @change="selectFile" />
    <el-button v-if="pending && !readonly && !history" :disabled="mutating" @click="upload">重试原上传</el-button>
    <el-table :data="rows" v-loading="fetching || mutating" row-key="id">
      <el-table-column prop="deliverableType" label="交付件类型" />
      <el-table-column label="标题"><template #default="{ row }">
        <el-input v-if="!readonly && !history" v-model="titles[row.id]" :disabled="mutating" maxlength="255" />
        <span v-else>{{ row.title }}</span>
      </template></el-table-column>
      <el-table-column label="文件"><template #default="{ row }"><el-button link :disabled="row.status!=='ACTIVE'" @click="download(row)">{{ row.fileName }}</el-button></template></el-table-column>
      <el-table-column prop="status" label="状态" />
      <el-table-column prop="uploadedAt" label="上传时间" />
      <el-table-column v-if="!readonly && !history" label="操作"><template #default="{ row }">
        <el-button :disabled="mutating" link @click="edit(row)">保存标题</el-button>
        <el-button :disabled="mutating" link type="danger" @click="remove(row)">删除交付件</el-button>
      </template></el-table-column>
    </el-table>
    <el-button :disabled="mutating || fetching" @click="refresh">刷新交付件</el-button>
    <el-button v-if="page > 1" :disabled="mutating || fetching" @click="page--; refresh()">上一页</el-button>
    <el-button v-if="page * 20 < total" :disabled="mutating || fetching" @click="page++; refresh()">下一页</el-button>
  </section>
</template>
<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import type { ProjectBusinessApi, BusinessId } from '@/api/pms/platform/business'
import type { DeliveryRecord, DeliveryScope } from '@/api/pms/platform/businessmodel/delivery'
const props = withDefaults(defineProps<{ api: ProjectBusinessApi; entityId: BusinessId; readonly?: boolean; deliverableType?: string }>(), { deliverableType: 'ATTACHMENT' })
const history=ref(false)
const message = useMessage(), type = ref(props.deliverableType)
const scope = ref<DeliveryScope>(), rows = ref<DeliveryRecord[]>([]), titles = ref<Record<string,string>>({})
const completed = ref(false), error = ref(''), fetching = ref(false), mutating = ref(false), page = ref(1), total = ref(0)
const fileInput = ref<HTMLInputElement>(), pending = ref<{ file:File;scope:DeliveryScope;key:string }>()
let generation = 0
const failureMessage = (failure:any) => failure?.response?.data?.msg || failure?.message || '交付件操作失败'
const refresh = async () => {
  const active=++generation, client=props.api, id=props.entityId, deliverableType=type.value,historical=history.value,currentPage=page.value
  fetching.value=true; error.value=''; scope.value=undefined; completed.value=false
  try {
    if(!/^[A-Za-z0-9_.:-]{1,64}$/.test(deliverableType))throw new Error('交付件类型不合法')
    const context=await client.deliveryContext(id)
    if(String(context.businessEntityKey)!==String(id) || !context.businessType || !/^[1-9][0-9]*$/.test(String(context.projectId)))throw new Error('交付件归属不匹配')
    const target={...context,deliverableType}
    const [list,result]=await Promise.all([historical?client.deliveryHistory(id,deliverableType,currentPage):client.deliveries(id,deliverableType,currentPage),client.completion(target)])
    if(active!==generation)return
    scope.value=target;rows.value=list.list;total.value=list.total;completed.value=result.completed
    titles.value=Object.fromEntries(list.list.map(row=>[row.id,row.title]))
  } catch(failure) {if(active===generation){rows.value=[];error.value=failureMessage(failure)}}
  finally {if(active===generation)fetching.value=false}
}
const selectFile = (event:Event) => {
  const file=(event.target as HTMLInputElement).files?.[0]
  if(!file || !scope.value || props.readonly || history.value || mutating.value)return
  pending.value={file,scope:{...scope.value},key:crypto.randomUUID()};upload()
}
const upload = async () => {
  if(!pending.value || props.readonly || history.value || mutating.value)return
  const attempt=pending.value, active=generation, client=props.api
  mutating.value=true;error.value=''
  try {
    await client.upload(attempt.scope,attempt.file,attempt.key)
    if(active===generation){pending.value=undefined;if(fileInput.value)fileInput.value.value='';await refresh()}
  } catch(failure){if(active===generation)error.value=failureMessage(failure)}
  finally{mutating.value=false}
}
const edit = async (row:DeliveryRecord) => {
  if(props.readonly || history.value || mutating.value)return
  const client=props.api,id=props.entityId,active=generation,title=titles.value[row.id];mutating.value=true;error.value=''
  try{await client.editDelivery(id,row,title);if(active===generation)await refresh()}
  catch(failure){if(active===generation)error.value=failureMessage(failure)}finally{mutating.value=false}
}
const remove = async (row:DeliveryRecord) => {
  if(props.readonly || history.value || mutating.value)return
  const client=props.api,id=props.entityId,active=generation;mutating.value=true;error.value=''
  try {
    try{await message.confirm('确定删除该交付件记录？归档或被引用的记录将由服务端保护。')}catch{return}
    if(active!==generation || props.readonly)return
    await client.removeDelivery(id,row);if(active===generation)await refresh()
  }catch(failure){if(active===generation)error.value=failureMessage(failure)}finally{mutating.value=false}
}
const download=async(row:DeliveryRecord)=>{const active=generation,client=props.api,id=props.entityId;try{const ticket=await client.downloadDelivery(id,row);if(active===generation)window.open(ticket.shortLivedUrl,'_blank','noopener')}catch(failure){if(active===generation)error.value=failureMessage(failure)}}
watch(()=>[props.api.base,props.entityId,type.value],()=>{pending.value=undefined;page.value=1;rows.value=[];refresh()},{immediate:true})
watch(history,()=>{page.value=1;rows.value=[];refresh()})
onBeforeUnmount(()=>++generation)
defineExpose({isBusy:()=>mutating.value,refresh})
</script>

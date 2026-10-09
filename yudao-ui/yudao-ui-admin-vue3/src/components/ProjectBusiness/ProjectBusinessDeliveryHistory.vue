<template>
  <el-drawer v-model="visible" title="上传记录" size="min(640px, 100vw)" append-to-body destroy-on-close>
    <p class="history-hint">保留历次上传记录，已移除的附件仅供查看。</p>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false"><el-button link @click="load">重试</el-button></el-alert>
    <el-skeleton v-if="loading" :rows="3" animated />
    <ul v-else-if="rows.length" class="history-list">
      <li v-for="row in rows" :key="row.id" class="history-row">
        <Icon icon="ep:document" class="history-icon" aria-hidden="true" />
        <div class="history-info"><strong>{{row.title || row.fileName}}</strong><span v-if="row.title && row.title!==row.fileName">{{row.fileName}}</span><time>{{formatDate(row.uploadedAt, 'YYYY-MM-DD HH:mm')}}</time></div>
        <div class="history-actions"><el-tag size="small" :type="row.status==='ACTIVE' ? 'success' : 'info'">{{row.status==='ACTIVE' ? '有效' : row.status==='WITHDRAWN' ? '已移除' : '已失效'}}</el-tag><el-button v-if="row.status==='ACTIVE'" link type="primary" @click="download(row)">下载</el-button></div>
      </li>
    </ul>
    <el-empty v-else-if="!error" description="暂无上传记录" :image-size="64" />
    <nav v-if="total > 20" class="history-pagination" aria-label="上传记录分页"><el-button :disabled="page===1 || loading" @click="page--;load()">上一页</el-button><span>第 {{page}} 页</span><el-button :disabled="page * 20 >= total || loading" @click="page++;load()">下一页</el-button></nav>
  </el-drawer>
</template>
<script setup lang="ts">
import {ref,watch,onBeforeUnmount} from 'vue'
import {formatDate} from '@/utils/formatTime'
import type {BusinessId,ProjectBusinessApi} from '@/api/pms/platform/business'
import type {DeliveryRecord} from '@/api/pms/platform/businessmodel/delivery'
const props=defineProps<{api:ProjectBusinessApi;entityId:BusinessId;deliverableType:string}>()
const visible=ref(false),loading=ref(false),error=ref(''),rows=ref<DeliveryRecord[]>([]),page=ref(1),total=ref(0)
let generation=0
const load=async()=>{
  const active=++generation,client=props.api,id=props.entityId,type=props.deliverableType,currentPage=page.value
  loading.value=true;error.value=''
  try{const result=await client.deliveryHistory(id,type,currentPage);if(active===generation){rows.value=result.list;total.value=result.total}}
  catch(failure:any){if(active===generation){rows.value=[];error.value=failure?.response?.data?.msg || failure?.message || '上传记录读取失败'}}
  finally{if(active===generation)loading.value=false}
}
const download=async(row:DeliveryRecord)=>{
  if(row.status!=='ACTIVE')return
  const active=generation,client=props.api,id=props.entityId
  try{const ticket=await client.downloadDelivery(id,row);if(active===generation && visible.value)window.open(ticket.shortLivedUrl,'_blank','noopener')}
  catch(failure:any){if(active===generation)error.value=failure?.response?.data?.msg || failure?.message || '附件下载失败'}
}
const open=()=>{page.value=1;rows.value=[];total.value=0;visible.value=true;load()}
watch(()=>[props.api.base,props.entityId,props.deliverableType],()=>{generation++;visible.value=false;rows.value=[];error.value='';loading.value=false})
watch(visible,value=>{if(!value)generation++})
onBeforeUnmount(()=>generation++)
defineExpose({open})
</script>
<style scoped>
.history-hint{margin:0 0 16px;color:var(--el-text-color-secondary);font-size:13px;line-height:1.6}
.history-list{list-style:none;margin:0;padding:0}
.history-row{display:flex;align-items:flex-start;gap:12px;padding:16px 0;border-bottom:1px solid var(--el-border-color-lighter)}
.history-icon{flex-shrink:0;margin-top:2px;font-size:24px;color:var(--el-color-primary)}
.history-info{flex:1;min-width:0;display:grid;gap:4px;overflow-wrap:anywhere;font-size:13px}
.history-info strong{font-weight:500;color:var(--el-text-color-primary)}
.history-info span,.history-info time{color:var(--el-text-color-secondary);font-size:12px}
.history-actions{display:flex;align-items:center;gap:12px;flex-shrink:0}
.history-pagination{display:flex;justify-content:flex-end;align-items:center;gap:12px;margin-top:16px;font-size:12px;color:var(--el-text-color-secondary)}
@media (width <= 400px){.history-row{flex-wrap:wrap}.history-actions{width:100%;justify-content:flex-end}}
</style>

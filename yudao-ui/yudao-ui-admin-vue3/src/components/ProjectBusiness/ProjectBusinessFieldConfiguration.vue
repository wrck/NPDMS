<template>
  <el-button :disabled="disabled || busy" @click="open">字段配置</el-button>
  <el-dialog v-model="visible" title="业务字段配置" width="900px" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert title="仅配置展示和查询，不改变字段读写权限。恢复默认需保存后生效。" type="info" :closable="false" />
    <el-table :data="settings" v-loading="busy" row-key="code">
      <el-table-column prop="code" label="字段" />
      <el-table-column label="名称"><template #default="{row}"><el-input v-model="row.label" :disabled="busy" maxlength="128" /></template></el-table-column>
      <el-table-column label="顺序" width="140"><template #default="{row}"><el-input-number v-model="row.displayOrder" :disabled="busy" :precision="0" /></template></el-table-column>
      <el-table-column label="列表显示" width="90"><template #default="{row}"><el-checkbox v-model="row.listVisible" :disabled="busy || !original(row.code)?.readable" /></template></el-table-column>
      <el-table-column label="可查询" width="90"><template #default="{row}"><el-checkbox v-model="row.searchable" :disabled="busy || !original(row.code)?.searchable" /></template></el-table-column>
      <el-table-column label="可排序" width="90"><template #default="{row}"><el-checkbox v-model="row.sortable" :disabled="busy || !original(row.code)?.sortable" /></template></el-table-column>
    </el-table>
    <template #footer>
      <el-button :disabled="busy" @click="reset">恢复默认</el-button>
      <el-button :disabled="busy" @click="visible=false">取消</el-button>
      <el-button type="primary" :disabled="busy || !loaded" :loading="saving" @click="save">保存配置</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {useMessage} from '@/hooks/web/useMessage'
import type {ProjectBusinessApi,BusinessFieldSetting} from '@/api/pms/platform/business'
import type {FieldVO} from '@/api/pms/platform/businessmodel'
const props=defineProps<{api:ProjectBusinessApi;disabled?:boolean}>()
const emit=defineEmits<{changed:[]}>()
const visible=ref(false),loading=ref(false),saving=ref(false),loaded=ref(false),error=ref(''),version=ref(0)
const fields=ref<FieldVO[]>([]),settings=ref<BusinessFieldSetting[]>([]),busy=computed(()=>loading.value||saving.value)
const message=useMessage(),initial=ref('')
let generation=0
watch(()=>props.api.base,()=>{++generation;visible.value=false;loaded.value=false;loading.value=false;saving.value=false;error.value=''})
const original=(code:string)=>fields.value.find(field=>field.code===code)
const defaults=()=>fields.value.map(field=>({code:field.code,label:field.name,displayOrder:field.displayOrder??0,listVisible:field.listVisible??field.readable,searchable:field.searchable??false,sortable:field.sortable??false}))
const reset=()=>{settings.value=defaults()}
const open=async()=>{
  if(props.disabled||busy.value)return
  const active=++generation,api=props.api;visible.value=true;loading.value=true;loaded.value=false;error.value=''
  try{const [model,configuration]=await Promise.all([api.fieldDefaults(),api.fieldConfiguration()]);if(active!==generation)return
    fields.value=model.fields;version.value=configuration.version
    settings.value=defaults().map(field=>({...field,...configuration.fields.find(setting=>setting.code===field.code)}));initial.value=JSON.stringify(settings.value);loaded.value=true
  }catch(failure){if(active===generation)error.value=failure instanceof Error?failure.message:'字段配置读取失败'}
  finally{if(active===generation)loading.value=false}
}
const save=async()=>{
  if(props.disabled||busy.value||!loaded.value)return
  const active=generation,api=props.api;saving.value=true;error.value=''
  try{await api.saveFieldConfiguration({version:version.value,fields:settings.value.map(field=>({...field}))});if(active!==generation)return;visible.value=false;emit('changed')}
  catch(failure){if(active===generation)error.value=failure instanceof Error?failure.message:'字段配置保存失败，请重新读取后重试'}
  finally{if(active===generation)saving.value=false}
}
const requestLeave=async()=>{
  if(busy.value)return false
  if(!visible.value || JSON.stringify(settings.value)===initial.value)return true
  try{await message.confirm('字段配置尚未保存，确定离开？');return true}catch{return false}
}
defineExpose({isBusy:()=>busy.value,requestLeave})
</script>

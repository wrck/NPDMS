<template>
  <el-button :disabled="disabled || busy" @click="open">{{buttonLabel || '字段配置'}}</el-button>
  <el-dialog v-model="visible" title="业务配置" width="1100px" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert title="配置控制列表、表单、明细页签及可选业务功能的展示，操作权限由业务授权决定。" type="info" :closable="false" />
    <div class="configuration-toolbar">
      <el-select v-model="selected" aria-label="命名配置" :disabled="busy"><el-option value="" label="基础配置"/><el-option v-for="profile in profiles" :key="profile.key" :value="profile.key" :label="profile.name"/></el-select>
      <el-input v-if="selectedProfile" v-model="selectedProfile.name" aria-label="配置名称" maxlength="128" :disabled="busy" />
      <el-checkbox :model-value="defaultProfile===selected" :disabled="busy" @update:model-value="value=>{if(value)defaultProfile=selected}">默认配置</el-checkbox>
      <el-button :disabled="busy" @click="addProfile">复制为新配置</el-button>
      <el-button v-if="selectedProfile" :disabled="busy" @click="removeProfile">删除配置</el-button>
      <el-select v-if="selectedProfile && formOptions.length" v-model="selectedProfile.formRevisionId" aria-label="表单模板" placeholder="默认表单模板" clearable :disabled="busy">
        <el-option v-for="option in formOptions" :key="String(option.layout.binding.formRevisionId)" :value="option.layout.binding.formRevisionId" :label="`${option.name} · V${option.layout.revisionNo}`"/>
      </el-select>
    </div>
    <div class="configuration-toolbar" aria-label="可选业务功能">
      <el-checkbox v-model="features.attachments" :disabled="busy">启用通用附件</el-checkbox>
      <el-checkbox v-model="features.approvals" :disabled="busy">启用独立业务审批</el-checkbox>
      <span>关闭后隐藏入口，已保存的附件和审批历史保留。原业务必需的材料和审批按原规则执行。</span>
    </div>
    <el-table :data="settings" v-loading="busy" row-key="code">
      <el-table-column prop="code" label="字段" />
      <el-table-column label="名称"><template #default="{row}"><el-input v-model="row.label" :disabled="busy" maxlength="128" /></template></el-table-column>
      <el-table-column label="顺序" width="130"><template #default="{row}"><el-input-number v-model="row.displayOrder" :disabled="busy" :precision="0" /></template></el-table-column>
      <el-table-column label="列表" width="70"><template #default="{row}"><el-checkbox v-model="row.listVisible" :disabled="busy || !original(row.code)?.readable" /></template></el-table-column>
      <el-table-column label="表单" width="70"><template #default="{row}"><el-checkbox v-model="row.formVisible" :disabled="busy || !original(row.code)?.readable || !!original(row.code)?.children?.length" /></template></el-table-column>
      <el-table-column label="明细页签" width="90"><template #default="{row}"><el-checkbox v-model="row.tabVisible" :disabled="busy || !original(row.code)?.readable || !original(row.code)?.children?.length" /></template></el-table-column>
      <el-table-column label="查询" width="70"><template #default="{row}"><el-checkbox v-model="row.searchable" :disabled="busy || !original(row.code)?.searchable" /></template></el-table-column>
      <el-table-column label="排序" width="70"><template #default="{row}"><el-checkbox v-model="row.sortable" :disabled="busy || !original(row.code)?.sortable" /></template></el-table-column>
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
import type {ProjectBusinessApi,BusinessFieldSetting,BusinessNamedConfiguration,BusinessId,BusinessFeatures} from '@/api/pms/platform/business'
import type {FieldVO,BusinessEntityFormData} from '@/api/pms/platform/businessmodel'
const props=defineProps<{api:ProjectBusinessApi;disabled?:boolean;projectId?:BusinessId;buttonLabel?:string}>()
const emit=defineEmits<{changed:[]}>()
const visible=ref(false),loading=ref(false),saving=ref(false),loaded=ref(false),error=ref(''),version=ref(0)
const fields=ref<FieldVO[]>([]),base=ref<BusinessFieldSetting[]>([]),profiles=ref<BusinessNamedConfiguration[]>([]),selected=ref(''),defaultProfile=ref('')
const formOptions=ref<Array<{name:string;layout:NonNullable<BusinessEntityFormData['layout']>}>>([])
const selectedProfile=computed(()=>profiles.value.find(profile=>profile.key===selected.value))
const settings=computed({get:()=>selectedProfile.value?.fields||base.value,set:value=>{if(selectedProfile.value)selectedProfile.value.fields=value;else base.value=value}})
const baseFeatures=ref<BusinessFeatures>({attachments:false,approvals:false})
const features=computed({get:()=>selectedProfile.value?.features||baseFeatures.value,set:value=>{if(selectedProfile.value)selectedProfile.value.features=value;else baseFeatures.value=value}})
const busy=computed(()=>loading.value||saving.value),message=useMessage(),initial=ref('')
let generation=0
watch(()=>props.api.base,()=>{++generation;visible.value=false;loaded.value=false;loading.value=false;saving.value=false;error.value=''})
const original=(code:string)=>fields.value.find(field=>field.code===code)
const defaults=()=>fields.value.map(field=>({code:field.code,label:field.name,displayOrder:field.displayOrder??0,listVisible:field.listVisible??field.readable,searchable:field.searchable??false,sortable:field.sortable??false,formVisible:field.formVisible??field.readable,tabVisible:field.tabVisible??field.readable}))
const expand=(overrides:BusinessFieldSetting[])=>defaults().map(field=>({...field,...Object.fromEntries(Object.entries(overrides.find(setting=>setting.code===field.code)||{}).filter(([,value])=>value!=null))}))
const reset=()=>{settings.value=defaults();features.value={attachments:false,approvals:false}}
const snapshot=()=>({version:version.value,fields:base.value,profiles:profiles.value,defaultProfile:defaultProfile.value||undefined,features:baseFeatures.value})
const addProfile=()=>{const key=`view_${crypto.randomUUID().replaceAll('-','')}`;profiles.value.push({key,name:'新配置',fields:JSON.parse(JSON.stringify(settings.value)),formRevisionId:selectedProfile.value?.formRevisionId,features:{...features.value}});selected.value=key}
const removeProfile=()=>{profiles.value=profiles.value.filter(profile=>profile.key!==selected.value);if(defaultProfile.value===selected.value)defaultProfile.value='';selected.value=''}
const open=async()=>{
  if(props.disabled||busy.value)return
  const active=++generation,api=props.api;visible.value=true;loading.value=true;loaded.value=false;error.value=''
  try{
    const [model,configuration,options]=await Promise.all([api.fieldDefaults(),api.fieldConfiguration(),props.projectId==null?Promise.resolve([]):api.formOptions(props.projectId)])
    if(active!==generation)return
    fields.value=model.fields;version.value=configuration.version;base.value=expand(configuration.fields)
    baseFeatures.value={attachments:false,approvals:false,...configuration.features}
    profiles.value=(configuration.profiles||[]).map(profile=>({...profile,fields:expand(profile.fields),features:{...baseFeatures.value,...profile.features}}))
    defaultProfile.value=configuration.defaultProfile||'';selected.value=defaultProfile.value;formOptions.value=options
    initial.value=JSON.stringify(snapshot());loaded.value=true
  }catch(failure){if(active===generation)error.value=failure instanceof Error?failure.message:'字段配置读取失败'}
  finally{if(active===generation)loading.value=false}
}
const save=async()=>{
  if(props.disabled||busy.value||!loaded.value)return
  const active=generation,api=props.api;saving.value=true;error.value=''
  try{await api.saveFieldConfiguration(JSON.parse(JSON.stringify(snapshot())));if(active!==generation)return;visible.value=false;emit('changed')}
  catch(failure){if(active===generation)error.value=failure instanceof Error?failure.message:'字段配置保存失败，请重新读取后重试'}
  finally{if(active===generation)saving.value=false}
}
const requestLeave=async()=>{
  if(busy.value)return false
  if(!visible.value || JSON.stringify(snapshot())===initial.value)return true
  try{await message.confirm('字段配置尚未保存，确定离开？');return true}catch{return false}
}
defineExpose({isBusy:()=>busy.value,requestLeave})
</script>
<style scoped>.configuration-toolbar{display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin:16px 0}.configuration-toolbar :deep(.el-select),.configuration-toolbar :deep(.el-input){width:210px}</style>

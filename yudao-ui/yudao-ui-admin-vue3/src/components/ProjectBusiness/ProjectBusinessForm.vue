<template>
  <el-alert v-if="error" :title="error" type="error" :closable="false" />
  <div v-if="!disabled && options.length" class="business-form-configuration">
    <label>字段配置</label>
    <el-select :model-value="selected?.binding.formRevisionId == null ? '' : String(selected.binding.formRevisionId)" :disabled="loading" @change="choose">
      <el-option v-if="presentation?.layout" label="当前已保存配置" :value="String(presentation.layout.binding.formRevisionId)" />
      <el-option v-for="option in options.filter(item=>String(item.layout.binding.formRevisionId)!==String(presentation?.layout?.binding.formRevisionId))" :key="String(option.layout.binding.formRevisionId)" :value="String(option.layout.binding.formRevisionId)" :label="`${option.name} · V${option.layout.revisionNo}`" />
    </el-select>
    <span v-if="bindingChanged">待保存配置</span>
  </div>
  <el-skeleton v-if="loading" :rows="5" animated />
  <component v-else :is="formComponent || ProjectBusinessContentForm" ref="content" v-bind="formComponent ? {api,current,scopeProjectId} : {}"
    :fields="fields" :writable-fields="writableFields" :initial-values="visibleValues" :presentation="visiblePresentation" :appearance="appearance" :disabled="disabled"
    @action="(kind:string,sn?:string)=>emit('action',kind,sn)" />
</template>
<script setup lang="ts">
import {computed,ref,shallowRef,watch,type Component} from 'vue'
import type {BusinessEntityData,BusinessEntityFormData,FieldVO} from '@/api/pms/platform/businessmodel'
import type {BusinessId,ProjectBusinessApi} from '@/api/pms/platform/business'
import type {BusinessFormAppearance} from '@/components/BusinessEntity/businessFormAppearance'
import ProjectBusinessContentForm from './ProjectBusinessContentForm.vue'
type Layout=NonNullable<BusinessEntityFormData['layout']>
type Input=Record<string,any>
const props=defineProps<{api:ProjectBusinessApi;current?:BusinessEntityData;scopeProjectId?:BusinessId;formComponent?:Component;fields?:FieldVO[];writableFields:FieldVO[];initialValues?:Input;presentation?:BusinessEntityFormData;appearance?:BusinessFormAppearance;disabled?:boolean}>()
const emit=defineEmits<{action:[kind:string,sn?:string]}>()
const content=ref<{buildInput:()=>Promise<Input>;isBusy?:()=>boolean}>(),loading=ref(false),error=ref('')
const selected=shallowRef<Layout>(),options=shallowRef<Array<{name:string;layout:Layout}>>([]),retained=ref<Input>({})
let generation=0
const bindingChanged=computed(()=>!!selected.value && String(selected.value.binding.formRevisionId)!==String(props.presentation?.layout?.binding.formRevisionId))
const baselineExtra=computed(()=>props.presentation?.extensions.fields||{})
const visibleValues=computed(()=>({...props.initialValues,...Object.fromEntries(Object.entries(retained.value).filter(([key])=>!key.startsWith('$')))}))
const definitions=computed(()=>{
  const fixed=new Set((props.fields||props.writableFields).map(field=>field.code)),result=new Map((props.presentation?.definitions||[]).map(field=>[field.code,field]))
  for(const field of selected.value?.fields||[]){
    const code=selected.value?.binding.fieldBindings[field.fieldKey]
    if(!code||field.controlledFile||fixed.has(code)||result.has(code))continue
    const type=field.valueType==='boolean'?'BOOLEAN':field.valueType==='number'?'NUMBER':field.valueType==='array'?(field.componentType==='group'?'OBJECT_LIST':'TEXT_LIST'):'TEXT'
    result.set(code,{code,label:code,type,required:field.required,maxLength:field.maxLength,allowedValues:field.allowedValues})
  }
  return [...result.values()]
})
const visiblePresentation=computed<BusinessEntityFormData>(()=>({
  ...props.presentation,layout:selected.value,
  extensions:{definitionRevisionId:props.presentation?.extensions.definitionRevisionId,version:props.presentation?.extensions.version??0,fields:{...baselineExtra.value,...retained.value.$extensions?.values}},
  definitions:definitions.value
}))
watch(()=>[props.current?.ref.entityId,props.current?.concurrencyBasis,props.presentation,props.scopeProjectId],async()=>{
  const active=++generation;selected.value=props.presentation?.layout;retained.value={};options.value=[];error.value=''
  if(props.current&&!props.presentation){loading.value=true;return}
  const project=props.scopeProjectId??props.initialValues?.projectId
  // Frozen/view-only records use their stored configuration; no current-template lookup.
  if(project==null||props.disabled){loading.value=false;return}
  loading.value=true
  try{
    if(!selected.value){const defaults=await props.api.formDefaults(project);if(active!==generation)return;selected.value=defaults.layout}
    const choices=await props.api.formOptions(project);if(active===generation)options.value=choices
  }catch(failure){if(active===generation)error.value=failure instanceof Error?failure.message:'命名字段配置读取失败'}
  finally{if(active===generation)loading.value=false}
},{immediate:true})
const merge=(left:Input,right:Input):Input=>({...left,...right,
  ...(left.$extensions||right.$extensions?{$extensions:{...left.$extensions,...right.$extensions,values:{...left.$extensions?.values,...right.$extensions?.values}}}:{}),
  ...(left.$business||right.$business?{$business:{...left.$business,...right.$business}}:{})})
const choose=async(value:string)=>{
  if(props.disabled||loading.value)return
  const layout=String(props.presentation?.layout?.binding.formRevisionId)===value?props.presentation?.layout:options.value.find(option=>String(option.layout.binding.formRevisionId)===value)?.layout
  if(!layout)return
  try{
    const changes=await content.value!.buildInput(),next=merge(retained.value,changes)
    const fixed=new Set((props.fields||props.writableFields).map(field=>field.code)),oldCodes=new Set((props.presentation?.definitions||[]).map(field=>field.code))
    const codes=new Set(Object.values(layout.binding.fieldBindings).filter(code=>!fixed.has(code)))
    const addsNew=[...codes].some(code=>!oldCodes.has(code))
    if(addsNew && Object.keys({...baselineExtra.value,...next.$extensions?.values}).some(code=>!codes.has(code)))throw new Error('此配置会遗漏已有扩展字段，请先在字段配置中保留这些字段')
    retained.value=next;selected.value={...layout,binding:{...layout.binding,extensionDefinitionRevisionId:addsNew?undefined:props.presentation?.layout?.binding.extensionDefinitionRevisionId??props.presentation?.extensions.definitionRevisionId}}
    error.value=''
  }catch(failure){error.value=failure instanceof Error?failure.message:'切换前请检查未保存内容'}
}
const buildInput=async()=>{
  if(props.disabled)return {}
  if(loading.value)throw new Error('字段配置尚未就绪')
  const result=merge(retained.value,await content.value!.buildInput())
  for(const code of Object.keys(result).filter(code=>!code.startsWith('$')))if(JSON.stringify(result[code]??null)===JSON.stringify(props.initialValues?.[code]??null))delete result[code]
  if(bindingChanged.value && (!props.current || Object.keys(result).length || Object.keys(retained.value).length)){
    result.$binding={expectedVersion:props.presentation?.layout?.binding.version??0,formRevisionId:selected.value!.binding.formRevisionId,extensionDefinitionRevisionId:selected.value!.binding.extensionDefinitionRevisionId,fieldBindings:selected.value!.binding.fieldBindings,bindRemainingFields:true}
    if(result.$extensions)result.$extensions={...result.$extensions,definitionRevisionId:selected.value!.binding.extensionDefinitionRevisionId,values:{...baselineExtra.value,...result.$extensions.values}}
  }
  return result
}
defineExpose({buildInput,isBusy:()=>loading.value||!!content.value?.isBusy?.()})
</script>
<style scoped>.business-form-configuration{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:16px}</style>

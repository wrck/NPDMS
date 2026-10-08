<template>
  <el-alert v-if="error" :title="error" type="error" :closable="false" />
  <ProjectBusinessContentForm ref="header" :writable-fields="headerFields" :fields="fields" :initial-values="initialValues" :appearance="appearance" :disabled="disabled" />
  <div v-if="!disabled" class="capture-template-actions">
    <span v-if="layout">{{String(presentation?.layout?.binding.formRevisionId)===String(layout.binding.formRevisionId)?'已绑定发布修订':'待保存绑定发布修订'}} {{ layout.binding.formRevisionId }}</span>
    <el-button :loading="loading" @click="selectDefault">使用完整工勘模板（保留原内容）</el-button>
  </div>
  <el-skeleton v-if="loading" :rows="6" animated />
  <SiteSurveyDynamicForm v-else-if="schema && survey" ref="capture" :model-value="survey" :schema="schema" :readonly="disabled"
    @update:model-value="survey=$event" @action="(kind:string,sn?:string)=>emit('action',kind,sn)" />
</template>
<script setup lang="ts">
import {computed,ref,shallowRef,watch} from 'vue'
import type {BusinessEntityData,BusinessEntityFormData,FieldVO} from '@/api/pms/platform/businessmodel'
import type {BusinessId,ProjectBusinessApi} from '@/api/pms/platform/business'
import type {BusinessFormAppearance} from '@/components/BusinessEntity/businessFormAppearance'
import type {SiteSurveyVO,SiteSurveyFormSchemaVO} from '@/api/pms/engineering/site-survey/entity'
import ProjectBusinessContentForm from '@/components/ProjectBusiness/ProjectBusinessContentForm.vue'
import SiteSurveyDynamicForm from '@/views/pms/delivery-business/site-survey/SiteSurveyDynamicForm.vue'
const props=defineProps<{api:ProjectBusinessApi;current?:BusinessEntityData;scopeProjectId?:BusinessId;writableFields:FieldVO[];fields?:FieldVO[];initialValues?:Record<string,unknown>;presentation?:BusinessEntityFormData;appearance?:BusinessFormAppearance;disabled?:boolean}>()
const emit=defineEmits<{action:[kind:string,sn?:string]}>()
const header=ref<InstanceType<typeof ProjectBusinessContentForm>>(),capture=ref<InstanceType<typeof SiteSurveyDynamicForm>>()
const headerFields=computed(()=>props.writableFields.filter(field=>['projectId','name','surveyDate','surveyorUserId','location'].includes(field.code)))
const layout=shallowRef<BusinessEntityFormData['layout']>(),survey=shallowRef<SiteSurveyVO>(),loading=ref(false),error=ref(''),selected=ref(false)
let generation=0
const schema=computed<SiteSurveyFormSchemaVO|undefined>(()=>layout.value && layout.value.formVersion!=null?{
  revisionId:layout.value.binding.formRevisionId as number,revisionVersion:layout.value.formVersion,
  formConfJson:JSON.parse(layout.value.formConfJson),formRulesJson:JSON.parse(layout.value.formRulesJson),
  fieldBindings:layout.value.binding.fieldBindings,fieldCatalog:(props.fields||props.writableFields).map(field=>({code:field.code,type:field.type,required:field.required}))
}:undefined)
const projectId=()=>props.scopeProjectId ?? props.initialValues?.projectId as BusinessId|undefined
const model=(next:BusinessEntityFormData['layout'],preserve=false):SiteSurveyVO=>({
  ...(props.initialValues||{}),...(props.presentation?.context||{}),
  ...(preserve?survey.value:{}),
  id:props.current?.ref.entityId as number|undefined,projectId:projectId() as number,name:String(props.initialValues?.name||''),
  version:props.current?.concurrencyBasis,formRevisionId:next?.binding.formRevisionId as number|undefined,formRevisionVersion:next?.formVersion,
  fieldBindings:next?.binding.fieldBindings,fieldCatalog:(props.fields||props.writableFields).map(field=>({code:field.code,type:field.type,required:field.required})),
  businessValues:preserve?{...(props.initialValues||{}),...survey.value?.businessValues}:{...(props.initialValues||{})},
  extensionValues:preserve?{...props.presentation?.extensions.fields,...survey.value?.extensionValues}:{...props.presentation?.extensions.fields}
})
const load=async(force=false)=>{
  const active=++generation,project=projectId();error.value='';loading.value=true
  try {
    if(project==null)throw new Error('请先选择项目')
    const next=!force && props.presentation?.layout ? props.presentation : await props.api.formDefaults(project)
    if(active!==generation)return
    if(!next.layout || next.layout.formVersion==null)throw new Error('完整工勘模板尚未发布或不可用')
    layout.value=next.layout;survey.value=model(next.layout,force)
    if(force)selected.value=true
  }catch(failure){if(active===generation)error.value=failure instanceof Error?failure.message:'完整工勘模板读取失败'}
  finally{if(active===generation)loading.value=false}
}
const selectDefault=()=>{if(!props.disabled&&!loading.value)load(true)}
watch(()=>[props.current?.ref.entityId,props.current?.concurrencyBasis,props.presentation,props.scopeProjectId],()=>{
  selected.value=false;layout.value=undefined;survey.value=undefined
  if(props.current&&!props.presentation){loading.value=true;return}
  load()
},{immediate:true})
const changed=(left:unknown,right:unknown)=>JSON.stringify(left??null)!==JSON.stringify(right??null)
const buildInput=async()=>{
  if(props.disabled)return {}
  if(loading.value||error.value||!layout.value||!survey.value||!capture.value)throw new Error('完整工勘模板尚未就绪')
  const basic=await header.value!.buildInput();await capture.value.validate()
  const value=survey.value,source=props.initialValues||{},input:Record<string,unknown>={...basic}
  for(const field of props.writableFields){
    if(Object.hasOwn(basic,field.code))continue
    const next=field.code==='outsourceRequired'?value.outsourceRequired:value.businessValues?.[field.code]
    if(next!==undefined && changed(next,source[field.code]))input[field.code]=next
  }
  const extras:Record<string,unknown>={}
  for(const [code,next] of Object.entries(value.extensionValues||{}))if(changed(next,props.presentation?.extensions.fields[code]))extras[code]=next
  const date=value.businessValues?.requiredEndDate
  if(changed(date,source.requiredEndDate)||value.projectEndDateChanged){
    if(!date || value.projectEndDateVersion==null)throw new Error('请刷新项目版本后再保存工期要求')
    input.$business={requiredEndDate:date,projectVersion:value.projectEndDateVersion}
  }
  const binding=props.presentation?.layout?.binding
  if((!props.current||selected.value||Object.keys(input).length||Object.keys(extras).length) && String(binding?.formRevisionId)!==String(layout.value.binding.formRevisionId))
    input.$binding={expectedVersion:binding?.version??0,formRevisionId:layout.value.binding.formRevisionId,extensionDefinitionRevisionId:binding?.extensionDefinitionRevisionId,fieldBindings:layout.value.binding.fieldBindings,bindRemainingFields:true}
  if(Object.keys(extras).length)input.$extensions={definitionRevisionId:props.presentation?.extensions.definitionRevisionId,expectedVersion:props.presentation?.extensions.version??0,values:extras}
  return input
}
defineExpose({buildInput,isBusy:()=>loading.value})
</script>
<style scoped>.capture-template-actions{display:flex;align-items:center;gap:12px;margin:12px 0;flex-wrap:wrap}</style>

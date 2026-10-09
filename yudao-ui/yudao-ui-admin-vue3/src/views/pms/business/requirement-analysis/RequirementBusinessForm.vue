<template>
  <section class="requirement-form-shell" aria-label="需求分析动态表单">
    <BusinessEntityForm v-if="!scopeProjectId" ref="project" :writable-fields="writableFields.filter(field=>field.code==='projectId')" :initial-values="initialValues" :disabled="disabled" lossless-numbers />
    <div v-form-create-keyboard-rows="disabled" class="form-host" :class="{'is-readonly':disabled}">
      <form-create v-model="values" v-model:api="formApi" :rule="render.rule" :option="render.option" :disabled="disabled" />
    </div>
    <el-text v-if="disabled">该版本正文和附件只读。</el-text>
  </section>
</template>
<script setup lang="ts">
import {reactive,ref,watch,provide} from 'vue'
import formCreateEngine from '@form-create/element-ui'
import type {BusinessEntityData,BusinessEntityFormData,FieldVO} from '@/api/pms/platform/businessmodel'
import type {BusinessId,ProjectBusinessApi} from '@/api/pms/platform/business'
import type {JsonObject} from '@/api/pms/platform/dynamic-form'
import {vFormCreateKeyboardRows} from '@/views/pms/project/project-master-detail/components/formCreateKeyboardRows'
import {decodeDynamicForm} from '@/views/pms/platform/dynamic-form/components/dynamicFormCodec'
import {registerDynamicFormComponents} from '@/views/pms/platform/dynamic-form/components/registerDynamicFormComponents'
import BusinessEntityForm from '@/components/BusinessEntity/BusinessEntityForm.vue'
import template from '@/views/pms/delivery-business/requirement-analysis/demo-template.json'
import RequirementBusinessFiles from './RequirementBusinessFiles.vue'

// Preserve the original professional renderer; all state, bindings and saves belong to ProjectBusinessForm.
const props=defineProps<{api:ProjectBusinessApi;current?:BusinessEntityData;scopeProjectId?:BusinessId;fields?:FieldVO[];writableFields:FieldVO[];initialValues?:Record<string,unknown>;presentation?:BusinessEntityFormData;disabled?:boolean}>()
registerDynamicFormComponents();formCreateEngine.component('RequirementBusinessFiles',RequirementBusinessFiles)
const project=ref<InstanceType<typeof BusinessEntityForm>>(),formApi=ref(),values=ref<JsonObject>({}),render=reactive<{rule:JsonObject[];option:JsonObject}>({rule:[],option:{}})
const busy=reactive(new Set<string>());provide('requirement-business-file-busy',(key:string,value:boolean)=>{if(value)busy.add(key);else busy.delete(key)})
const mappings=new Map<string,{code:string;extension:boolean}>()
const clone=<T,>(value:T):T=>JSON.parse(JSON.stringify(value))
watch(()=>[props.initialValues,props.presentation],()=>{
  const fixed=new Map((props.fields||props.writableFields).map(field=>[field.code,field])),writable=new Set(props.writableFields.map(field=>field.code))
  const bindings=props.presentation?.layout?.binding.fieldBindings||Object.fromEntries([...fixed.keys()].map(code=>[code.replace(/[A-Z]/g,value=>`_${value}`).toUpperCase(),code]))
  const extension=new Set((props.presentation?.definitions||[]).map(field=>field.code))
  const source=props.presentation?.layout
  const decoded=decodeDynamicForm(source?JSON.parse(source.formConfJson):clone(template.formConfJson),source?JSON.parse(source.formRulesJson):clone(template.formRulesJson))
  const next:JsonObject={};mappings.clear()
  const visit=(rules:JsonObject[]):JsonObject[]=>rules.flatMap(rule=>{
    const field=String(rule.field||''),code=bindings[field],metadata=fixed.get(code)
    if(rule.type==='PmsFileArtifact'){
      const sets=props.presentation?.context?.attachments as Array<{key:{purposeCode:string};activeFacts:unknown[]}>|undefined
      return [{...rule,type:'RequirementBusinessFiles',validate:[],props:{apiBase:props.api.base,entityId:props.current?.ref.entityId,fieldKey:field,readonly:!!props.disabled,currentFacts:sets?.find(set=>set.key.purposeCode===`FORM_FIELD_ATTACHMENT/${field}`)?.activeFacts||[]}}]
    }
    if(field){
      if(!code || !metadata && !extension.has(code) || metadata && (!metadata.readable || metadata.formVisible===false))return []
      const empty=rule.type==='group'||rule.type==='checkbox'?[]:null
      next[field]=clone((metadata?props.initialValues?.[code]:props.presentation?.extensions.fields[code])??empty)
      if(metadata?writable.has(code):true)mappings.set(field,{code,extension:!metadata})
      rule.validate=[]
      const readonly=!!props.disabled || !!metadata && !writable.has(code)
      if(rule.type==='Editor')rule.props={...rule.props as JsonObject,height:'180px',readonly,editorConfig:{...((rule.props as JsonObject)?.editorConfig as JsonObject||{}),readOnly:readonly}}
      else if(readonly)rule.props={...rule.props as JsonObject,disabled:true,readonly:true}
    }
    if(Array.isArray(rule.children))rule.children=visit(rule.children as JsonObject[])
    return [rule]
  })
  render.rule=visit(decoded.rule);render.option={...decoded.option,submitBtn:false,resetBtn:false,form:{...decoded.option.form as JsonObject,labelPosition:'top'}};values.value=next
},{immediate:true})
watch(()=>props.disabled,()=>{
  const visit=(rules:JsonObject[])=>rules.forEach(rule=>{
    if(rule.type==='RequirementBusinessFiles')rule.props={...rule.props as JsonObject,readonly:!!props.disabled}
    if(rule.type==='Editor'){
      const readonly=!!props.disabled || !mappings.has(String(rule.field));const existing=rule.props as JsonObject||{}
      rule.props={...existing,readonly,editorConfig:{...existing.editorConfig as JsonObject,readOnly:readonly}}
    }
    if(Array.isArray(rule.children))visit(rule.children as JsonObject[])
  });visit(render.rule)
})
const readValues=async()=>{
  if(props.disabled)return {fields:{},extensions:{}}
  if(busy.size)throw new Error('请等待附件上传完成')
  const fields:Record<string,unknown>=await project.value?.buildInput()||{},extensions:Record<string,unknown>={}
  mappings.forEach(({code,extension},field)=>{if(Object.hasOwn(values.value,field))(extension?extensions:fields)[code]=clone(values.value[field]??null)})
  return {fields,extensions}
}
defineExpose({readValues,isBusy:()=>busy.size>0})
</script>
<style scoped>.requirement-form-shell{min-width:0;max-width:100%}.form-host :deep(.el-form-item__label){font-weight:600}.form-host :deep(.el-form-item){margin-bottom:20px}.is-readonly :deep(.el-input__wrapper),.is-readonly :deep(.el-textarea__inner){box-shadow:none}</style>

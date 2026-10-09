<template>
  <el-alert v-if="!schema" title="完整工勘模板尚未发布或不可用" type="error" :closable="false" />
  <ProjectBusinessContentForm ref="header" :writable-fields="headerFields" :fields="fields" :initial-values="initialValues" :appearance="appearance" :disabled="disabled" />
  <el-form label-position="top" :disabled="disabled"><el-form-item label="工勘地点" required>
    <el-input v-model="description" aria-label="工勘地点" placeholder="填写现场地点说明，或使用下方结构化地点维护" />
    <PmsLocationSelector v-if="!disabled && survey?.projectId" v-model="location" :project-id="survey.projectId" />
  </el-form-item></el-form>
  <SiteSurveyDynamicForm v-if="schema && survey" ref="capture" :model-value="survey" :schema="schema" :readonly="disabled"
    @update:model-value="survey=$event" @action="(kind:string,sn?:string)=>emit('action',kind,sn)" />
</template>
<script setup lang="ts">
import {computed,ref,shallowRef,watch} from 'vue'
import type {BusinessEntityData,BusinessEntityFormData,FieldVO} from '@/api/pms/platform/businessmodel'
import type {BusinessId} from '@/api/pms/platform/business'
import type {BusinessFormAppearance} from '@/components/BusinessEntity/businessFormAppearance'
import type {SiteSurveyVO,SiteSurveyFormSchemaVO} from '@/api/pms/engineering/site-survey/entity'
import ProjectBusinessContentForm from '@/components/ProjectBusiness/ProjectBusinessContentForm.vue'
import SiteSurveyDynamicForm from '@/views/pms/delivery-business/site-survey/SiteSurveyDynamicForm.vue'
import PmsLocationSelector from './SurveyLocationSelector.vue'
import type {LocationMaintainRequest} from '@/api/pms/asset/location'
import {hasStructuredSurveyLocation} from '@/views/pms/delivery-business/site-survey/siteSurveyForm'

// Configuration, binding and extension persistence belong to ProjectBusinessForm.
defineOptions({inheritAttrs:false})
const props=defineProps<{current?:BusinessEntityData;scopeProjectId?:BusinessId;writableFields:FieldVO[];fields?:FieldVO[];initialValues?:Record<string,unknown>;presentation?:BusinessEntityFormData;appearance?:BusinessFormAppearance;disabled?:boolean}>()
const emit=defineEmits<{action:[kind:string,sn?:string]}>()
const header=ref<InstanceType<typeof ProjectBusinessContentForm>>(),capture=ref<InstanceType<typeof SiteSurveyDynamicForm>>()
const headerFields=computed(()=>props.writableFields.filter(field=>['projectId','name','surveyDate','surveyorUserId'].includes(field.code)))
const description=ref(''),location=ref<LocationMaintainRequest>(),initialLocation=ref('')
const locationSignature=(value?:LocationMaintainRequest)=>JSON.stringify({
  address:value?.address,site:value?.site,siteLocation:value?.siteLocation,
  extraSiteLocations:value?.extraSiteLocations?.length ? value.extraSiteLocations : undefined,
  fallbackLocation:hasStructuredSurveyLocation(value) ? undefined : value?.fallbackLocation
})
const layout=computed(()=>props.presentation?.layout),survey=shallowRef<SiteSurveyVO>()
const schema=computed<SiteSurveyFormSchemaVO|undefined>(()=>layout.value && layout.value.formVersion!=null?{
  revisionId:layout.value.binding.formRevisionId as number,revisionVersion:layout.value.formVersion,
  formConfJson:JSON.parse(layout.value.formConfJson),formRulesJson:JSON.parse(layout.value.formRulesJson),
  fieldBindings:layout.value.binding.fieldBindings,fieldCatalog:(props.fields||props.writableFields).map(field=>({code:field.code,type:field.type,required:field.required}))
}:undefined)
watch(()=>[props.initialValues,props.presentation],()=>{
  survey.value={...(props.initialValues||{}),...(props.presentation?.context||{}),id:props.current?.ref.entityId as number|undefined,
    projectId:(props.scopeProjectId??props.initialValues?.projectId) as number,name:String(props.initialValues?.name||''),version:props.current?.concurrencyBasis,
    formRevisionId:layout.value?.binding.formRevisionId as number|undefined,formRevisionVersion:layout.value?.formVersion,
    fieldBindings:layout.value?.binding.fieldBindings,fieldCatalog:schema.value?.fieldCatalog,
    businessValues:{...props.initialValues},extensionValues:{...props.presentation?.extensions.fields}}
  description.value=String(props.initialValues?.location||'')
  const context=props.presentation?.context||{}
  location.value={projectId:survey.value.projectId,...(context.addressId?{address:{id:context.addressId as number,expectedVersion:context.addressVersion as number}}:{}),...(context.siteId?{site:{id:context.siteId as number,expectedVersion:context.siteVersion as number}}:{}),...(context.siteLocationId?{siteLocation:{id:context.siteLocationId as number,expectedVersion:context.siteLocationVersion as number}}:{})}
  if(!context.addressId && !context.siteId && !context.siteLocationId)location.value.fallbackLocation=description.value
  initialLocation.value=locationSignature(location.value)
},{immediate:true})
const readValues=async()=>{
  if(props.disabled)return {fields:{},extensions:{},business:{}}
  if(!schema.value||!survey.value||!capture.value)throw new Error('完整工勘模板尚未就绪')
  const basic=await header.value!.buildInput();await capture.value.validate()
  const value=survey.value,fields:Record<string,unknown>={}
  for(const field of props.writableFields){
    const next=Object.hasOwn(basic,field.code)?basic[field.code]:field.code==='outsourceRequired'?value.outsourceRequired:value.businessValues?.[field.code]
    if(next!==undefined)fields[field.code]=next
  }
  const date=value.businessValues?.requiredEndDate,business:Record<string,unknown>={}
  const maintenance=location.value && JSON.parse(JSON.stringify(location.value)) as LocationMaintainRequest|undefined
  let locationDescription=description.value
  if(hasStructuredSurveyLocation(maintenance) && maintenance){
    if(!maintenance.site?.id && !maintenance.address?.id && !maintenance.address?.detailAddress)throw new Error('新地点必须填写详细地址')
    if(!maintenance.site?.id && !maintenance.address?.id && !maintenance.address?.provinceCode)throw new Error('新地点请选择省市区')
    if(!maintenance.site?.id && !maintenance.address?.id && !maintenance.site?.name)throw new Error('新地点必须填写站点名称')
    if(maintenance.siteLocation && !maintenance.siteLocation.id && !maintenance.siteLocation.name)maintenance.siteLocation=undefined
    if(maintenance.address && !maintenance.address.id)maintenance.address.fullAddress=[maintenance.address.countryName,maintenance.address.provinceName,maintenance.address.cityName,maintenance.address.districtName,maintenance.address.detailAddress].filter(Boolean).join('')
    const address=maintenance.address?.fullAddress || maintenance.addressText || ''
    const site=maintenance.site?.name ? `${maintenance.site.name}（${address}）` : address
    locationDescription=maintenance.fallbackLocation || [site,maintenance.siteLocation?.name,...(maintenance.extraSiteLocations||[]).map(item=>item.name)].filter(Boolean).join(' / ') || locationDescription
  }else if(maintenance?.fallbackLocation && maintenance.fallbackLocation!==props.initialValues?.location)locationDescription=maintenance.fallbackLocation
  if(!locationDescription.trim())throw new Error('请填写工勘地点')
  const locationChanged=locationSignature(location.value)!==initialLocation.value || description.value!==props.initialValues?.location
  if(locationChanged && hasStructuredSurveyLocation(maintenance)){
    business.location=maintenance;business.locationDescription=locationDescription
    if(props.current)delete fields.location;else fields.location=locationDescription
  }else fields.location=locationDescription
  if(JSON.stringify(date??null)!==JSON.stringify(props.initialValues?.requiredEndDate??null)||value.projectEndDateChanged){
    if(!date||value.projectEndDateVersion==null)throw new Error('请刷新项目版本后再保存工期要求')
    business.requiredEndDate=date;business.projectVersion=value.projectEndDateVersion
  }
  return {fields,extensions:value.extensionValues||{},business}
}
defineExpose({readValues})
</script>

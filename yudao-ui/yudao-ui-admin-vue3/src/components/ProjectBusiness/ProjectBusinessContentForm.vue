<template>
  <BusinessEntityForm ref="body" :writable-fields="writableFields" :appearance="appearance" :fields="fields" :presentation="presentation" :initial-values="initialValues" :disabled="disabled" lossless-numbers />
  <BusinessEntityForm v-if="extraFields.length" ref="extra" :writable-fields="extraFields" :initial-values="presentation?.extensions.fields" :disabled="disabled" lossless-numbers />
</template>
<script setup lang="ts">
import {computed,ref} from 'vue'
import BusinessEntityForm from '../BusinessEntity/BusinessEntityForm.vue'
import type {BusinessFormAppearance} from '../BusinessEntity/businessFormAppearance'
import type {BusinessEntityFormData,FieldVO} from '@/api/pms/platform/businessmodel'
const props=defineProps<{appearance?:BusinessFormAppearance;writableFields:FieldVO[];fields?:FieldVO[];presentation?:BusinessEntityFormData;initialValues?:Record<string,unknown>;disabled?:boolean}>()
const body=ref<InstanceType<typeof BusinessEntityForm>>(),extra=ref<InstanceType<typeof BusinessEntityForm>>()
const extraFields=computed<FieldVO[]>(()=>{
  const mapped=new Set(Object.values(props.presentation?.layout?.binding.fieldBindings||{}))
  return (props.presentation?.definitions||[]).filter(field=>!mapped.has(field.code)).map(field=>({code:field.code,name:field.label,type:field.type,required:field.required,readable:true,writable:true}))
})
const buildInput=async()=>{
  const input=await body.value!.buildInput(),changes=await extra.value?.buildInput()
  if(changes && Object.keys(changes).length){
    const layoutPatch=input.$extensions as {values:Record<string,unknown>}|undefined
    input.$extensions={definitionRevisionId:props.presentation!.layout?.binding.extensionDefinitionRevisionId??props.presentation!.extensions.definitionRevisionId,
      expectedVersion:props.presentation!.extensions.version,values:{...layoutPatch?.values,...changes}}
  }
  return input
}
defineExpose({buildInput})
</script>

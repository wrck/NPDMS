<template>
  <el-alert v-if="error" :title="error" type="error" :closable="false" />
  <el-tabs v-if="groups.length">
    <el-tab-pane v-for="group in groups" :key="group.code" :label="group.name" :name="group.code">
      <el-button v-if="group.writable && !disabled" @click="add(group)">新增{{ group.name }}</el-button>
      <el-table :data="rows[group.code]" row-key="id" border>
        <el-table-column v-for="field in group.children?.filter(item=>item.readable)" :key="field.code" :label="field.name" min-width="160">
          <template #default="{row}">
            <span v-if="disabled || !group.writable || !field.writable">{{ display(row[field.code]) }}</span>
            <el-select v-else-if="field.type==='BOOLEAN'" v-model="row[field.code]" :aria-label="field.name" clearable><el-option label="是" :value="true"/><el-option label="否" :value="false"/></el-select>
            <el-date-picker v-else-if="field.type==='DATE' || field.type==='DATETIME'" v-model="row[field.code]" :aria-label="field.name" :type="field.type==='DATE'?'date':'datetime'" :value-format="field.type==='DATE'?'YYYY-MM-DD':'YYYY-MM-DD HH:mm:ss'" />
            <el-select v-else-if="field.type==='TEXT_LIST'" v-model="row[field.code]" :aria-label="field.name" multiple allow-create filterable default-first-option />
            <el-input v-else v-model="row[field.code]" :aria-label="field.name" :inputmode="field.type==='NUMBER'?'decimal':undefined" />
          </template>
        </el-table-column>
        <el-table-column v-if="group.writable && !disabled" label="操作" width="90"><template #default="{$index}"><el-button type="danger" link @click="rows[group.code].splice($index,1)">移除</el-button></template></el-table-column>
      </el-table>
    </el-tab-pane>
  </el-tabs>
</template>
<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import type {FieldVO} from '@/api/pms/platform/businessmodel'
const props=defineProps<{fields:FieldVO[];initialValues?:Record<string,unknown>;disabled?:boolean}>()
const rows=ref<Record<string,Record<string,any>[]>>({}),error=ref('')
const groups=computed(()=>props.fields.filter(field=>field.readable && field.children?.length && field.tabVisible!==false).sort((a,b)=>(a.displayOrder??0)-(b.displayOrder??0)))
watch(()=>[props.initialValues,props.fields],()=>{
  const loaded:typeof rows.value={}
  for(const field of groups.value)loaded[field.code]=JSON.parse(JSON.stringify(props.initialValues?.[field.code]||[]))
  rows.value=loaded;error.value=''
},{immediate:true})
const display=(value:unknown)=>value==null?'—':typeof value==='boolean'?value?'是':'否':Array.isArray(value)?value.join('、'):String(value)
const add=(field:FieldVO)=>rows.value[field.code].push(Object.fromEntries((field.children||[]).filter(child=>child.writable).map(child=>[child.code,child.type==='TEXT_LIST'?[]:null])))
const buildInput=async()=>{
  if(props.disabled)return {}
  error.value='';const input:Record<string,unknown>={}
  for(const group of groups.value.filter(field=>field.writable)){
    for(const [index,row] of rows.value[group.code].entries())for(const field of group.children||[]){
      if(field.writable && field.required && (row[field.code]==null || row[field.code]==='' || Array.isArray(row[field.code]) && !row[field.code].length)){
        error.value=`${group.name}第 ${index+1} 行：${field.name}不能为空`;throw new Error(error.value)
      }
    }
    if(JSON.stringify(rows.value[group.code])!==JSON.stringify(props.initialValues?.[group.code]||[])){
      const writable=new Set(['id','version',...(group.children||[]).filter(field=>field.writable).map(field=>field.code)])
      input[group.code]=rows.value[group.code].map(row=>Object.fromEntries(Object.entries(row).filter(([code])=>writable.has(code))))
    }
  }
  return input
}
defineExpose({buildInput})
</script>

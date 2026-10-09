<template>
  <details class="requirement-file-slot" @toggle="opened=($event.target as HTMLDetailsElement).open">
    <summary>附件<span v-if="currentFacts?.length">（含 {{currentFacts.length}} 个历史文件）</span></summary>
    <template v-if="opened">
      <RevisionFiles v-if="currentFacts?.length" :revision-id="entityId" :field-key="fieldKey" :current-facts="currentFacts" :allowed-actions="[]" disabled />
      <ProjectBusinessDeliveries v-if="entityId" :api="api" :entity-id="entityId" :deliverable-type="fieldKey" :readonly="readonly" embedded @busy-change="value=>busy(fieldKey,value)" />
      <el-text v-else>保存草稿后可添加附件</el-text>
    </template>
  </details>
</template>
<script setup lang="ts">
import {computed,inject,onBeforeUnmount,ref} from 'vue'
import {createProjectBusinessApi,type BusinessId} from '@/api/pms/platform/business'
import type {DynamicFormFileFactVO} from '@/api/pms/platform/dynamic-form'
import ProjectBusinessDeliveries from '@/components/ProjectBusiness/ProjectBusinessDeliveries.vue'
import RevisionFiles from '@/views/pms/delivery-business/requirement-analysis/entity/RevisionFiles.vue'
const props=defineProps<{apiBase:string;entityId?:BusinessId;fieldKey:string;currentFacts?:DynamicFormFileFactVO[];readonly?:boolean}>()
const opened=ref(false),api=computed(()=>createProjectBusinessApi(props.apiBase))
const busy=inject<(key:string,value:boolean)=>void>('requirement-business-file-busy',()=>{})
onBeforeUnmount(()=>busy(props.fieldKey,false))
</script>
<style scoped>.requirement-file-slot{box-sizing:border-box;width:100%;min-width:0;margin:0 0 18px;border:1px solid var(--el-border-color-light);border-radius:6px;padding:10px 14px}.requirement-file-slot>summary{cursor:pointer;color:var(--el-text-color-secondary)}</style>

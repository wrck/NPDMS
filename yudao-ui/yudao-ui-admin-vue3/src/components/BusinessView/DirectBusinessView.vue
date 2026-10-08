<template>
  <el-alert v-if="error" :title="error" type="error" :closable="false" />
  <component ref="content" v-else-if="view" :is="component" :api-base="view.apiBase" :initial-entity-id="entityId"
    :scope-project-id="projectId" :readonly="readonly" :allowed-actions="allowedActions" />
</template>
<script setup lang="ts">
import {computed,defineAsyncComponent,shallowRef,ref,watch} from 'vue'
import {getDirectBusinessView,type DirectBusinessView} from '@/api/pms/platform/business'
import type {BusinessViewId} from '@/api/pms/platform/business-view'
import ProjectBusinessPage from '@/components/ProjectBusiness/ProjectBusinessPage.vue'
const content=ref<{requestLeave?:()=>Promise<boolean>}>()
defineExpose({requestLeave:async()=>content.value?.requestLeave ? await content.value.requestLeave() : !view.value})
const props=defineProps<{ownerModule:string;entityType:string;stableCode:string;projectId:BusinessViewId;entityId?:BusinessViewId;readonly?:boolean;allowedActions:string[]}>()
const survey=defineAsyncComponent(()=>import('@/views/pms/business/site-survey/index.vue'))
const requirement=defineAsyncComponent(()=>import('@/views/pms/business/requirement-analysis/index.vue'))
const view=shallowRef<DirectBusinessView>(),error=ref('');let generation=0
const component=computed(()=>props.stableCode==='SOL_SITE_SURVEY'?survey:props.stableCode==='SOL_REQUIREMENT_ANALYSIS_REVISION'?requirement:ProjectBusinessPage)
watch(()=>[props.ownerModule,props.entityType,props.stableCode,props.projectId,props.entityId],async()=>{
  const active=++generation;view.value=undefined;error.value=''
  try{const result=await getDirectBusinessView(props.stableCode);if(active!==generation)return
    if(result.ownerModule!==props.ownerModule || result.entityType!==props.entityType || result.stableCode!==props.stableCode)throw new Error('业务视图身份不匹配')
    view.value=result
  }catch(failure){if(active===generation)error.value=failure instanceof Error?failure.message:'业务视图读取失败'}
},{immediate:true})
</script>

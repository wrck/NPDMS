<template>
  <div v-if="deferContent" v-loading="true" class="direct-view-loading" />
  <el-alert v-else-if="error" :title="error" type="error" :closable="false" />
  <component ref="content" v-else-if="view" :is="component" :key="`${stableCode}-${projectId}`" :stable-code="stableCode" :api-base="view.apiBase" :initial-entity-id="entityId"
    :scope-project-id="projectId" :readonly="readonly" :allowed-actions="allowedActions" :execution="execution" :stage-code="stageCode" :binding-parameters="bindingParameters"
    :initial-project="nativeBusinessKind(stableCode) ? initialProject : undefined"
    :initialization="initialPage"
    @changed="emit('changed')" @dirty-change="emit('dirty-change', $event)" />
</template>
<script setup lang="ts">
import {computed,shallowRef,ref,watch} from 'vue'
import {getDirectBusinessView,type DirectBusinessView,type BusinessPageOpening,type BusinessPageInitialData} from '@/api/pms/platform/business'
import type {BusinessViewId} from '@/api/pms/platform/business-view'
import type {ProjectBusinessExecutionSelection} from '@/api/pms/project/projects/nodeExecutions'
import {projectBusinessPage} from '@/components/ProjectBusiness/projectBusinessPages'
import {nativeBusinessKind} from '@/components/ProjectBusiness/native/nativeBusinessKinds'
import type {ProjectMasterVO} from '@/api/pms/project/projects'
const content=ref<{requestLeave?:()=>Promise<boolean>;canAdoptAssociatedObject?:(id:BusinessViewId)=>boolean}>()
const emit=defineEmits<{changed:[];'dirty-change':[value:boolean]}>()
defineExpose({requestLeave:async()=>props.deferContent || (content.value?.requestLeave ? await content.value.requestLeave() : !view.value),
  canAdoptAssociatedObject:(id:BusinessViewId)=>content.value?.canAdoptAssociatedObject?.(id)===true})
const props=defineProps<{ownerModule:string;entityType:string;stableCode:string;projectId:BusinessViewId;initialProject?:ProjectMasterVO;entityId?:BusinessViewId;readonly?:boolean;execution?:ProjectBusinessExecutionSelection;stageCode?:string;allowedActions:string[];bindingParameters?:Record<string,unknown>;deferContent?:boolean;initialization?:BusinessPageOpening}>()
const initialPage=shallowRef<BusinessPageInitialData>();let initializationUsed=false
const view=shallowRef<DirectBusinessView>(),error=ref('');let generation=0
const component=computed(()=>projectBusinessPage(props.stableCode))
watch(()=>[props.ownerModule,props.entityType,props.stableCode,props.projectId,nativeBusinessKind(props.stableCode)?undefined:props.entityId],async()=>{
  const active=++generation;view.value=undefined;error.value='';initialPage.value=undefined
  const opening=!initializationUsed?props.initialization:undefined;initializationUsed=true
  try{const result=await(opening?.view??getDirectBusinessView(props.stableCode));if(active!==generation)return
    if(result.ownerModule!==props.ownerModule || result.entityType!==props.entityType || result.stableCode!==props.stableCode)throw new Error('业务视图身份不匹配')
    if(opening){
      if(String(opening.data.request.projectId)!==String(props.projectId)
          || String(opening.data.request.initialEntityId??'')!==String(props.entityId??'')
          || opening.data.model.stableCode!==props.stableCode)throw new Error('业务页面初始化范围不匹配')
      initialPage.value=opening.data
    }
    view.value=result
  }catch(failure){if(active===generation)error.value=failure instanceof Error?failure.message:'业务视图读取失败'}
},{immediate:true})
</script>
<style scoped>
.direct-view-loading { position: relative; min-height: 200px; }
</style>

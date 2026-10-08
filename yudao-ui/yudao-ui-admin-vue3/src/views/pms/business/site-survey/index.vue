<template>
  <ProjectBusinessPage ref="page" api-base="/api/v1/pms/site-survey-business" title="现场工勘" deliverable-type="ATTACHMENT" :operation-allowed="allowed" :hidden-actions="['location','deadline']">
    <template #business-fields="{current,execute,busy,actions}"><SurveyBusinessFields :current="current" :execute="execute" :busy="busy" :actions="actions" /></template>
  </ProjectBusinessPage>
</template>
<script setup lang="ts">
import { ref } from 'vue'
const page=ref<InstanceType<typeof ProjectBusinessPage>>()
defineExpose({requestLeave:async()=>page.value ? await page.value.requestLeave() : false})
import type { BusinessEntityData } from '@/api/pms/platform/businessmodel'
const allowed=(code:string,current?:BusinessEntityData)=>code==='create' || (code==='delivery' ? (current?.fieldValues.status===0 || current?.fieldValues.status===1) : code==='archive' ? current?.fieldValues.status===1 : current?.fieldValues.status===0)
import SurveyBusinessFields from './SurveyBusinessFields.vue'
import ProjectBusinessPage from '@/components/ProjectBusiness/ProjectBusinessPage.vue'
</script>

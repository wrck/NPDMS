<template>
  <ProjectBusinessPage api-base="/api/v1/pms/site-survey-business" title="现场工勘" deliverable-type="ATTACHMENT" :operation-allowed="allowed" />
</template>
<script setup lang="ts">
import type { BusinessEntityData } from '@/api/pms/platform/businessmodel'
const allowed=(code:string,current?:BusinessEntityData)=>code==='create' || (code==='archive' ? current?.fieldValues.status===1 : current?.fieldValues.status===0)
import ProjectBusinessPage from '@/components/ProjectBusiness/ProjectBusinessPage.vue'
</script>

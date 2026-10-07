<template><ProjectBusinessPage api-base="/api/v1/pms/requirement-analysis-business" title="需求分析" deliverable-type="ATTACHMENT" :operation-allowed="allowed" /></template>
<script setup lang="ts">
import type { BusinessEntityData } from '@/api/pms/platform/businessmodel'
const allowed=(code:string,current?:BusinessEntityData)=>code==='create' || (code==='copy' ? current?.fieldValues.revisionState==='FROZEN' : current?.fieldValues.revisionState==='DRAFT')
import ProjectBusinessPage from '@/components/ProjectBusiness/ProjectBusinessPage.vue'
</script>

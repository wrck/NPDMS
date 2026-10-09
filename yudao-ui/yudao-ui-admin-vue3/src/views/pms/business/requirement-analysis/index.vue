<template>
  <ProjectBusinessPage ref="page" api-base="/api/v1/pms/requirement-analysis-business" title="需求分析" deliverable-type="ATTACHMENT"
    :scope-project-id="scopeProjectId" :initial-entity-id="initialEntityId" :readonly="readonly" :allowed-actions="allowedActions"
    :workspace="scopeProjectId!=null" :select-initial="scopeProjectId==null?undefined:selectInitial" :form-component="formComponent" save-label="保存需求分析" :operation-allowed="allowed">
    <template v-if="scopeProjectId!=null" #list="list">
      <div class="requirement-toolbar">
        <div><strong>需求分析</strong><p class="requirement-caption">维护项目需求与附件，按版本保存和确认。</p></div>
        <div class="requirement-actions">
          <el-button :loading="list.loading" :disabled="list.busy" @click="list.reload">刷新</el-button>
          <el-button v-if="!list.rows.length && list.actionsFor().some(action=>action.code==='create'&&action.executable)" type="primary" :disabled="list.busy" @click="list.create">创建需求分析草稿</el-button>
          <el-button :disabled="!list.current || list.busy" @click="openHistory">修订记录</el-button>
        </div>
      </div>
      <el-divider />
      <div class="revision-options" data-testid="requirement-version-table" aria-label="选择需求分析版本">
        <button v-for="row in list.rows.filter(row=>row.fieldValues.revisionState==='DRAFT'||row.fieldValues.effectiveMarker===1)" :key="String(row.ref.entityId)" class="revision-option"
          :class="{'is-selected':String(list.current?.ref.entityId)===String(row.ref.entityId)}" :aria-pressed="String(list.current?.ref.entityId)===String(row.ref.entityId)" :disabled="list.busy" @click="list.open(row.ref.entityId)">
          <strong>{{row.fieldValues.revisionState==='DRAFT'?'编辑当前草稿':'查看有效完成版'}}</strong>
          <span>V{{row.fieldValues.revisionNo}} · {{row.fieldValues.revisionState==='DRAFT'?'草稿':'已完成'}}</span>
          <small v-if="row.fieldValues.frozenAt">{{formatDate(row.fieldValues.frozenAt as string | number)}}</small>
        </button>
      </div>
    </template>
    <template #editor-heading="{current}">
      <div class="analysis-heading"><div><h3>项目需求分析</h3><p>记录客户目标、网络现状和实施要求，作为实施方案与工程交底的输入。</p></div>
        <el-tag v-if="current" :type="current.fieldValues.revisionState==='DRAFT'?'warning':'success'">{{current.fieldValues.revisionState==='DRAFT'?'当前草稿':current.fieldValues.effectiveMarker===1?'当前有效':'历史完成版'}} · V{{current.fieldValues.revisionNo}}</el-tag>
      </div>
    </template>
    <template #details="{current}"><RequirementBriefingSection v-if="current && scopeProjectId" :project-id="Number(scopeProjectId)" /></template>
  </ProjectBusinessPage>
  <RevisionDrawer ref="history" @view="id=>page?.open(id)" @compare="openCompare" />
  <CompareDrawer ref="compare" />
</template>
<script setup lang="ts">
import {markRaw,ref} from 'vue'
import {formatDate} from '@/utils/formatTime'
import type {BusinessEntityData} from '@/api/pms/platform/businessmodel'
import type {BusinessId} from '@/api/pms/platform/business'
import ProjectBusinessPage from '@/components/ProjectBusiness/ProjectBusinessPage.vue'
import RequirementBusinessForm from './RequirementBusinessForm.vue'
import RevisionDrawer from '@/views/pms/delivery-business/requirement-analysis/entity/RevisionDrawer.vue'
import CompareDrawer from '@/views/pms/delivery-business/requirement-analysis/entity/CompareDrawer.vue'
import RequirementBriefingSection from '@/views/pms/delivery-business/requirement-analysis/entity/RequirementBriefingSection.vue'
const props=defineProps<{scopeProjectId?:BusinessId;initialEntityId?:BusinessId;readonly?:boolean;allowedActions?:string[]}>()
const page=ref<InstanceType<typeof ProjectBusinessPage>>(),history=ref<InstanceType<typeof RevisionDrawer>>(),compare=ref<InstanceType<typeof CompareDrawer>>()
const formComponent=markRaw(RequirementBusinessForm)
const allowed=(code:string,current?:BusinessEntityData)=>code==='create' || (code==='copy'?current?.fieldValues.revisionState==='FROZEN':current?.fieldValues.revisionState==='DRAFT')
const selectInitial=(rows:BusinessEntityData[])=>rows.find(row=>row.fieldValues.revisionState==='DRAFT')?.ref.entityId??rows.find(row=>row.fieldValues.effectiveMarker===1)?.ref.entityId
const openHistory=()=>{const row=page.value?.current();if(row)history.value?.open(row.fieldValues.entityId as BusinessId,row.ref.entityId)}
const openCompare=(left:BusinessId,right:BusinessId)=>{const row=page.value?.current();if(row)compare.value?.open(row.fieldValues.entityId as BusinessId,left,right)}
defineExpose({requestLeave:async()=>page.value ? await page.value.requestLeave() : false})
</script>
<style scoped>
.requirement-toolbar,.analysis-heading{display:flex;justify-content:space-between;align-items:center;gap:16px}.requirement-actions{display:flex;gap:8px}.requirement-caption,.analysis-heading p{color:var(--el-text-color-secondary);margin:6px 0}.revision-options{display:flex;gap:12px;flex-wrap:wrap;margin-bottom:20px}.revision-option{display:flex;flex-direction:column;gap:6px;text-align:left;padding:14px 18px;border:1px solid var(--el-border-color);background:var(--el-bg-color);border-radius:8px;color:var(--el-text-color-primary);cursor:pointer;min-width:220px}.revision-option.is-selected{border-color:var(--el-color-primary);background:var(--el-color-primary-light-9)}.revision-option span,.revision-option small{color:var(--el-text-color-secondary)}.analysis-heading{margin:20px 0}.analysis-heading h3{margin:0}@media(max-width:680px){.requirement-toolbar,.analysis-heading{align-items:flex-start;flex-direction:column}}
</style>

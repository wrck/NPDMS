<template>
  <ProjectBusinessPage ref="page" api-base="/api/v1/pms/site-survey-business" title="工勘" deliverable-type="ATTACHMENT"
    :dialog-editor="scopeProjectId!=null" :scope-project-id="scopeProjectId" :prepare-create="scopeProjectId==null?undefined:prepareCreate" :form-appearance="scopeProjectId==null?undefined:appearance"
    :operation-allowed="allowed" :hidden-actions="['location','deadline']">
    <template v-if="scopeProjectId!=null" #list="list">
      <SurveyBusinessList :rows="list.rows" :total="list.total" :page="list.page" :loading="list.loading" :project-id="scopeProjectId" :actions-for="list.actionsFor"
        @search="list.search" @create="list.create" @open="list.open" @action="list.action" @change-page="list.changePage" />
    </template>
    <template #business-fields="{current,execute,busy,actions}"><SurveyBusinessFields v-if="actions.some(action=>['location','deadline'].includes(action.code) && action.executable)" :current="current" :execute="execute" :busy="busy" :actions="actions" /></template>
  </ProjectBusinessPage>
</template>
<script setup lang="ts">
import {computed,ref,onMounted} from 'vue'
import type {BusinessEntityData} from '@/api/pms/platform/businessmodel'
import type {BusinessId} from '@/api/pms/platform/business'
import type {BusinessFormAppearance} from '@/components/BusinessEntity/businessFormAppearance'
import {getSimpleUserList} from '@/api/system/user'
import {getProject} from '@/api/pms/project/projects'
import {useUserStore} from '@/store/modules/user'
import SurveyBusinessList from './SurveyBusinessList.vue'
import SurveyBusinessFields from './SurveyBusinessFields.vue'
import ProjectBusinessPage from '@/components/ProjectBusiness/ProjectBusinessPage.vue'
const props=defineProps<{scopeProjectId?:BusinessId}>()
const page=ref<InstanceType<typeof ProjectBusinessPage>>()
defineExpose({requestLeave:async()=>page.value ? await page.value.requestLeave() : false})
const allowed=(code:string,current?:BusinessEntityData)=>code==='create' || (code==='delivery' ? (current?.fieldValues.status===0 || current?.fieldValues.status===1) : code==='archive' ? current?.fieldValues.status===1 : current?.fieldValues.status===0)
const users=ref<Array<{value:string|number;label:string}>>([])
const user=useUserStore()
const prepareCreate=async()=>({...(props.scopeProjectId==null?{}:{name:(await getProject(props.scopeProjectId as number)).projectName}),surveyorUserId:user.getUser.id})
onMounted(async()=>{try{users.value=(await getSimpleUserList()).map(item=>({value:item.id,label:item.nickname}))}catch{/* Existing stored user values remain readable. */}})
const appearance=computed<BusinessFormAppearance>(()=>({columns:2,labelPosition:'top',labelWidth:'auto',fields:{
  projectId:{hidden:props.scopeProjectId!=null,order:0},name:{hidden:props.scopeProjectId!=null,order:1},
  surveyDate:{order:2},surveyorUserId:{order:3,label:'工勘人员',control:'select',options:users.value},
  location:{order:4,span:2},outsourceRequired:{order:5,span:2,label:'分工／转包',control:'checkbox'},
  powerSupply:{order:6,label:'供电情况'},cabinet:{order:7,label:'机柜情况'},networkPort:{order:8,label:'网口情况'},
  fiber:{order:9,label:'光纤情况'},module:{order:10,label:'模块情况'},cable:{order:11,label:'线缆情况'},ground:{order:12,label:'接地情况'},
  constructionResource:{order:13,label:'施工资源'},conclusion:{order:14,span:2,control:'editor'},remark:{order:15,span:2,control:'textarea'},
  cabinetReady:{hidden:true},cableReady:{hidden:true},moduleReady:{hidden:true},originalModule:{hidden:true},manufacturerInstallation:{hidden:true},
  railTrayRequired:{hidden:true},materialMatches:{hidden:true},powerTypes:{hidden:true},powerEnvironments:{hidden:true},networkPortTypes:{hidden:true},selectedMaterials:{hidden:true}
}}))
</script>

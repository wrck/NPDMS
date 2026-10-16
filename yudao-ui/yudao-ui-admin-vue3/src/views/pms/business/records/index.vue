<template>
  <ContentWrap>
    <el-form inline><el-form-item label="项目">
      <el-select aria-label="业务所属项目" :model-value="projectId" filterable remote :remote-method="searchProjects" :loading="loadingProjects" placeholder="搜索并选择项目" style="width:360px" @change="selectProject">
        <el-option v-for="project in projects" :key="String(project.id)" :value="String(project.id)" :label="`${project.projectName} · ${project.projectCode}`" />
      </el-select>
    </el-form-item></el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button @click="loadView">重新读取</el-button></el-alert>
    <el-empty v-if="!projectId" description="选择项目后可独立办理业务" />
  </ContentWrap>
  <component v-if="view && projectId" :is="projectBusinessPage(view.stableCode)" :key="`${view.stableCode}-${projectId}-${entityId || ''}`" :stable-code="view.stableCode" :api-base="view.apiBase" :scope-project-id="projectId" :initial-entity-id="entityId" v-bind="nativeBusinessKind(view.stableCode) ? { showConfiguration: true } : {}" />
</template>
<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {ContentWrap} from '@/components/ContentWrap'
import {projectBusinessPage} from '@/components/ProjectBusiness/projectBusinessPages'
import {nativeBusinessKind} from '@/components/ProjectBusiness/native/nativeBusinessKinds'
import {getDirectBusinessView,type DirectBusinessView} from '@/api/pms/platform/business'
import {getProjectPage,type ProjectMasterVO} from '@/api/pms/project/projects'

const route=useRoute(),router=useRouter(),view=ref<DirectBusinessView>(),error=ref('')
const projects=ref<ProjectMasterVO[]>([]),loadingProjects=ref(false)
const projectId=computed(()=>typeof route.query.projectId==='string' && /^[1-9][0-9]*$/.test(route.query.projectId)?route.query.projectId:undefined)
const entityId=computed(()=>typeof route.query.entityId==='string' && /^[1-9][0-9]*$/.test(route.query.entityId)?route.query.entityId:undefined)
let generation=0,searchGeneration=0
const loadView=async()=>{const active=++generation;error.value='';view.value=undefined;try{const result=await getDirectBusinessView(String(route.params.stableCode));if(active===generation)view.value=result}catch(failure){if(active===generation)error.value=failure instanceof Error?failure.message:'业务入口读取失败'}}
const searchProjects=async(projectName='')=>{const active=++searchGeneration;loadingProjects.value=true;try{const result=await getProjectPage({pageNo:1,pageSize:50,projectName});if(active===searchGeneration)projects.value=result.list}catch{if(active===searchGeneration)error.value='项目列表读取失败，请重试'}finally{if(active===searchGeneration)loadingProjects.value=false}}
// Route guards in the common page retain responsibility for unsaved input.
const selectProject=(value:string)=>router.replace({query:{...route.query,projectId:value}})
watch(()=>route.params.stableCode,loadView,{immediate:true})
searchProjects()
</script>

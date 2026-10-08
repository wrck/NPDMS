<template>
  <section v-if="current" class="survey-business-fields">
    <el-collapse v-model="expanded">
      <el-collapse-item title="维护结构化工勘地点" name="location">
        <el-input v-model="description" aria-label="地点说明" :disabled="busy || !allowed('location')" />
        <PmsLocationSelector v-if="expanded.includes('location') && allowed('location')" v-model="location" :project-id="projectId" />
        <el-button :disabled="busy || !allowed('location') || !location || !description.trim()" @click="execute('location',{description,location})">保存结构化地点</el-button>
      </el-collapse-item>
      <el-collapse-item v-if="showDeadline!==false" title="项目要求完成日期" name="deadline">
        <el-date-picker v-model="endDate" type="date" value-format="YYYY-MM-DD" aria-label="项目要求完成日期" :disabled="busy || !allowed('deadline')" />
        <el-button :loading="readingProject" :disabled="busy || readingProject || !allowed('deadline') || !endDate" @click="saveDeadline">更新项目期限</el-button>
        <el-alert v-if="error" :title="error" type="error" :closable="false" />
      </el-collapse-item>
    </el-collapse>
  </section>
</template>
<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import PmsLocationSelector from '@/components/PmsLocationSelector/index.vue'
import type {LocationMaintainRequest} from '@/api/pms/asset/location'
import type {BusinessEntityData,OperationVO} from '@/api/pms/platform/businessmodel'
import {getProject} from '@/api/pms/project/projects'
const props=defineProps<{showDeadline?:boolean;current?:BusinessEntityData;busy:boolean;actions:OperationVO[];execute:(code:string,values:Record<string,unknown>)=>Promise<unknown>}>()
const expanded=ref<string[]>([])
const description=ref(''),location=ref<LocationMaintainRequest>(),endDate=ref(''),readingProject=ref(false),error=ref('')
const projectId=computed(()=>{const value=Number(props.current?.fieldValues.projectId);return Number.isSafeInteger(value)&&value>0?value:undefined})
const allowed=(code:string)=>props.actions.some(action=>action.code===code&&action.executable)
watch(()=>props.current,row=>{description.value=String(row?.fieldValues.location||'');endDate.value=String(row?.fieldValues.requiredEndDate||'');location.value=undefined;error.value=''}, {immediate:true})
const saveDeadline=async()=>{
  if(!projectId.value || props.busy || readingProject.value)return
  const selected=props.current;readingProject.value=true;error.value=''
  try{const project=await getProject(projectId.value);if(props.current!==selected)return
    if(project.version==null)throw new Error('项目版本不可用，请重新读取项目')
    await props.execute('deadline',{projectVersion:project.version,endDate:endDate.value})
  }catch(failure){error.value=failure instanceof Error?failure.message:'项目期限读取失败'}
  finally{readingProject.value=false}
}
</script>

<template>
  <ContentWrap>
    <el-form inline class="-mb-15px" @submit.prevent="search">
      <el-form-item label="项目编号"><PmsEntitySelect :model-value="projectId" :api="getProjectPage" label-field="projectName" value-field="id" query-field="projectName" placeholder="请选择项目" disabled class="!w-180px" /></el-form-item>
      <el-form-item label="工勘名称"><el-input v-model="name" clearable class="!w-200px" @keyup.enter="search" /></el-form-item>
      <el-form-item label="状态"><el-select v-model="status" clearable class="!w-160px"><el-option v-for="dict in getIntDictOptions(DICT_TYPE.PMS_SITE_SURVEY_STATUS)" :key="dict.value" :label="dict.label" :value="dict.value" /></el-select></el-form-item>
      <el-form-item><el-button :loading="loading" @click="search"><Icon icon="ep:search" />查询</el-button><el-button v-if="canCreate" type="primary" @click="emit('create')"><Icon icon="ep:plus" />新增工勘</el-button></el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="fieldValues.name" label="工勘名称" min-width="180" />
      <el-table-column prop="fieldValues.location" label="工勘地点" min-width="180" show-overflow-tooltip />
      <el-table-column label="地点状态" width="110"><template #default="{row}"><el-tag :type="row.fieldValues.locationResolutionStatus==='RESOLVED'?'success':'warning'">{{row.fieldValues.locationResolutionStatus || 'UNRESOLVED'}}</el-tag></template></el-table-column>
      <el-table-column prop="fieldValues.surveyDate" label="工勘日期" width="120" />
      <el-table-column label="状态" width="100"><template #default="{row}"><dict-tag :type="DICT_TYPE.PMS_SITE_SURVEY_STATUS" :value="row.fieldValues.status" /></template></el-table-column>
      <el-table-column label="操作" width="320" fixed="right"><template #default="{row}">
        <el-button link type="primary" @click="emit('open',row.ref.entityId,true)">详情</el-button>
        <el-button v-if="can(row,'save')" link type="primary" @click="emit('open',row.ref.entityId,false)">编辑</el-button>
        <el-button v-if="can(row,'confirm')" link type="success" @click="emit('action',row,'confirm')">确认</el-button>
        <el-button v-if="can(row,'reject')" link type="warning" @click="emit('action',row,'reject')">驳回</el-button>
        <el-button v-if="can(row,'delete')" link type="danger" :disabled="!!row.fieldValues.outsourceRequestId" @click="emit('action',row,'delete')">删除</el-button>
      </template></el-table-column>
    </el-table>
    <el-pagination :total="total" :current-page="page" :page-size="20" layout="total, prev, pager, next, jumper" class="float-right mb-15px mt-15px" @current-change="value=>emit('changePage',value)" />
  </ContentWrap>
</template>
<script setup lang="ts">
import {computed,ref} from 'vue'
import {getProjectPage} from '@/api/pms/project/projects'
import PmsEntitySelect from '@/components/PmsEntitySelect/index.vue'
import {DICT_TYPE,getIntDictOptions} from '@/utils/dict'
import type {BusinessEntityData,OperationVO,FieldFilter} from '@/api/pms/platform/businessmodel'
import type {BusinessId} from '@/api/pms/platform/business'
const props=defineProps<{rows:BusinessEntityData[];total:number;page:number;loading:boolean;projectId?:BusinessId;actionsFor:(row?:BusinessEntityData)=>OperationVO[]}>()
const emit=defineEmits<{search:[filters:FieldFilter[]];create:[];open:[id:BusinessId,readonly:boolean];action:[row:BusinessEntityData,code:string];changePage:[page:number]}>()
const name=ref(''),status=ref<number>()
const can=(row:BusinessEntityData,code:string)=>props.actionsFor(row).some(action=>action.code===code&&action.executable)
const canCreate=computed(()=>props.actionsFor().some(action=>action.code==='create'&&action.executable))
const search=()=>{
  const filters:FieldFilter[]=[]
  if(name.value.trim())filters.push({fieldCode:'name',operator:'LIKE',values:[name.value.trim()]})
  if(status.value!=null)filters.push({fieldCode:'status',operator:'EQ',values:[status.value]})
  emit('search',filters)
}
</script>

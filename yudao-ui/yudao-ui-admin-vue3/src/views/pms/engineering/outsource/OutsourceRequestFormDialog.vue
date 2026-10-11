<template>
  <Dialog v-model="visible" :title="form.id ? '编辑外包申请' : '新建外包申请'" width="min(960px, 95vw)" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form ref="formRef" class="outsource-input-form" :model="form" :rules="rules" :disabled="saving" label-width="120px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="项目" prop="projectId">
            <el-input v-if="sourceSurveyId" :model-value="`工勘所属项目 #${form.projectId}`" disabled />
            <PmsEntitySelect
              v-else
              v-model="form.projectId"
              :api="ProjectApi.getProjectPage"
              label-field="projectName"
              value-field="id"
              query-field="projectName"
              placeholder="请选择项目"
              :disabled="!!form.id"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="单号" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 OS-2026-001" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="类型" prop="outsourceType">
            <el-select v-model="form.outsourceType" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_OUTSOURCE_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="工作量" prop="workQuantity">
            <el-input-number v-model="form.workQuantity" :min="0" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="计划开始日期" prop="plannedStartDate">
            <el-date-picker
              v-model="form.plannedStartDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择开始日期"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="计划结束日期" prop="plannedEndDate">
            <el-date-picker
              v-model="form.plannedEndDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择结束日期"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="预估费用" prop="estimatedCost">
            <el-input-number v-model="form.estimatedCost" :min="0" :precision="2" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="实际费用" prop="actualCost">
            <el-input-number v-model="form.actualCost" :min="0" :precision="2" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="币种" prop="currency">
            <el-select v-model="form.currency" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_CURRENCY)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="服务商名称" prop="vendorName">
            <el-input v-model="form.vendorName" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="联系电话" prop="contactPhone">
            <el-input v-model="form.contactPhone" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="申请人" prop="applicantUserId">
            <PmsEntitySelect
              v-model="form.applicantUserId"
              :api="UserApi.getUserPage"
              label-field="nickname"
              value-field="id"
              query-field="nickname"
              placeholder="请选择申请人"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="申请时间" prop="applyTime">
            <el-date-picker
              v-model="form.applyTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择申请时间"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="工作内容" prop="workContent">
            <Editor v-model="form.workContent" height="180px" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="附件" prop="attachmentFiles">
            <NativeAttachments :key="String(form.id || 'new')" kind="outsourceRequest" :entity-id="form.id"
              :readonly="saving || ![0, 4].includes(form.status ?? -1) || !checkPermi(['pms:res-outsource:update'])"
              :legacy="form.attachmentFiles" @changed="emit('attachmentChanged', $event)" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input v-model="form.remark" type="textarea" :rows="2" />
          </el-form-item>
        </el-col>
        <el-col v-if="form.id" :span="12">
          <el-form-item label="触发来源" prop="triggerSource">
            <el-select v-model="form.triggerSource" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_TRIGGER_SOURCE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="emit('save')">保存</el-button>
    </template>
  </Dialog>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { checkPermi } from '@/utils/permission'
import NativeAttachments from '../attachment/NativeAttachments.vue'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import * as ProjectApi from '@/api/pms/project/projects'
import * as UserApi from '@/api/system/user'
import type { OutsourceRequestVO } from '@/api/pms/engineering/outsource'
const props = defineProps<{ modelValue: boolean; form: OutsourceRequestVO; sourceSurveyId?: number | string; saving: boolean; error?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; save: []; attachmentChanged: [owner: { version?: number; status?: number; configLogUrl?: string }] }>()
const visible = computed({ get: () => props.modelValue, set: value => { if (!props.saving) emit('update:modelValue', value) } })
const formRef = ref()
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  code: [{ required: true, message: '请输入单号' }],
  name: [{ required: true, message: '请输入名称' }],
  outsourceType: [{ required: true, message: '请选择类型' }],
  workContent: [{ required: true, message: '请输入工作内容' }],
  applicantUserId: [{ required: true, message: '请选择申请人' }],
  applyTime: [{ required: true, message: '请选择申请时间' }]
}
defineExpose({ validate: () => formRef.value.validate() })
</script>
<style scoped>
@media (max-width: 767px) {
  .outsource-input-form :deep(.el-col-12) { max-width: 100%; flex-basis: 100%; }
  .outsource-input-form :deep(.el-form-item) { display: block; }
  .outsource-input-form :deep(.el-form-item__label) { width: 100% !important; justify-content: flex-start; }
}
</style>

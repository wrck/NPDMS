<template>
  <Dialog v-model="visible" :title="form.id ? '编辑领料申请' : '新建领料申请'" width="min(960px, 95vw)" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form ref="formRef" :model="form" :rules="rules" :disabled="saving || readOnly" label-width="120px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="项目" prop="projectId">
            <el-input v-if="sourceSurveyId" :model-value="`工勘所属项目 #${form.projectId}`" disabled />
            <PmsEntitySelect
              v-else
              v-model="form.projectId"
              @change="form.equipmentId = undefined"
              :api="ProjectApi.getProjectPage"
              label-field="projectName"
              value-field="id"
              query-field="projectName"
              placeholder="请选择项目"
              :disabled="!!form.id || !!nativeBusiness?.projectId()"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="单号" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 MR-2026-001" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="类型" prop="requisitionType">
            <el-select v-model="form.requisitionType" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_MATERIAL_REQ_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="关联设备" prop="equipmentId">
            <ProjectDeviceSelect v-model="form.equipmentId" :project-id="form.projectId" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="产品名称/物料名称" prop="productName"><el-input v-model="form.productName" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="产品编码/物料编码" prop="productCode"><el-input v-model="form.productCode" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="规格型号" prop="specification"><el-input v-model="form.specification" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="数量" prop="quantity">
            <el-input-number v-model="form.quantity" :min="0" :precision="2" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="需求日期" prop="neededDate">
            <el-date-picker
              v-model="form.neededDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择需求日期"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="备件库编号" prop="warehouseId">
            <el-input-number v-model="form.warehouseId" :min="0" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="备件库名称" prop="warehouseName">
            <el-input v-model="form.warehouseName" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="库存状态" prop="stockStatus">
            <el-select v-model="form.stockStatus" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_STOCK_STATUS)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="申请人" prop="applicantUserId">
            <PmsEntitySelect
              v-model="form.applicantUserId"
              :api="UserApi.getUserPage"
              :disabled="!!nativeBusiness"
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
              :disabled="!!nativeBusiness"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择申请时间"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="附件" prop="attachmentFiles">
            <NativeAttachments :key="String(form.id || 'new')" kind="materialRequisition" :entity-id="form.id"
              :readonly="saving || ![0, 4].includes(form.status ?? -1) || !checkPermi(['pms:imp-material-req:update'])"
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
    <component v-if="nativeBusiness && modelValue" :is="nativeBusiness.Form" :key="String(form.id || 'new')" :record="form" kind="material" />
    <template #footer>
      <el-button :disabled="saving" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="readOnly" @click="emit('save')">保存</el-button>
    </template>
  </Dialog>
</template>
<script setup lang="ts">
import { useNativeBusiness } from '@/components/ProjectBusiness/native/nativeBusinessContext'
const nativeBusiness = useNativeBusiness()
import { computed, ref } from 'vue'
import { checkPermi } from '@/utils/permission'
import NativeAttachments from '../attachment/NativeAttachments.vue'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import * as ProjectApi from '@/api/pms/project/projects'
import * as UserApi from '@/api/system/user'
import type { MaterialRequisitionVO } from '@/api/pms/engineering/material-req'
import ProjectDeviceSelect from '@/components/ProjectDeviceSelect/index.vue'
const props = defineProps<{ modelValue: boolean; form: MaterialRequisitionVO; sourceSurveyId?: number | string; saving: boolean; error?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; save: []; attachmentChanged: [owner: { version?: number; status?: number; configLogUrl?: string }] }>()
const visible = computed({ get: () => props.modelValue, set: value => { if (!props.saving) emit('update:modelValue', value) } })
const readOnly = computed(() => !!nativeBusiness && !nativeBusiness.can(props.form.id ? 'save' : 'create', 'material'))
const formRef = ref()
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  code: [{ required: true, message: '请输入单号' }],
  name: [{ required: true, message: '请输入名称' }],
  requisitionType: [{ required: true, message: '请选择类型' }],
  productName: [{ required: true, message: '请输入产品名称/物料名称' }],
  quantity: [{ required: true, message: '请输入数量' }],
  applicantUserId: [{ required: true, message: '请选择申请人' }],
  applyTime: [{ required: true, message: '请选择申请时间' }]
}
defineExpose({ validate: () => formRef.value.validate() })
</script>

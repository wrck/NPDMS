<template>
  <Dialog v-model="visible" title="领料申请明细" width="min(960px, 95vw)">
    <el-descriptions :column="2" border class="mb-15px">
      <el-descriptions-item label="单号">{{ current.code }}</el-descriptions-item>
      <el-descriptions-item label="名称">{{ current.name }}</el-descriptions-item>
      <el-descriptions-item label="项目"><ProjectTag :project-id="current.projectId" /></el-descriptions-item>
      <el-descriptions-item label="类型">
        <dict-tag :type="DICT_TYPE.PMS_MATERIAL_REQ_TYPE" :value="current.requisitionType ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="关联设备">{{ current.equipmentId }}</el-descriptions-item>
      <el-descriptions-item label="产品名称/物料名称">{{ current.productName }}</el-descriptions-item>
      <el-descriptions-item label="产品编码/物料编码">{{ current.productCode }}</el-descriptions-item>
      <el-descriptions-item label="规格型号">{{ current.specification }}</el-descriptions-item>
      <el-descriptions-item label="数量">{{ current.quantity }}</el-descriptions-item>
      <el-descriptions-item label="需求日期">{{ current.neededDate }}</el-descriptions-item>
      <el-descriptions-item label="备件库编号">{{ current.warehouseId }}</el-descriptions-item>
      <el-descriptions-item label="备件库名称">{{ current.warehouseName }}</el-descriptions-item>
      <el-descriptions-item label="库存状态">
        <dict-tag :type="DICT_TYPE.PMS_STOCK_STATUS" :value="current.stockStatus ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="状态">
        <dict-tag :type="DICT_TYPE.PMS_APPROVAL_STATUS" :value="current.status ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="申请人"><UserTag :user-id="current.applicantUserId" /></el-descriptions-item>
      <el-descriptions-item label="申请时间">{{ current.applyTime }}</el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ current.remark }}</el-descriptions-item>
      <el-descriptions-item v-if="current.approveOpinion" label="审批意见" :span="2">
        {{ current.approveOpinion }}
      </el-descriptions-item>
    </el-descriptions>
    <NativeAttachments v-if="current.id" kind="materialRequisition" :entity-id="current.id" :readonly="true" :legacy="current.attachmentFiles" />
  </Dialog>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import NativeAttachments from '../attachment/NativeAttachments.vue'
import type { MaterialRequisitionVO } from '@/api/pms/engineering/material-req'
import { DICT_TYPE } from '@/utils/dict'
import ProjectTag from '@/components/ProjectTag/index.vue'
import UserTag from '@/components/UserTag/index.vue'
const props = defineProps<{ modelValue: boolean; current: Partial<MaterialRequisitionVO> }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
</script>

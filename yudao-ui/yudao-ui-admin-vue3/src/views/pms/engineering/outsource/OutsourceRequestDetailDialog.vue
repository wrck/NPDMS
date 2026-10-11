<template>
  <Dialog v-model="visible" title="外包申请明细" width="min(960px, 95vw)">
    <el-descriptions :column="2" border class="mb-15px">
      <el-descriptions-item label="单号">{{ current.code }}</el-descriptions-item>
      <el-descriptions-item label="名称">{{ current.name }}</el-descriptions-item>
      <el-descriptions-item label="项目">{{ current.projectId }}</el-descriptions-item>
      <el-descriptions-item label="类型">
        <dict-tag :type="DICT_TYPE.PMS_OUTSOURCE_TYPE" :value="current.outsourceType ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="工作量">{{ current.workQuantity }}</el-descriptions-item>
      <el-descriptions-item label="币种">
        <dict-tag :type="DICT_TYPE.PMS_CURRENCY" :value="current.currency ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="预估费用">{{ current.estimatedCost }}</el-descriptions-item>
      <el-descriptions-item label="实际费用">{{ current.actualCost }}</el-descriptions-item>
      <el-descriptions-item label="计划开始">{{ current.plannedStartDate }}</el-descriptions-item>
      <el-descriptions-item label="计划结束">{{ current.plannedEndDate }}</el-descriptions-item>
      <el-descriptions-item label="服务商名称">{{ current.vendorName }}</el-descriptions-item>
      <el-descriptions-item label="联系电话">{{ current.contactPhone }}</el-descriptions-item>
      <el-descriptions-item label="申请人"><UserTag :user-id="current.applicantUserId" /></el-descriptions-item>
      <el-descriptions-item label="申请时间">{{ current.applyTime ? formatDate(current.applyTime) : '-' }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <dict-tag :type="DICT_TYPE.PMS_APPROVAL_STATUS" :value="current.status ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="触发来源">
        <dict-tag :type="DICT_TYPE.PMS_TRIGGER_SOURCE" :value="current.triggerSource ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="工作内容" :span="2">
        <div v-html="current.workContent"></div>
      </el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ current.remark }}</el-descriptions-item>
      <el-descriptions-item v-if="current.approveOpinion" label="审批意见" :span="2">
        {{ current.approveOpinion }}
      </el-descriptions-item>
    </el-descriptions>
  </Dialog>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { OutsourceRequestVO } from '@/api/pms/engineering/outsource'
import { DICT_TYPE } from '@/utils/dict'
import UserTag from '@/components/UserTag/index.vue'
import { formatDate } from '@/utils/formatTime'
const props = defineProps<{ modelValue: boolean; current: Partial<OutsourceRequestVO> }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
</script>

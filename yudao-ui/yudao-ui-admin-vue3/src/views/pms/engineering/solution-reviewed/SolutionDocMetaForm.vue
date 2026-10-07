<template>
  <!-- 方案信息：方案记录的登记字段（名称/类型/版本/客户方案），不是方案正文章节；审核级别由提交时规则判定冻结，界面不再展示区分 -->
  <section class="doc-meta" aria-label="方案信息">
    <el-row :gutter="16">
      <el-col :span="12">
        <el-form-item label="方案名称" prop="name"><el-input v-model="model.name" :disabled="readOnly || !!model.id" /></el-form-item>
      </el-col>
      <el-col :span="12">
        <!-- 任务完成链仅认可 IMPLEMENTATION（SolutionCompletionMapper/SolutionReviewMapper 固定值）；扩充值域须先经规格裁决 -->
        <el-form-item label="方案类型" prop="solutionType">
          <el-select v-model="model.solutionType" class="!w-full" :disabled="readOnly"><el-option label="实施方案" value="IMPLEMENTATION" /></el-select>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="版本标签" prop="versionLabel"><el-input v-model="model.versionLabel" :disabled="readOnly" /></el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="客户方案">
          <el-radio-group v-model="customerPlan.hasCustomerPlan" :disabled="readOnly">
            <el-radio value="yes">已有客户方案</el-radio>
            <el-radio value="no">无</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-col>
      <el-col v-if="customerPlan.hasCustomerPlan === 'yes'" :span="12">
        <el-form-item label="上传方案文件">
          <CustomerSolutionDocumentPicker v-model="pendingFiles" :readonly="readOnly" />
          <div v-if="customerPlan.customerPlanUrl">已保存的方案文件保持不变；保存后替换为新文件。</div>
        </el-form-item>
      </el-col>
      <el-col v-if="customerPlan.hasCustomerPlan === 'yes'" :span="24">
        <el-button disabled title="客户方案系统识别未接入，请下载后人工核对再录入正文">提交系统识别</el-button>
      </el-col>
    </el-row>
  </section>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import CustomerSolutionDocumentPicker from './CustomerSolutionDocumentPicker.vue'
import type { SolutionVO } from '@/api/pms/engineering/solution'

defineOptions({ name: 'SolutionDocMetaForm' })

defineProps<{ readOnly: boolean }>()
const model = defineModel<SolutionVO>({ required: true })
const pendingFiles=defineModel<File[]>('pendingFiles',{default:()=>[]})

// remark 是共享信封列：本组件只拥有 hasCustomerPlan/customerPlanUrl 两键，写回时
// 先解析现值再覆盖，其余键（业务清单/培训/归档等）由章节表单维护，禁止整体覆盖。
const META_REMARK_KEYS = ['hasCustomerPlan', 'customerPlanUrl'] as const
const customerPlan = reactive({ hasCustomerPlan: '', customerPlanUrl: '' })
const parseEnvelope = (raw: unknown): Record<string, unknown> => {
  try {
    const v = raw ? JSON.parse(String(raw)) : null
    return v && typeof v === 'object' && !Array.isArray(v) ? v : {}
  } catch {
    return {}
  }
}
watch(
  () => model.value?.remark,
  (raw) => {
    const envelope = parseEnvelope(raw)
    for (const key of META_REMARK_KEYS) customerPlan[key] = String(envelope[key] ?? '')
  },
  { immediate: true }
)
watch(
  customerPlan,
  () => {
    if (!model.value) return
    model.value.remark = JSON.stringify({ ...parseEnvelope(model.value.remark), ...customerPlan })
  },
  { deep: true }
)
</script>

<template>
  <el-dialog v-model="visible" title="更正项目客户" :width="mobile ? '96%' : '560px'"
    :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close>
    <div v-loading="loading">
      <p class="project-caption">{{ projectLabel }}</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-form v-if="inspection" label-position="top" @submit.prevent="save">
        <el-form-item label="当前客户">
          <el-input :model-value="currentCustomerLabel" readonly />
        </el-form-item>
        <template v-if="inspection.canCorrect">
          <el-form-item label="更正为" required>
            <PmsEntitySelect v-model="customerCode" :api="getSelectableCustomers"
              :label-field="['code', 'name']" value-field="code" query-field="keyword"
              placeholder="按客户名称或编码选择" :disabled="saving" @change="selectCustomer" />
          </el-form-item>
          <el-form-item label="客户编码"><el-input :model-value="customerCode" readonly /></el-form-item>
          <el-form-item label="客户名称"><el-input :model-value="selectedCustomerName" readonly :placeholder="validatingCustomer ? '正在读取客户主档' : ''" /></el-form-item>
          <el-form-item label="更正说明"><el-input v-model="reason" type="textarea" maxlength="500"
            :disabled="saving" placeholder="选填" /></el-form-item>
          <p class="form-helper">仅更正项目客户关联并保留操作记录，不改挂下游业务。</p>
        </template>
        <template v-else>
          <el-alert title="当前项目不能直接更换客户" type="warning" :closable="false"
            :description="inspection.references.length ? '以下现有或历史业务引用需要先明确处理方案，不会自动改挂。' : '项目已关闭，客户关联只读。'" />
          <el-table v-if="inspection.references.length" :data="inspection.references" size="small" class="reference-table">
            <el-table-column prop="label" label="现有业务引用" />
            <el-table-column prop="count" label="记录数" width="90" />
          </el-table>
        </template>
      </el-form>
    </div>
    <template #footer>
      <el-button :disabled="saving" @click="visible = false">取消</el-button>
      <el-button :disabled="loading || saving" @click="inspect">重新检查</el-button>
      <el-button v-if="inspection?.canCorrect" type="primary" :loading="saving"
        :disabled="loading || validatingCustomer || !customerCode" @click="save">保存更正</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { useMessage } from '@/hooks/web/useMessage'
import { inspectCustomerCorrection, correctProjectCustomer, type CustomerCorrectionInspection } from '@/api/pms/project/customer-selected'
import { getSelectableCustomers, type SelectedCustomer } from './customerSelection'
import { createSubmissionIdempotencyState } from '@/views/pms/project/projects/submissionIdempotency'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import { getCustomerByCode } from '@/api/pms/customer'

const emit = defineEmits<{ updated: [] }>()
const mobile = useMediaQuery('(max-width: 767px)')
const message = useMessage()
const visible = ref(false), loading = ref(false), saving = ref(false)
const projectId = ref<number>(), projectLabel = ref(''), customerCode = ref(''), reason = ref(''), error = ref('')
const inspection = ref<CustomerCorrectionInspection>()
const selectedCustomerName = ref(''), validatingCustomer = ref(false)
const submission = createSubmissionIdempotencyState()
let sequence = 0, customerSequence = 0
const currentCustomerLabel = computed(() => [inspection.value?.customerCode, inspection.value?.customerName].filter(Boolean).join(' · ') || '未关联客户')
const selectCustomer = async (code: unknown, customer?: SelectedCustomer) => {
  const request = ++customerSequence
  customerCode.value = customer && customer.code === code ? customer.code : ''
  selectedCustomerName.value = ''
  validatingCustomer.value = false
  if (!customerCode.value) return
  validatingCustomer.value = true
  try {
    const master = await getCustomerByCode(customerCode.value)
    if (request !== customerSequence) return
    if (master.code !== code || master.lifecycleStatus !== 'ENABLED') throw new Error('该客户当前不可用于新关联')
    selectedCustomerName.value = master.name
    error.value = ''
  } catch (failure: any) {
    if (request === customerSequence) {
      customerCode.value = ''
      error.value = failure?.message || '客户主档读取失败，请重新选择'
    }
  } finally { if (request === customerSequence) validatingCustomer.value = false }
}
const inspect = async () => {
  if (!projectId.value) return
  const request = ++sequence
  loading.value = true
  error.value = ''
  inspection.value = undefined
  try {
    const result = await inspectCustomerCorrection(projectId.value)
    if (request === sequence) inspection.value = result
  } catch (failure: any) {
    if (request === sequence) error.value = failure?.message || '引用检查失败，请重试；尚未更改客户。'
  } finally { if (request === sequence) loading.value = false }
}
const open = (project: ProjectMasterVO) => {
  projectId.value = project.id
  projectLabel.value = `${project.projectCode} · ${project.projectName}`
  customerCode.value = reason.value = ''
  selectedCustomerName.value = ''
  customerSequence++
  validatingCustomer.value = false
  submission.reset()
  visible.value = true
  void inspect()
}
const save = async () => {
  if (saving.value || loading.value || validatingCustomer.value || !inspection.value?.canCorrect || !customerCode.value || !projectId.value) return
  saving.value = true
  error.value = ''
  const payload = { projectId: projectId.value, version: inspection.value.version, customerCode: customerCode.value, reason: reason.value.trim() }
  try {
    await correctProjectCustomer(payload.projectId, payload.version, payload.customerCode, payload.reason, submission.keyFor(payload))
    message.success('客户关联已保存，操作记录已留存')
    visible.value = false
    emit('updated')
  } catch (failure: any) { error.value = failure?.message || '更正未成功，请重新检查项目版本和业务引用。' }
  finally { saving.value = false }
}
defineExpose({ open })
</script>
<style scoped>
.project-caption, .form-helper { color: var(--el-text-color-secondary); line-height: 1.5; }
.form-helper { font-size: 12px; }
.reference-table { margin-top: 12px; }
</style>

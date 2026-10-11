<template>
  <ContentWrap :body-style="{ padding: '20px', overflow: 'hidden' }">
    <header class="receipt-heading"><h2>到货签收</h2><p>登记到货情况，上传交付件并记录签收结果。</p></header>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-160px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_ARRIVAL_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button v-if="!readonly" type="primary" @click="openForm()" v-hasPermi="['pms:imp-arrival:create']"
          ><Icon icon="ep:plus" />登记到货</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap :body-style="{ padding: '20px', overflow: 'hidden' }">
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="equipmentId" label="设备编号" width="100" />
      <el-table-column prop="quantity" label="数量" width="80" />
      <el-table-column prop="arrivalTime" label="完成时间" width="160" :formatter="dateFormatter" />
      <el-table-column prop="inspectionResult" label="验收结果" min-width="180" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_ARRIVAL_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:imp-arrival:query']"
            >{{ editableRecord(row) && !readonly ? '编辑' : '查看' }}</el-button
          >
          <el-button
            link
            type="warning"
            v-if="!readonly && row.status === 0"
            @click="handleAction(row, 'markAbnormal')"
            v-hasPermi="['pms:imp-arrival:update']"
            >标记异常</el-button
          >
          <el-button v-if="!readonly && row.status !== 1" link type="danger" @click="remove(row)" v-hasPermi="['pms:imp-arrival:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>

  <Dialog v-model="formVisible" :title="form.id ? (readOnly ? '查看签收' : '编辑签收') : '登记到货'" width="min(640px, 95vw)">
    <el-form class="receipt-form" :class="{ 'receipt-readonly': readOnly }" ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="readOnly">
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-form-item label="到货人">
            <span class="auto-value">{{ receiverName }}</span>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="完成时间">
            <span class="auto-value">{{ completionTimeText }}</span>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="上传交付件" prop="attachmentUrl">
            <PmsFileReferenceList
              v-if="form.id && form.signDocumentArtifactId"
              owner-context="IMP" object-type="ARRIVAL" :object-id="String(form.id)"
              purpose-code="ARRIVAL_SIGN_DOCUMENT" reference-key="arrival-sign-document"
              :artifact-id="form.signDocumentArtifactId" :version-no="form.signDocumentVersionNo" />
            <template v-if="form.attachmentUrl">
              <a
                v-for="url in form.attachmentUrl.split(',').filter(Boolean)" :key="url" :href="url" target="_blank" rel="noopener">查看历史附件</a>
            </template>
            <el-upload
              v-if="!readOnly" v-model:file-list="uploadFiles" :auto-upload="false" :limit="1"
              :disabled="saving" accept=".pdf,.png,.jpg,.jpeg,.doc,.docx,.txt"
              :on-change="selectAttachment" :on-remove="removeAttachment">
              <el-button :disabled="saving">选择签收单文件</el-button>
              <template #tip><div class="el-upload__tip">保存时上传并自动关联项目交付件；支持 PDF、图片、Word、TXT，最大 50 MB。</div></template>
            </el-upload>
            <span v-else-if="!form.attachmentUrl && !form.signDocumentArtifactId">未上传</span>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="formVisible = false">{{ readOnly ? '关闭' : '取消' }}</el-button>
      <el-button v-if="!readOnly" type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import * as ArrivalApi from '@/api/pms/engineering/arrival'
import type { ArrivalVO } from '@/api/pms/engineering/arrival'
import * as UserApi from '@/api/system/user'
import { useUserStore } from '@/store/modules/user'
import { checkPermi } from '@/utils/permission'
import { dateFormatter, formatDate } from '@/utils/formatTime'
import type { UploadFile, UploadFiles, UploadUserFile } from 'element-plus'
import { PmsFileReferenceList } from '@/components/PmsFileArtifact'
import { createArrivalUploadAttempt, saveArrivalReceipt, type ArrivalUploadAttempt } from './saveArrivalReceipt'

defineOptions({ name: 'ProjectArrivalReceiptPanel' })
const props = defineProps<{ projectId: number; /** 任务所属阶段未进入时由工作区强制只读：隐藏业务写操作，查看照常 */ readonly?: boolean }>()
const userStore = useUserStore()
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<ArrivalVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, projectId: props.projectId, code: '', status: undefined })
const formVisible = ref(false)
const formRef = ref()
const uploadFiles = ref<UploadUserFile[]>([])
let uploadAttempt: ArrivalUploadAttempt | undefined
const detailLoading = ref(false)
const detailError = ref(false)
let detailSequence = 0
type ArrivalForm = Omit<ArrivalVO, 'arrivalTime'> & { arrivalTime?: string | number | null }
const form = ref<ArrivalForm>({ projectId: props.projectId, code: '', status: 0 })
const editableRecord = (row: Pick<ArrivalVO, 'status'>) => (row.status === 0 || row.status === 2) && checkPermi(['pms:imp-arrival:update'])
const readOnly = computed(() => detailLoading.value || detailError.value || !!props.readonly || (form.value.id ? !editableRecord(form.value) : !checkPermi(['pms:imp-arrival:create'])))
const rules = {
  attachmentUrl: [{ validator: (_rule: unknown, _value: unknown, callback: (error?: Error) => void) =>
    callback(uploadAttempt || form.value.signDocumentArtifactId ? undefined : new Error('请选择签收单文件')) }]
}
const receiverName = ref('')
// 到货人默认当前用户；承接历史记录中他人签收时按编号回显昵称
const resolveReceiverName = async (receiverUserId?: number) => {
  const current = userStore.getUser
  const fallback = current.nickname || (current.id != null ? String(current.id) : '当前用户')
  if (receiverUserId == null || String(receiverUserId) === String(current.id ?? '')) {
    receiverName.value = fallback
    return
  }
  try {
    const user = await UserApi.getSimpleUser(receiverUserId)
    receiverName.value = user?.nickname || `用户 ${receiverUserId}`
  } catch {
    receiverName.value = `用户 ${receiverUserId}`
  }
}
const completionTimeText = computed(() => {
  const value = form.value.arrivalTime
  return value == null || value === '' ? '上传交付件后自动填入' : formatDate(Number(value))
})

const load = async () => {
  loading.value = true
  try {
    const data = await ArrivalApi.getArrivalPage({ ...query, projectId: props.projectId })
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = async (row?: ArrivalVO) => {
  if (saving.value) return
  const sequence = ++detailSequence
  detailLoading.value = false
  uploadFiles.value = []; uploadAttempt = undefined
  detailError.value = false
  form.value = {
      id: undefined,
      projectId: props.projectId,
      code: '',
      arrivalTime: undefined,
      receiverUserId: userStore.getUser.id || undefined,
      equipmentId: undefined,
      quantity: undefined,
      inspectionResult: '',
      exceptionRecord: '',
      remark: '',
      version: undefined,
      status: 0,
      ...row,
      // UploadFile derives string/array output from its initial value. Legacy
      // records with NULL evidence must still use this API's string contract.
      attachmentUrl: row?.attachmentUrl ?? ''
  }
  resolveReceiverName(form.value.receiverUserId)
  formVisible.value = true
  if (row?.id) {
    detailLoading.value = true
    try {
      const detail = await ArrivalApi.getArrival(row.id)
      if (sequence === detailSequence) form.value = { ...form.value, ...detail }
    }
    catch { if (sequence === detailSequence) { detailError.value = true; message.error('签收详情加载失败，请关闭后重试') } }
    finally { if (sequence === detailSequence) detailLoading.value = false }
  }
}
const selectAttachment = (file: UploadFile, list: UploadFiles) => {
  if (readOnly.value || !file.raw) return
  if (!/\.(pdf|png|jpe?g|docx?|txt)$/i.test(file.name) || file.size! <= 0 || file.size! > 50 * 1024 * 1024) {
    list.splice(list.indexOf(file), 1); message.error('文件格式不支持或大小超过 50 MB'); return
  }
  uploadAttempt = createArrivalUploadAttempt(file.raw)
}
const removeAttachment = () => { uploadAttempt = undefined }
const save = async () => {
  if (readOnly.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    form.value.projectId = props.projectId
    await saveArrivalReceipt(form.value as ArrivalVO, uploadAttempt)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } catch (failure: any) {
    message.error(failure?.message || '保存失败，请重试；已上传文件会保留')
  } finally {
    saving.value = false
  }
}
const remove = async (row: ArrivalVO) => {
  if (row.status === 1) return
  await message.delConfirm()
  await ArrivalApi.deleteArrival(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (row: ArrivalVO, action: 'markAbnormal') => {
  const actionText = { markAbnormal: '标记异常' }[action]
  await message.confirm(`确认${actionText}该签收记录？`)
  if (action === 'markAbnormal') await ArrivalApi.markAbnormalArrival(row.id!)
  message.success(`${actionText}成功`)
  await load()
}
onMounted(load)
</script>

<style scoped>
.receipt-heading { margin-bottom: 20px; }
.receipt-heading h2 { margin: 0 0 6px; font-size: 16px; font-weight: 600; }
.receipt-heading p { margin: 0; color: var(--el-text-color-secondary); font-size: 13px; }
.auto-value { color: var(--el-text-color-regular); line-height: 32px; }
.receipt-form :deep(.el-form-item__content > div:has(.el-upload-list)) { width: 100%; }
</style>

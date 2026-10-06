<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item v-if="!props.projectId" label="项目编号" prop="projectId">
        <PmsEntitySelect
          v-model="query.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="projectName"
          value-field="id"
          query-field="projectName"
          placeholder="请选择项目"
          class="!w-180px"
        />
      </el-form-item>
      <el-form-item label="方案名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_APPROVAL_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:sol-solution:create']"
          ><Icon icon="ep:plus" />新增方案</el-button
        >
      </el-form-item>
    </el-form>
    <el-alert title="编辑界面按项目交付 Demo 4.1 组织：第 1~8 章为方案内容，底部为页面功能按钮（保存草稿/生成方案/下载方案/提交审核）；重大方案复审未接入，本地状态不等同于新项目方案门禁事实。" type="info" :closable="false" />
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="name" label="方案名称" min-width="180" />
      <el-table-column prop="reviewLevel" label="审核级别" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_REVIEW_LEVEL" :value="row.reviewLevel" />
        </template>
      </el-table-column>
      <el-table-column prop="versionLabel" label="版本" width="90" />
      <el-table-column prop="baselineVersion" label="基线版本" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_APPROVAL_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="420" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:sol-solution:query']"
            >{{ editableDraft(row) ? '编辑' : '查看' }}</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleSimpleAction(row, 'submit')"
            v-hasPermi="['pms:sol-solution:update']"
            >提交</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 1"
            @click="handleSimpleAction(row, 'startReview')"
            v-hasPermi="['pms:sol-solution:update']"
            >开始审核</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 2"
            :disabled="row.reviewLevel !== 0"
            :title="row.reviewLevel !== 0 ? '重大方案复审尚未接入，不能直接通过' : undefined"
            @click="openApprove(row, 'approve')"
            v-hasPermi="['pms:sol-solution:audit']"
            >{{ row.reviewLevel === 0 ? '通过' : '复审待接入' }}</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 2"
            @click="openApprove(row, 'reject')"
            v-hasPermi="['pms:sol-solution:audit']"
            >驳回</el-button
          >
          <el-button
            link
            type="info"
            v-if="row.status === 2"
            @click="handleSimpleAction(row, 'withdraw')"
            v-hasPermi="['pms:sol-solution:update']"
            >撤回</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 2"
            @click="handleSimpleAction(row, 'terminate')"
            v-hasPermi="['pms:sol-solution:update']"
            >终止</el-button
          >
          <el-button v-if="row.status === 0" link type="danger" @click="remove(row)" v-hasPermi="['pms:sol-solution:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? (readOnly ? '查看方案' : '编辑方案') : '新增方案'" :loading="detailLoading" width="min(1080px, 96vw)">
    <el-alert v-if="detailError" :title="detailError" type="error" :closable="false">
      <el-button @click="openForm(form)">重新加载</el-button>
    </el-alert>
    <el-alert v-if="saveError" :title="saveError" type="error" :closable="false" class="mb-12px" />
    <el-descriptions v-if="form.id && !detailError" title="当前记录审核信息" :column="2" border class="mb-16px" data-testid="solution-review-result">
      <el-descriptions-item label="状态"><dict-tag :type="DICT_TYPE.PMS_APPROVAL_STATUS" :value="form.status ?? ''" /></el-descriptions-item>
      <el-descriptions-item label="记录版本">{{ form.version ?? '—' }}</el-descriptions-item>
      <el-descriptions-item label="基线版本">{{ form.baselineVersion ?? '—' }}</el-descriptions-item>
      <el-descriptions-item label="审核时间">{{ form.approvedTime ? formatDate(form.approvedTime) : '—' }}</el-descriptions-item>
      <el-descriptions-item label="审核人编号">{{ form.approvedBy ?? '—' }}</el-descriptions-item>
      <el-descriptions-item label="审核意见" :span="2"><span class="whitespace-pre-wrap break-words">{{ form.approvalOpinion || '—' }}</span></el-descriptions-item>
    </el-descriptions>
    <el-form v-if="!detailError" ref="formRef" :model="form" :rules="rules" label-width="100px" :disabled="readOnly">
      <el-form-item v-if="!props.projectId" label="项目编号" prop="projectId">
        <PmsEntitySelect
          v-model="form.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="projectName"
          value-field="id"
          query-field="projectName"
          placeholder="请选择项目"
          :disabled="!!form.id"
        />
      </el-form-item>
      <SolutionChapterForm v-model="form" v-model:pending-files="pendingCustomerFiles" :read-only="readOnly" :saving="saving" @save="save" @submit-review="submitReview" />
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="formVisible = false">取消</el-button>
      <el-button v-if="!readOnly" type="primary" :loading="saving" @click="save">保存草稿</el-button>
    </template>
  </Dialog>

  <Dialog v-model="approveVisible" :title="approveAction === 'approve' ? '审核通过' : '审核驳回'" width="520px">
    <el-form :model="approveForm" label-width="100px">
      <el-form-item label="审核意见">
        <el-input v-model="approveForm.approvalOpinion" type="textarea" :rows="4" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="approveVisible = false">取消</el-button>
      <el-button :type="approveAction === 'approve' ? 'success' : 'warning'" :loading="saving" @click="submitApprove"
        >确认</el-button
      >
    </template>
  </Dialog>

</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import * as SolutionApi from '@/api/pms/engineering/solution'
import type { SolutionApproveVO, SolutionVO } from '@/api/pms/engineering/solution'
import * as ProjectApi from '@/api/pms/project/projects'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import { checkPermi } from '@/utils/permission'
import { buildRecordName } from '../recordNaming'
import { formatDate } from '@/utils/formatTime'
import SolutionChapterForm from './SolutionChapterForm.vue'
import { saveCustomerSolutionDocuments } from '../solution-reviewed/saveCustomerSolutionDocuments'
import type { DeliveryUploadAttempt } from '@/components/DeliveryArtifact/uploadDeliveryFile'

defineOptions({ name: 'PmsEngSolution' })
const props = defineProps<{ projectId?: number }>()
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<SolutionVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, projectId: props.projectId ?? '', name: '', status: undefined })
const formVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const saveError = ref('')
let detailSequence = 0
const formRef = ref()
const form = ref<SolutionVO>({ projectId: 0, name: '', reviewLevel: 0, status: 0 })
const editableDraft = (row: SolutionVO) => row.status === 0 && checkPermi(['pms:sol-solution:update'])
const readOnly = computed(() => saving.value || detailLoading.value || !!detailError.value || (form.value.id ? !editableDraft(form.value) : !checkPermi(['pms:sol-solution:create'])))
const pendingCustomerFiles = ref<File[]>([])
const customerAttempts: DeliveryUploadAttempt[] = []
let savedCustomerDraft: string | undefined
const parseEnvelope = (raw?: string): Record<string, unknown> => {
  try { const value = raw ? JSON.parse(raw) : null; return value && typeof value === 'object' && !Array.isArray(value) ? value : {} }
  catch { return {} }
}
const rules = {
  projectId: [
    { required: true, message: '请选择项目' },
    { validator: (_rule: unknown, value: number | string, callback: (error?: Error) => void) => callback(Number(value) > 0 ? undefined : new Error('请选择项目')) }
  ],
  name: [{ required: true, message: '请输入方案名称' }]
}

// 新建草稿时方案名称按“项目编码_项目名称_实施方案_(版本标签|年月日时分秒)”自动生成；编辑中或用户已改名称时不覆盖
const lastAutoName = ref('')
const projectInfoCache = new Map<number, ProjectMasterVO>()
const autoFillName = async (projectId: number | string) => {
  const pid = Number(projectId)
  if (!pid || pid <= 0 || form.value.id) return
  if (form.value.name && form.value.name !== lastAutoName.value) return
  let project = projectInfoCache.get(pid)
  if (!project) {
    try {
      project = await ProjectApi.getProject(pid)
      projectInfoCache.set(pid, project)
    } catch {
      return // 项目信息获取失败时保持手输
    }
  }
  if (Number(form.value.projectId) !== pid || form.value.id) return
  if (form.value.name && form.value.name !== lastAutoName.value) return
  form.value.name = buildRecordName({
    projectCode: project.projectCode,
    projectName: project.projectName,
    typeLabel: '实施方案',
    version: form.value.versionLabel
  })
  lastAutoName.value = form.value.name
}
watch(() => form.value.projectId, pid => { autoFillName(pid) })
watch(() => form.value.versionLabel, () => { autoFillName(form.value.projectId) })

const approveVisible = ref(false)
const approveAction = ref<'approve' | 'reject'>('approve')
const approveForm = reactive<SolutionApproveVO>({ id: 0, approvalOpinion: '', version: undefined })

const load = async () => {
  loading.value = true
  try {
    const data = await SolutionApi.getSolutionPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = async (row?: SolutionVO) => {
  if (saving.value) return
  pendingCustomerFiles.value = []; customerAttempts.splice(0); savedCustomerDraft = undefined
  saveError.value = ''
  const sequence = ++detailSequence
  detailError.value = ''
  detailLoading.value = !!row?.id
  // Replace the whole object so a previous reviewed record cannot leak its
  // status, baseline or approval metadata into a newly created draft.
  form.value = {
      projectId: props.projectId ?? 0,
      name: '',
      solutionType: 'IMPLEMENTATION',
      background: '',
      target: '',
      team: '',
      inventory: '',
      plan: '',
      topology: '',
      interfacePlan: '',
      ipPlan: '',
      versionLabel: '',
      script: '',
      quality: '',
      risk: '',
      oAndM: '',
      reviewLevel: 0,
      remark: '',
      version: undefined,
      status: 0,
      id: row?.id
  }
  formVisible.value = true
  if (!row?.id) {
    autoFillName(form.value.projectId)
    return
  }
  try {
    const detail: SolutionVO | null = await SolutionApi.getSolution(row.id)
    if (sequence !== detailSequence) return
    if (!detail || detail.id !== row.id) {
      detailError.value = '方案不存在或已不可见，请关闭后刷新列表'
      return
    }
    form.value = detail
  } catch {
    if (sequence === detailSequence) detailError.value = '方案详情加载失败，请重试；当前不能保存'
  } finally {
    if (sequence === detailSequence) detailLoading.value = false
  }
}
watch(formVisible, visible => { if (!visible) ++detailSequence })
onBeforeUnmount(() => { ++detailSequence })
const draftFingerprint = () => JSON.stringify({ ...form.value, version: undefined })
const persistDraftAndCustomerFiles = async () => {
  const withFiles = pendingCustomerFiles.value.length > 0 && parseEnvelope(form.value.remark).hasCustomerPlan === 'yes'
  if (!withFiles) {
    if (form.value.id) await SolutionApi.updateSolution(form.value)
    else form.value.id = await SolutionApi.createSolution(form.value)
    return
  }
  if (savedCustomerDraft !== draftFingerprint()) {
    const existingId = form.value.id, previousVersion = form.value.version
    if (existingId) await SolutionApi.updateSolution(form.value)
    else form.value.id = await SolutionApi.createSolution(form.value)
    const current = await SolutionApi.getSolution(form.value.id!)
    if (!current || current.id !== form.value.id || current.status !== 0 || current.version === undefined
      || (existingId && previousVersion !== undefined && current.version !== previousVersion + 1)) {
      throw new Error('方案版本或状态已变化，请刷新后重试')
    }
    form.value.version = current.version
    savedCustomerDraft = draftFingerprint()
  }
  const { attached } = await saveCustomerSolutionDocuments({ id: form.value.id!, version: form.value.version! }, pendingCustomerFiles.value, customerAttempts)
  form.value.version = attached.version
  form.value.remark = JSON.stringify({ ...parseEnvelope(form.value.remark), hasCustomerPlan: 'yes', customerPlanUrl: attached.customerPlanUrl })
  pendingCustomerFiles.value = []; customerAttempts.splice(0); savedCustomerDraft = undefined
}
const save = async () => {
  if (readOnly.value) return
  await formRef.value.validate()
  saving.value = true
  saveError.value = ''
  try {
    await persistDraftAndCustomerFiles()
    message.success('保存成功')
    formVisible.value = false
    await load()
  } catch (error: any) {
    saveError.value = error?.message || '保存失败，请重试'
  } finally {
    saving.value = false
  }
}
// Demo 4.1 提交审核：保存后提交，服务经理进行审核，重大项目自动推送总部复审
const submitReview = async () => {
  if (readOnly.value) return
  await formRef.value.validate()
  saving.value = true
  saveError.value = ''
  try {
    await persistDraftAndCustomerFiles()
    await SolutionApi.submitSolution(form.value.id!)
    message.success('已提交审核；等待服务经理进行审核')
    formVisible.value = false
    await load()
  } catch (error: any) {
    saveError.value = error?.message || '保存或提交失败，请重试'
  } finally {
    saving.value = false
  }
}
const remove = async (row: SolutionVO) => {
  if (row.status !== 0) return
  await message.delConfirm()
  await SolutionApi.deleteSolution(row.id!)
  message.success('删除成功')
  await load()
}
const handleSimpleAction = async (
  row: SolutionVO,
  action: 'submit' | 'startReview' | 'withdraw' | 'terminate'
) => {
  const actionText = { submit: '提交', startReview: '开始审核', withdraw: '撤回', terminate: '终止' }[action]
  await message.confirm(`确认${actionText}方案【${row.name}】？`)
  if (action === 'submit') await SolutionApi.submitSolution(row.id!)
  if (action === 'startReview') await SolutionApi.startReviewSolution(row.id!)
  if (action === 'withdraw') await SolutionApi.withdrawSolution(row.id!)
  if (action === 'terminate') await SolutionApi.terminateSolution(row.id!)
  message.success(`${actionText}成功`)
  await load()
}
const openApprove = (row: SolutionVO, action: 'approve' | 'reject') => {
  if (action === 'approve' && row.reviewLevel !== 0) {
    message.warning('重大方案复审尚未接入，不能直接通过')
    return
  }
  approveAction.value = action
  Object.assign(approveForm, { id: row.id, approvalOpinion: '', version: row.version })
  approveVisible.value = true
}
const submitApprove = async () => {
  saving.value = true
  try {
    if (approveAction.value === 'approve') {
      await SolutionApi.approveSolution({ id: approveForm.id, approvalOpinion: approveForm.approvalOpinion, version: approveForm.version })
    } else {
      await SolutionApi.rejectSolution({ id: approveForm.id, approvalOpinion: approveForm.approvalOpinion, version: approveForm.version })
    }
    message.success('操作成功')
    approveVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>

<template>
  <el-form inline class="-mb-15px satisfaction-query">
    <el-form-item v-if="!props.readonly">
      <el-button v-hasPermi="['pms:acceptance:satisfaction:manage']" type="primary"
        :loading="starting || preparingStart" :disabled="!canWrite || !context.projectId" @click="openStart">
        手动发起
      </el-button>
    </el-form-item>
    <el-form-item v-if="!context.scoped" label="项目">
      <el-input-number
        v-if="!context.scoped"
        v-model="projectId"
        :min="1"
        controls-position="right"
        placeholder="项目ID"
        class="!w-220px"
      />
      <el-input v-else :model-value="`项目 #${props.projectId}`" disabled class="!w-220px" />
    </el-form-item>
    <el-form-item><el-button :loading="loading" @click="load"><Icon icon="ep:refresh" />{{ context.scoped ? '刷新任务' : '查询' }}</el-button></el-form-item>
    <el-form-item v-if="canCreate"><el-button type="primary" :loading="creating" @click="openCreate">发起满意度调查</el-button></el-form-item>
  </el-form>
  <el-dialog v-model="createVisible" title="发起满意度调查" width="min(560px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!creating" :show-close="!creating">
    <el-form label-position="top" :disabled="creating || !canCreate">
      <el-form-item label="已发布问卷">
        <el-select v-model="selectedRevisionId" class="!w-full" placeholder="选择问卷版本">
          <el-option v-for="option in publishedQuestionnaires" :key="option.revisionId" :label="option.label" :value="option.revisionId" />
        </el-select>
      </el-form-item>
      <el-empty v-if="!publishedQuestionnaires.length" description="暂无已发布问卷，请先在问卷模板中配置并发布。" :image-size="60" />
      <p>本次调查使用所选版本的题目和评分规则，由当前用户负责采集，可在创建后指派。</p>
    </el-form>
    <template #footer>
      <el-button :disabled="creating" @click="createVisible = false">取消</el-button>
      <el-button type="primary" :loading="creating" :disabled="!canCreate || !selectedRevisionId" @click="createCollection">创建调查</el-button>
    </template>
  </el-dialog>
  <el-alert
    v-if="!context.valid"
    title="项目上下文无效，未查询其他项目。"
    type="warning"
    :closable="false"
  />
  <el-alert v-else-if="errorText" :title="errorText" type="error" :closable="false" />
  <el-skeleton v-else-if="loading" :rows="4" animated />
  <el-empty v-else-if="!tasks.length" description="暂无分配给您的满意度调查任务。有管理权限时可选择项目手动发起首轮，无需等待初验。" />
  <el-table v-else :data="tasks" stripe>
    <el-table-column prop="collectionKey" label="调查编号" min-width="180" show-overflow-tooltip />
    <el-table-column v-if="!context.scoped" prop="projectId" label="项目ID" min-width="150" />
    <el-table-column prop="revisionNo" label="轮次" width="80" />
    <el-table-column prop="assignedToUserId" label="责任人" min-width="130" />
    <el-table-column label="任务状态" min-width="120"><template #default="{ row }"><el-tag>{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
    <el-table-column label="问卷状态" min-width="100"><template #default="{ row }">{{ statusLabel(row.questionnaireStatus) }}</template></el-table-column>
    <el-table-column v-if="canWrite" label="操作" width="340" fixed="right">
      <template #default="scope">
        <el-button link type="primary" @click="openAssign(scope.row)">指派</el-button>
        <el-button link type="primary" @click="openGrant(scope.row)">发送问卷</el-button>
        <el-button link type="primary" @click="openAssisted(scope.row)">现场协助</el-button>
        <el-button v-if="scope.row.resultId" link type="warning" @click="openRecollect(scope.row)"
          >整改重收</el-button
        >
      </template>
    </el-table-column>
  </el-table>

  <ManualStartDialog v-model="startVisible" :options="startOptions" :submitting="starting" :error="errorText" @submit="startTask" />
  <el-dialog v-model="assignVisible" title="指派采集责任人" width="min(460px, 94vw)">
    <el-form label-position="top" :disabled="!canWrite"
      ><el-form-item label="采集责任人"
        ><PmsEntitySelect v-model="assignedUserId" :api="getUserPage" label-field="nickname" value-field="id" query-field="nickname" placeholder="选择项目参与人" /></el-form-item
    ></el-form>
    <template #footer
      ><el-button @click="assignVisible = false">取消</el-button
      ><el-button type="primary" :disabled="!canWrite" @click="assign"
        >确认指派</el-button
      ></template
    >
  </el-dialog>

  <el-dialog v-model="grantVisible" title="受控问卷链接" width="min(640px, 94vw)" destroy-on-close @closed="closeGrant">
    <template v-if="!grantUrl">
      <el-form label-position="top" :disabled="!canWrite"
        ><el-form-item label="有效期"
          ><el-date-picker
            v-model="grantExpiresAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item
      ></el-form>
      <el-alert
        title="链接只在本次创建后显示，请立即交付给客户。"
        type="warning"
        :closable="false"
        show-icon
      />
    </template>
    <div v-else class="grant-result">
      <Qrcode v-if="customerGrantUrl" :key="customerGrantUrl" :text="customerGrantUrl" :width="200" @done="url => grantQrImage = url" />
      <p>客户扫码后可直接填写满意度问卷并手写签字。</p>
      <a v-if="grantQrImage && customerGrantUrl" :href="grantQrImage" download="满意度调查二维码.png">保存二维码</a>
      <el-form label-position="top" class="grant-address">
        <el-form-item label="客户可访问的系统地址">
          <el-input v-model="publicBaseUrl" placeholder="例如 https://pms.example.com" />
          <span>请使用客户手机可访问的域名或局域网地址。</span>
        </el-form-item>
      </el-form>
      <el-alert v-if="!customerGrantUrl" title="请输入有效的 HTTP 或 HTTPS 系统地址。" type="error" :closable="false" />
      <el-alert v-else-if="isLoopbackAddress" title="当前地址仅限本机访问；客户用其他手机扫码前，请修改系统地址。" type="warning" :closable="false" />
      <p>有效期至 {{ formatDate(new Date(grantExpiresAt)) }}。请在关闭前保存二维码或复制链接。</p>
      <el-input :model-value="customerGrantUrl" readonly
        ><template #append><el-button @click="copyLink">复制</el-button></template></el-input
      >
      <el-link v-if="customerGrantUrl" :href="customerGrantUrl" target="_blank" rel="noopener noreferrer" type="primary">打开客户问卷</el-link>
    </div>
    <template #footer
      ><el-button @click="closeGrant">关闭</el-button
      ><el-button v-if="!grantUrl" type="primary" :loading="grantCreating" :disabled="!canWrite" @click="createGrant"
        >创建链接</el-button
      ></template
    >
  </el-dialog>

  <el-dialog v-model="assistedVisible" title="现场协助填写问卷" width="min(720px, 94vw)" destroy-on-close>
    <el-alert
      title="请按客户反馈填写本轮问卷，并上传客户签字。提交后答卷不可修改。"
      type="info"
      :closable="false"
    />
    <el-form label-position="top" class="dialog-form" :disabled="!canWrite">
      <el-form-item label="客户联系人"
        ><el-input v-model="assisted.customerContactRef" data-testid="assisted-customer-contact"
      /></el-form-item>
      <QuestionnaireFields ref="questionnaireRef" :frozen-questions="selected?.frozenQuestions" />
      <el-form-item label="客户签字（必填）">
        <el-upload
          v-model:file-list="assistedSignatureFiles"
          data-testid="assisted-signature-upload"
          :auto-upload="false"
          :limit="1"
          accept=".png,.jpg,.jpeg,.pdf"
        >
          <el-button>选择签字文件</el-button>
          <template #tip
            ><div class="el-upload__tip">支持 PNG、JPEG 或 PDF，最多 10 MB</div></template
          >
        </el-upload>
      </el-form-item>
      <el-form-item label="补充附件（可选）">
        <el-upload
          v-model:file-list="assistedAttachmentFiles"
          data-testid="assisted-attachment-upload"
          :auto-upload="false"
          :limit="10"
          multiple
          accept=".png,.jpg,.jpeg,.pdf"
        >
          <el-button>选择附件</el-button>
          <template #tip><div class="el-upload__tip">单个文件最多 50 MB</div></template>
        </el-upload>
      </el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="assistedVisible = false">取消</el-button
      ><el-button
        type="primary"
        data-testid="assisted-submit"
        :loading="assistedSubmitting"
        :disabled="!canWrite"
        @click="submitAssisted"
        >上传并提交</el-button
      ></template
    >
  </el-dialog>

  <el-dialog v-model="recollectVisible" title="登记整改并重收" width="min(620px, 94vw)">
    <el-form label-position="top" :disabled="!canWrite">
      <el-form-item label="整改证据摘要"
        ><el-input v-model="recollectForm.evidenceSummary" type="textarea" :rows="4"
      /></el-form-item>
      <el-form-item label="证据文件事实版本（可选）"
        ><el-input v-model="recollectForm.evidenceFileFactVersion"
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="recollectVisible = false">取消</el-button
      ><el-button type="primary" :disabled="!canWrite" @click="submitRecollect"
        >创建下一轮</el-button
      ></template
    >
  </el-dialog>
</template>

<script setup lang="ts">
import { generateUUID } from '@/utils'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { Qrcode } from '@/components/Qrcode'
import { getTenantId } from '@/utils/auth'
import { checkPermi } from '@/utils/permission'
import { formatDate } from '@/utils/formatTime'
import * as Api from '@/api/pms/acceptance/satisfaction'
import type { TaskView } from '@/api/pms/acceptance/satisfaction'
import type { UploadUserFile } from 'element-plus'
import { satisfactionProjectContext, type SatisfactionViewProps } from './projectContext'
import { getUserPage } from '@/api/system/user'
import QuestionnaireFields from './QuestionnaireFields.vue'
import ManualStartDialog from './ManualStartDialog.vue'
const questionnaireRef = ref<InstanceType<typeof QuestionnaireFields>>()
const statusLabel = (status?: string) => ({ PENDING_ASSIGNMENT: '待指派', PENDING_COLLECTION: '待采集', ASSIGNED: '已指派', PENDING_DECISION: '待判定', PENDING_ARCHIVE: '待归档', FAILED: '未达标', ACTIVE: '可填写', SUBMITTED: '已提交', ARCHIVED: '已归档', INVALIDATED: '已失效' }[status || ''] || status || '—')

const props = defineProps<SatisfactionViewProps>()
const emit = defineEmits<{ 'dirty-change': [value: boolean]; changed: [] }>()

const message = useMessage()
const loading = ref(false)
const starting = ref(false)
const startOperationIds = new Map<string, string>()
const startVisible = ref(false)
const preparingStart = ref(false)
const startOptions = ref<Api.ManualStartOptions>({ configured: false, tasks: [] })
const projectId = ref<number>()
const context = computed(() => satisfactionProjectContext(props.projectId, projectId.value))
const canWrite = computed(() => !props.readonly && context.value.valid)
const canCreate = computed(() => canWrite.value && checkPermi(['pms:acceptance:satisfaction:manage']))
const createVisible = ref(false)
const creating = ref(false)
const createContext = ref<Api.IndependentCollectionContext>()
const selectedRevisionId = ref<number>()
const publishedQuestionnaires = ref<{ templateId: number; revisionId: number; label: string }[]>([])
const createKey = ref('')
const errorText = ref('')
let loadSequence = 0
let contextVersion = 0
const writableTask = (task?: TaskView) =>
  !!task && canWrite.value && (!context.value.scoped || task.projectId === props.projectId)
const tasks = ref<TaskView[]>([])
const selected = ref<TaskView>()
const assignVisible = ref(false)
const assignedUserId = ref<number>()
const grantVisible = ref(false)
const grantExpiresAt = ref('')
const grantUrl = ref('')
const grantCreating = ref(false)
const grantQrImage = ref('')
const publicBaseUrl = ref(import.meta.env.VITE_CUSTOMER_CONFIRM_BASE_URL || window.location.origin)
const customerGrantUrl = computed(() => {
  if (!grantUrl.value) return ''
  try {
    const base = new URL(publicBaseUrl.value)
    if (!['http:', 'https:'].includes(base.protocol) || base.username || base.password) return ''
    const link = new URL(grantUrl.value)
    return new URL(link.pathname + link.search, base.origin).href
  } catch { return '' }
})
const isLoopbackAddress = computed(() => !!customerGrantUrl.value &&
  ['localhost', '127.0.0.1', '[::1]'].includes(new URL(customerGrantUrl.value).hostname))
watch(customerGrantUrl, () => { grantQrImage.value = '' })
const assistedVisible = ref(false)
const assistedSubmitting = ref(false)
const assistedRequestId = ref('')
const assistedSignatureFiles = ref<UploadUserFile[]>([])
const assistedAttachmentFiles = ref<UploadUserFile[]>([])
const assisted = reactive({
  customerContactRef: '',
  answerSnapshot: '{\n  "answers": []\n}'
})
const recollectVisible = ref(false)
const recollectForm = reactive({ evidenceSummary: '', evidenceFileFactVersion: '' })

const openCreate = async () => {
  if (!canCreate.value || creating.value) return
  const target = context.value.projectId
  if (!target) { message.warning('请先选择项目'); return }
  const sequence = contextVersion
  creating.value = true
  try {
    const [project, templates] = await Promise.all([Api.getIndependentCollectionContext(target), Api.listTemplates()])
    if (sequence !== contextVersion || !canCreate.value || target !== context.value.projectId) return
    createContext.value = project
    publishedQuestionnaires.value = templates.filter(item => item.status === 'PUBLISHED').flatMap(item =>
      item.revisions.filter(revision => revision.status === 'PUBLISHED' && String(revision.id) === String(item.currentRevisionId))
        .map(revision => ({ templateId: item.id, revisionId: revision.id, label: `${item.name} · V${revision.revisionNo} · 达标分 ${revision.threshold}` })))
    selectedRevisionId.value = undefined
    createKey.value = generateUUID()
    createVisible.value = true
  } finally { creating.value = false }
}
const createCollection = async () => {
  const project = createContext.value
  const selectedQuestionnaire = publishedQuestionnaires.value.find(item => item.revisionId === selectedRevisionId.value)
  if (!createVisible.value || !canCreate.value || creating.value || !project || !selectedQuestionnaire
      || project.projectId !== context.value.projectId) return
  const sequence = contextVersion
  creating.value = true
  try {
    await Api.createIndependentCollection({ projectId: project.projectId, templateId: selectedQuestionnaire.templateId,
      templateRevisionId: selectedQuestionnaire.revisionId, expectedProjectVersion: project.projectVersion,
      expectedTreeVersion: project.treeVersion }, createKey.value)
    if (sequence !== contextVersion) return
    createVisible.value = false
    message.success('满意度调查已创建')
    emit('changed')
    await load()
  } finally { creating.value = false }
}

const load = async () => {
  const sequence = ++loadSequence
  tasks.value = []
  errorText.value = ''
  if (!context.value.valid) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const result = await Api.listTasks(context.value.projectId)
    if (sequence === loadSequence) tasks.value = result
  } catch {
    if (sequence === loadSequence) errorText.value = '满意度任务加载失败，请重新查询。'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}
const openStart = async () => {
  const target = context.value.projectId
  if (!canWrite.value || !target || starting.value || preparingStart.value) return
  const version = contextVersion
  preparingStart.value = true
  errorText.value = ''
  try {
    const options = await Api.getStartOptions(target)
    if (version !== contextVersion || target !== context.value.projectId) return
    if (options.configured) await startTask()
    else { startOptions.value = options; startVisible.value = true }
  } catch {
    if (version === contextVersion && target === context.value.projectId) errorText.value = '发起配置加载失败，请重试。'
  } finally { preparingStart.value = false }
}
const startTask = async (selection?: Api.ManualStartSelection) => {
  const targetProjectId = context.value.projectId
  if (!canWrite.value || !targetProjectId || starting.value) return
  const version = contextVersion
  starting.value = true
  try {
    const requestKey = JSON.stringify([targetProjectId, selection])
    const operationId = startOperationIds.get(requestKey) ?? generateUUID()
    startOperationIds.set(requestKey, operationId)
    const result = selection ? await Api.startTask(targetProjectId, operationId, selection) : await Api.startTask(targetProjectId, operationId)
    startOperationIds.delete(requestKey)
    if (version !== contextVersion || targetProjectId !== context.value.projectId) return
    message.success(result.outcome === 'CREATED'
      ? '满意度调查已发起，由项目当前满意度责任人继续办理'
      : '该项目已有满意度调查，请由责任人继续办理；未达标请使用整改重收')
    emit('changed')
    startVisible.value = false
    await load()
  } catch {
    if (version === contextVersion && targetProjectId === context.value.projectId) {
      errorText.value = '满意度调查发起失败，请根据错误提示检查项目权限、冻结问卷、责任人及交付件配置后重试。'
    }
  } finally {
    starting.value = false
  }
}
const openAssign = (task: TaskView) => {
  if (!writableTask(task)) return
  selected.value = task
  assignedUserId.value = task.assignedToUserId
  assignVisible.value = true
}
const assign = async () => {
  if (!selected.value || !writableTask(selected.value) || !assignedUserId.value) return
  const version = contextVersion
  await Api.assignTask(selected.value, assignedUserId.value)
  if (version !== contextVersion) return
  emit('changed')
  message.success('指派成功')
  assignVisible.value = false
  await load()
}
const openGrant = (task: TaskView) => {
  if (!writableTask(task)) return
  selected.value = task
  grantUrl.value = ''
  grantExpiresAt.value = formatDate(new Date(Date.now() + 24 * 3600_000), 'YYYY-MM-DDTHH:mm:ss')
  grantVisible.value = true
}
const createGrant = async () => {
  if (grantCreating.value || !selected.value || !writableTask(selected.value) || !grantExpiresAt.value) return
  if (!(new Date(grantExpiresAt.value).getTime() > Date.now())) {
    message.warning('请选择未来的链接有效期')
    return
  }
  const version = contextVersion
  grantCreating.value = true
  try {
  const grant = await Api.createGrant(selected.value.id, new Date(grantExpiresAt.value).getTime())
  if (version !== contextVersion || !canWrite.value || !grantVisible.value) return
  const tenantId = getTenantId() ?? 0
  grantUrl.value = `${window.location.origin}/satisfaction-questionnaires/${encodeURIComponent(grant.token)}?tenantId=${tenantId}`
  } finally { grantCreating.value = false }
}
const copyLink = async () => {
  if (!customerGrantUrl.value) return
  try {
    await navigator.clipboard.writeText(customerGrantUrl.value)
    message.success('链接已复制')
  } catch { message.warning('自动复制不可用，请选中上方链接手动复制') }
}
const closeGrant = () => {
  grantVisible.value = false
  grantUrl.value = ''
}
const openAssisted = (task: TaskView) => {
  if (!writableTask(task)) return
  selected.value = task
  assistedRequestId.value = generateUUID()
  assisted.customerContactRef = ''
  assisted.answerSnapshot = '{\n  "answers": []\n}'
  assistedSignatureFiles.value = []
  assistedAttachmentFiles.value = []
  assistedVisible.value = true
}
const mediaType = (file: File) => {
  if (file.type) return file.type
  const extension = file.name.toLowerCase().split('.').pop()
  if (extension === 'png') return 'image/png'
  if (extension === 'jpg' || extension === 'jpeg') return 'image/jpeg'
  if (extension === 'pdf') return 'application/pdf'
  return ''
}
const assistedOperationId = () =>
  `ui:${Date.now().toString(36)}:${Math.random().toString(36).slice(2, 10)}`
const uploadAssistedFile = async (
  taskId: number,
  responseId: number,
  policyKey: 'SATISFACTION_SIGNATURE' | 'SATISFACTION_ATTACHMENT',
  file: File,
  requestId: string,
  checkContext: () => void
) => {
  checkContext()
  const declaredMediaType = mediaType(file)
  if (!declaredMediaType) throw new Error(`不支持的文件类型：${file.name}`)
  const operationId = assistedOperationId()
  const initialized = await Api.initializeAssistedFile(taskId, {
    requestId,
    responseId,
    policyKey,
    operationId,
    fileName: file.name,
    categoryCode: policyKey,
    declaredSizeBytes: file.size,
    declaredMediaType
  })
  checkContext()
  return Api.completeAssistedFile(
    taskId,
    initialized.sessionId,
    {
      requestId,
      responseId,
      policyKey,
      operationId,
      fileSlotKey: initialized.fileSlotKey,
      fileSequence: initialized.fileSequence,
      artifactId: initialized.artifactId
    },
    file
  )
}
const toSubmissionFile = (fact: Api.AssistedFileFact) => ({
  role: fact.policyKey === 'SATISFACTION_SIGNATURE' ? 'SIGNATURE' : 'ATTACHMENT',
  fileSlotKey: fact.fileSlotKey,
  sequence: fact.fileSequence,
  artifactId: fact.fileFact.artifactId,
  versionNo: fact.fileFact.versionNo,
  referenceKey: fact.fileFact.referenceKey,
  artifactVersion: fact.fileFact.fileFactVersion.artifactVersion,
  referenceVersion: fact.fileFact.fileFactVersion.referenceVersion,
  availabilityVersion: fact.fileFact.fileFactVersion.availabilityVersion,
  scopeVersion: fact.fileFact.scopeVersion,
  sha256: fact.fileFact.sha256
})
const submitAssisted = async () => {
  if (!selected.value || !writableTask(selected.value)) return
  const signature = assistedSignatureFiles.value[0]?.raw
  if (!assisted.customerContactRef.trim()) return message.warning('请输入客户联系人')
  if (!signature) return message.warning('请选择客户签字文件')
  try {
    if (!questionnaireRef.value) throw new Error('请等待问卷加载')
    assisted.answerSnapshot = questionnaireRef.value.snapshot()
  } catch (error) {
    return message.warning(error instanceof Error ? error.message : '请检查问卷内容')
  }
  assistedSubmitting.value = true
  const version = contextVersion
  const requestId = assistedRequestId.value
  const task = selected.value
  const checkContext = () => {
    if (version !== contextVersion || !writableTask(task))
      throw new Error('页面上下文已变化，请重新打开采集任务。')
  }
  try {
    const taskId = task.id
    const reservation = await Api.reserveAssistedResponse(taskId, requestId)
    checkContext()
    const uploaded: Api.AssistedFileFact[] = []
    uploaded.push(
      await uploadAssistedFile(
        taskId,
        reservation.responseId,
        'SATISFACTION_SIGNATURE',
        signature,
        requestId,
        checkContext
      )
    )
    for (const item of assistedAttachmentFiles.value) {
      if (item.raw) {
        uploaded.push(
          await uploadAssistedFile(
            taskId,
            reservation.responseId,
            'SATISFACTION_ATTACHMENT',
            item.raw,
            requestId,
            checkContext
          )
        )
      }
    }
    checkContext()
    await Api.submitAssisted(taskId, {
      requestId,
      responseId: reservation.responseId,
      customerContactRef: assisted.customerContactRef.trim(),
      answerSnapshot: assisted.answerSnapshot,
      files: uploaded.map(toSubmissionFile)
    })
    if (version !== contextVersion) return
    emit('changed')
    message.success('现场协助答卷已提交并完成判定')
    assistedVisible.value = false
    await load()
  } finally {
    assistedSubmitting.value = false
  }
}
const openRecollect = (task: TaskView) => {
  if (!writableTask(task)) return
  selected.value = task
  recollectVisible.value = true
}
const submitRecollect = async () => {
  if (!selected.value?.resultId || !writableTask(selected.value)) return
  const version = contextVersion
  await Api.recollect(selected.value.id, {
    priorResultId: selected.value.resultId,
    remediationRequestId: generateUUID(),
    evidenceSummary: recollectForm.evidenceSummary,
    evidenceFileFactVersion: recollectForm.evidenceFileFactVersion || undefined
  })
  if (version !== contextVersion) return
  emit('changed')
  message.success('整改事实与下一轮问卷已创建')
  recollectVisible.value = false
  await load()
}
const dirty = computed(
  () =>
    assignVisible.value ||
    grantVisible.value ||
    assistedVisible.value ||
    recollectVisible.value ||
    createVisible.value || creating.value ||
    starting.value || startVisible.value || preparingStart.value ||
    assistedSubmitting.value
)
const resetDialogs = () => {
  startVisible.value = false
  createVisible.value = false
  createContext.value = undefined
  selectedRevisionId.value = undefined
  assignVisible.value = grantVisible.value = assistedVisible.value = recollectVisible.value = false
  grantUrl.value = ''
  selected.value = undefined
}
watch(dirty, (value) => emit('dirty-change', value), { immediate: true })
watch(
  () => props.projectId,
  () => {
    contextVersion++
    resetDialogs()
    void load()
  },
  { immediate: true, flush: 'sync' }
)
watch(
  () => props.readonly,
  () => {
    contextVersion++
    closeGrant()
  },
  { flush: 'sync' }
)
onBeforeUnmount(() => {
  loadSequence++
  contextVersion++
})
defineExpose({
  isDirty: () => dirty.value,
  discardChanges: () => {
    if (assistedSubmitting.value || creating.value || starting.value) return false
    contextVersion++
    resetDialogs()
    return true
  }
})
</script>

<style scoped lang="scss">
.grant-result {
  display: grid;
  justify-items: center;
  gap: 18px;
}
.dialog-form {
  margin-top: 16px;
}
.grant-address { width: 100%; }
@media (width <= 767px) {
  .satisfaction-query :deep(.el-form-item) {
    width: 100%;
  }
}
</style>

<template>
  <section class="annotation-panel" aria-labelledby="inspection-annotation-title">
    <header class="inspection-card__header">
      <h2 id="inspection-annotation-title">检出问题确认</h2>
      <el-tag v-if="currentReport"
        >当前报告 R{{ currentReport.revisionNo ?? '—' }} ·
        {{ reportStatus(currentReport.statusCode) }}</el-tag
      >
    </header>
    <el-alert
      v-if="!currentReport"
      title="尚无当前报告，请先完成报告生成或提交。"
      type="info"
      :closable="false"
      show-icon
    />
    <template v-else>
      <el-descriptions :column="smallScreen ? 1 : 3" border class="report-summary">
        <el-descriptions-item label="报告版本"
          >V{{ currentReport.revisionNo ?? '未提供' }}</el-descriptions-item
        >
        <el-descriptions-item label="来源">{{
          currentReport.sourceCode === 'MANUAL'
            ? '人工报告'
            : currentReport.sourceCode === 'PLATFORM'
              ? '自动报告'
              : currentReport.sourceCode || '未提供'
        }}</el-descriptions-item>
        <el-descriptions-item label="生成时间">{{
          currentReport.generatedAt ? formatDate(currentReport.generatedAt) : '—'
        }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        v-if="!frozenResultsComplete"
        title="当前报告冻结结果尚未完整获取，暂不能提交标注，请刷新任务详情。"
        type="warning"
        :closable="false"
        show-icon
        class="mb-16px"
      />
      <el-alert
        v-else-if="task.statusCode === 'ARCHIVED' || task.statusCode === 'CANCELLED'"
        title="任务已归档或已取消，标注历史仅供查阅。"
        type="info"
        :closable="false"
        show-icon
        class="mb-16px"
      />
      <el-table
        ref="resultsTableRef"
        :data="abnormalResults"
        :row-key="(row: ResultView) => String(row.id)"
        :expand-row-keys="expandedResultIds"
        @expand-change="(_row, rows) => (expandedResultIds = rows.map((row) => String(row.id)))"
        empty-text="当前报告暂无异常检出项"
      >
        <el-table-column type="expand" width="42"
          ><template #default="{ row }">
            <div
              v-if="selectedResult && String(row.id) === selectedResultId"
              class="annotation-editor"
            >
              <p v-if="dirty" class="help-text" role="status"
                >当前输入尚未保存，请先保存或关闭编辑再选择其他检出项。</p
              >
              <div class="section-heading"
                ><h3 class="inspection-section-title"
                  >标注 {{ selectedResult.ruleName || '当前检出问题' }}</h3
                ><el-button link :disabled="disabled || validating" @click="resetEditor"
                  >关闭编辑</el-button
                ></div
              >
              <el-alert
                v-if="previousAnnotation"
                :title="`正在修订 R${previousAnnotation.revisionNo ?? '—'} 标注，须填写修改原因；原标注保留。`"
                type="info"
                :closable="false"
                show-icon
                class="mb-16px"
              />
              <el-form
                ref="formRef"
                :model="form"
                :rules="rules"
                label-position="top"
                :disabled="readonly || validating"
                @submit.prevent="submitAnnotation"
              >
                <el-form-item label="标注类型" prop="kind"
                  ><el-radio-group v-model="form.kind"
                    ><el-radio value="FALSE_POSITIVE">误报</el-radio
                    ><el-radio value="IGNORE">不关注</el-radio
                    ><el-radio value="TRACK">确认跟踪</el-radio></el-radio-group
                  ></el-form-item
                >
                <el-form-item :label="reasonLabel" prop="reason"
                  ><el-input
                    v-model="form.reason"
                    type="textarea"
                    :rows="3"
                    maxlength="2000"
                    show-word-limit
                    placeholder="填写本次标注的原因或依据"
                /></el-form-item>
                <template v-if="form.kind === 'TRACK'">
                  <div class="form-grid">
                    <el-form-item label="责任人" prop="responsibleUserId">
                      <el-select
                        v-model="form.responsibleUserId"
                        filterable
                        clearable
                        :loading="usersLoading"
                        placeholder="选择责任人"
                        @visible-change="loadUsersOnOpen"
                      >
                        <el-option
                          v-if="
                            form.responsibleUserId &&
                            !users.some((user) => String(user.id) === form.responsibleUserId)
                          "
                          :value="form.responsibleUserId"
                          :label="userLabel(form.responsibleUserId)"
                          disabled
                        />
                        <el-option
                          v-for="user in users"
                          :key="String(user.id)"
                          :value="String(user.id)"
                          :label="`${user.nickname || user.username}（${user.id}）`"
                        />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="计划解决时间" prop="dueAt"
                      ><el-date-picker
                        v-model="form.dueAt"
                        type="datetime"
                        placeholder="选择计划解决时间"
                    /></el-form-item>
                  </div>
                  <el-alert
                    v-if="usersError"
                    :title="usersError"
                    type="error"
                    :closable="false"
                    class="mb-16px"
                    ><template #default
                      ><el-button
                        link
                        type="primary"
                        :disabled="readonly || usersLoading"
                        @click="loadUsers"
                        >重新加载用户</el-button
                      ></template
                    ></el-alert
                  >
                  <p class="help-text"
                    >用户列表仅供选择身份；责任人的处理权限和项目范围由服务端核验。已闭环待办的责任人和期限须保留。</p
                  >
                </template>
                <el-form-item v-if="previousAnnotation" label="标注修改原因" prop="changeReason"
                  ><el-input
                    v-model="form.changeReason"
                    type="textarea"
                    :rows="2"
                    maxlength="2000"
                    show-word-limit
                    placeholder="说明为何修订已有标注"
                /></el-form-item>
                <el-button
                  type="primary"
                  native-type="submit"
                  :loading="validating"
                  :disabled="readonly || !frozenResultsComplete"
                  >保存标注</el-button
                >
              </el-form>
            </div>
            <el-descriptions :column="smallScreen ? 1 : 2" class="annotation-source">
              <el-descriptions-item label="结果记录">{{ row.id }}</el-descriptions-item>
              <el-descriptions-item label="规则修订">{{
                row.ruleRevisionId ?? '未提供'
              }}</el-descriptions-item>
              <el-descriptions-item label="来源执行">{{
                row.executionId ?? '未提供'
              }}</el-descriptions-item>
              <el-descriptions-item label="报告记录">{{ currentReport.id }}</el-descriptions-item>
              <el-descriptions-item v-if="latestAnnotation(row.id)" label="最新标注记录">{{
                latestAnnotation(row.id)?.id
              }}</el-descriptions-item>
            </el-descriptions>
            <div class="output-detail"
              ><h4 class="inspection-section-title">检出输出摘要</h4
              ><pre>{{ row.outputSummary || '未提供输出摘要' }}</pre>
            </div>
          </template></el-table-column
        >
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column label="设备 / SN" min-width="220"
          ><template #default="{ row }">{{ deviceLabel(row.deviceId) }}</template></el-table-column
        >
        <el-table-column label="检测规则" min-width="210"
          ><template #default="{ row }">{{
            row.ruleName || '未提供名称'
          }}</template></el-table-column
        >
        <el-table-column label="严重级别" width="110"
          ><template #default="{ row }">{{
            row.severityName || severityLabel(row.severityCode)
          }}</template></el-table-column
        >
        <el-table-column label="异常说明" min-width="220"
          ><template #default="{ row }">{{
            row.detailMessage || row.detailCode || '未提供说明'
          }}</template></el-table-column
        >
        <el-table-column label="最新标注" min-width="130"
          ><template #default="{ row }"
            ><el-tag :type="annotationType(latestAnnotation(row.id)?.kind)">{{
              kindLabel(latestAnnotation(row.id)?.kind)
            }}</el-tag
            ><p v-if="latestAnnotation(row.id)" class="help-text"
              >R{{ latestAnnotation(row.id)?.revisionNo ?? '—' }}</p
            ></template
          ></el-table-column
        >
        <el-table-column label="原因 / 依据" min-width="210"
          ><template #default="{ row }">{{
            latestAnnotation(row.id)?.reason || '尚未标注'
          }}</template></el-table-column
        >
        <el-table-column label="责任人 / 期限" min-width="190"
          ><template #default="{ row }"
            ><template v-if="latestAnnotation(row.id)?.kind === 'TRACK'"
              >{{ userLabel(latestAnnotation(row.id)?.responsibleUserId)
              }}<p class="help-text">{{
                latestAnnotation(row.id)?.dueAt
                  ? formatDate(latestAnnotation(row.id)?.dueAt)
                  : '未提供期限'
              }}</p></template
            ><span v-else>—</span></template
          ></el-table-column
        >
        <el-table-column label="操作" width="110" :fixed="smallScreen ? false : 'right'"
          ><template #default="{ row }"
            ><el-button
              link
              type="primary"
              :disabled="
                readonly || !frozenResultsComplete || (dirty && String(row.id) !== selectedResultId)
              "
              @click="openEditor(row)"
              >{{ latestAnnotation(row.id) ? '修订标注' : '填写标注' }}</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <p class="help-text"
        >只标注当前报告冻结的异常检出项。误报和不关注仍保留报告与标注历史；确认跟踪会建立或更新问题待办。</p
      >
    </template>

    <div v-if="currentReport && task.statusCode === 'ANNOTATION_PENDING'" class="complete-actions">
      <p class="help-text">完成当前报告的全部异常标注后，可确认完成标注。</p>
      <el-button
        type="primary"
        :disabled="readonly || !frozenResultsComplete || dirty || validating"
        @click="emit('complete')"
        >确认完成标注</el-button
      >
    </div>
    <el-collapse class="annotation-history"
      ><el-collapse-item
        name="annotations"
        :title="`查看标注历史（${task.annotations.length} 条）`"
      >
        <div class="section-heading history-heading"
          ><h3 class="inspection-section-title">标注历史</h3
          ><span class="help-text">包含当前与历史报告，历史记录只读</span></div
        >
        <el-table
          :data="task.annotations"
          :row-key="(row: AnnotationView) => String(row.id)"
          empty-text="暂无标注历史"
        >
          <el-table-column type="expand" width="42"
            ><template #default="{ row }">
              <el-descriptions :column="smallScreen ? 1 : 3" class="annotation-source">
                <el-descriptions-item label="标注记录">{{ row.id }}</el-descriptions-item>
                <el-descriptions-item label="报告来源">{{
                  reportLabel(row.reportId)
                }}</el-descriptions-item>
                <el-descriptions-item label="结果记录">{{
                  row.resultId ?? '未提供'
                }}</el-descriptions-item>
              </el-descriptions>
            </template></el-table-column
          >
          <el-table-column label="报告 / 修订" min-width="180"
            ><template #default="{ row }"
              >报告 V{{
                task.reports.find((report) => sameId(report.id, row.reportId))?.revisionNo ??
                '未提供'
              }}<el-tag
                v-if="sameId(row.reportId, task.currentReportId)"
                size="small"
                class="current-tag"
                >当前报告</el-tag
              ></template
            ></el-table-column
          >
          <el-table-column prop="revisionNo" label="标注修订" width="100" />
          <el-table-column label="类型" width="110"
            ><template #default="{ row }">{{ kindLabel(row.kind) }}</template></el-table-column
          >
          <el-table-column
            prop="reason"
            label="原因 / 依据"
            min-width="230"
            show-overflow-tooltip
          />
          <el-table-column
            prop="changeReason"
            label="修改原因"
            min-width="200"
            show-overflow-tooltip
          />
          <el-table-column label="责任人" min-width="150"
            ><template #default="{ row }">{{
              row.kind === 'TRACK' ? userLabel(row.responsibleUserId) : '—'
            }}</template></el-table-column
          >
          <el-table-column label="计划解决时间" min-width="170"
            ><template #default="{ row }">{{
              row.dueAt ? formatDate(row.dueAt) : '—'
            }}</template></el-table-column
          >
          <el-table-column label="标注人" min-width="150"
            ><template #default="{ row }">{{
              userLabel(row.annotatedBy)
            }}</template></el-table-column
          >
          <el-table-column label="标注时间" min-width="170"
            ><template #default="{ row }">{{
              row.annotatedAt ? formatDate(row.annotatedAt) : '—'
            }}</template></el-table-column
          >
        </el-table>
      </el-collapse-item></el-collapse
    >
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import type { FormInstance, FormRules, TableInstance, TagProps } from 'element-plus'
import type {
  TaskDetail,
  AnnotationInput,
  AnnotationView,
  ResultView,
  WireLong
} from '@/api/pms/inspection/tasks'
import { getSimpleUserList } from '@/api/system/user'
import { formatDate } from '@/utils/formatTime'
import { getStrDictOptions } from '@/utils/dict'

defineOptions({ name: 'InspectionAnnotationPanel' })
const props = defineProps<{ task: TaskDetail; disabled: boolean }>()
const emit = defineEmits<{
  annotate: [payload: AnnotationInput]
  complete: []
  draftChanged: [dirty: boolean]
}>()
const smallScreen = useMediaQuery('(max-width: 767px)')
const sameId = (left?: WireLong, right?: WireLong) =>
  left != null && right != null && String(left) === String(right)
const currentReport = computed(() =>
  props.task.reports.find((report) => sameId(report.id, props.task.currentReportId))
)
const frozenIds = computed(() => new Set((currentReport.value?.resultIds || []).map(String)))
const frozenResultsComplete = computed(
  () =>
    Array.isArray(currentReport.value?.resultIds) &&
    currentReport.value!.resultIds!.every((id) =>
      props.task.results.some((result) => sameId(id, result.id))
    )
)
const abnormalResults = computed(() =>
  props.task.results.filter(
    (result) => frozenIds.value.has(String(result.id)) && result.outcome === 'ABNORMAL'
  )
)
const greaterId = (left: WireLong, right: WireLong) => {
  const leftText = String(left).replace(/^0+/, '')
  const rightText = String(right).replace(/^0+/, '')
  return leftText.length === rightText.length
    ? leftText > rightText
    : leftText.length > rightText.length
}
const latestAnnotations = computed(() => {
  const latest = new Map<string, AnnotationView>()
  for (const annotation of props.task.annotations) {
    if (!sameId(annotation.reportId, props.task.currentReportId) || annotation.resultId == null)
      continue
    const key = String(annotation.resultId)
    const previous = latest.get(key)
    if (
      !previous ||
      (annotation.revisionNo ?? 0) > (previous.revisionNo ?? 0) ||
      ((annotation.revisionNo ?? 0) === (previous.revisionNo ?? 0) &&
        greaterId(annotation.id, previous.id))
    )
      latest.set(key, annotation)
  }
  return latest
})
const latestAnnotation = (resultId: WireLong) => latestAnnotations.value.get(String(resultId))
const readonly = computed(
  () =>
    props.disabled || !['ANNOTATION_PENDING', 'TRACKING', 'CLOSED'].includes(props.task.statusCode)
)
const kinds: Record<string, string> = {
  FALSE_POSITIVE: '误报',
  IGNORE: '不关注',
  TRACK: '确认跟踪'
}
const kindLabel = (kind?: string) => (kind ? kinds[kind] || kind : '未标注')
const annotationType = (kind?: string): TagProps['type'] =>
  kind === 'TRACK' ? 'warning' : kind ? 'info' : 'danger'
const reportStatuses: Record<string, string> = {
  AVAILABLE: '可用',
  GENERATING: '生成中',
  FAILED: '生成失败'
}
const reportStatus = (status: string) => reportStatuses[status] || status
const reportLabel = (id?: WireLong) => {
  if (id == null) return '未提供报告'
  const report = props.task.reports.find((item) => sameId(item.id, id))
  return report ? `R${report.revisionNo ?? '—'} · ${id}` : String(id)
}
const deviceLabel = (id: WireLong) => {
  const device = props.task.devices.find((item) => sameId(item.deviceId, id))
  return device
    ? [device.serialNumber || '未提供 SN', device.deviceName].filter(Boolean).join(' · ')
    : `设备 ${id}`
}
const severities = computed(() => getStrDictOptions('pms_inspection_rule_severity'))
const severityLabel = (code?: string) =>
  severities.value.find((item) => item.value === code)?.label || code || '未提供'
const resultsTableRef = ref<TableInstance>()
const expandedResultIds = ref<string[]>([])
const selectedResultId = ref('')
const selectedResult = computed(() =>
  abnormalResults.value.find((result) => String(result.id) === selectedResultId.value)
)
const previousAnnotation = computed(() =>
  selectedResult.value ? latestAnnotation(selectedResult.value.id) : undefined
)
const formRef = ref<FormInstance>()
const validating = ref(false)
const form = reactive({
  kind: '' as '' | AnnotationInput['kind'],
  reason: '',
  changeReason: '',
  responsibleUserId: '',
  dueAt: null as Date | null
})
const snapshot = ref(JSON.stringify(form))
const dirty = computed(() => !!selectedResult.value && JSON.stringify(form) !== snapshot.value)
const reasonLabel = computed(() =>
  form.kind === 'FALSE_POSITIVE'
    ? '误报原因'
    : form.kind === 'IGNORE'
      ? '不关注依据'
      : '跟踪原因 / 依据'
)
const rules: FormRules = {
  kind: [{ required: true, message: '请选择标注类型', trigger: 'change' }],
  reason: [
    {
      required: true,
      whitespace: true,
      max: 2000,
      message: '请填写原因或依据（不超过2000字）',
      trigger: 'blur'
    }
  ],
  changeReason: [
    {
      required: true,
      whitespace: true,
      max: 2000,
      message: '请填写标注修改原因（不超过2000字）',
      trigger: 'blur'
    }
  ],
  responsibleUserId: [{ required: true, message: '请选择责任人', trigger: 'change' }],
  dueAt: [{ type: 'date', required: true, message: '请选择计划解决时间', trigger: 'change' }]
}
const resetEditor = () => {
  selectedResultId.value = ''
  Object.assign(form, {
    kind: '',
    reason: '',
    changeReason: '',
    responsibleUserId: '',
    dueAt: null
  })
  snapshot.value = JSON.stringify(form)
  formRef.value?.clearValidate()
  emit('draftChanged', false)
}
const openEditor = (result: ResultView) => {
  if (readonly.value || !frozenResultsComplete.value) return
  if (selectedResultId.value === String(result.id)) {
    resultsTableRef.value?.toggleRowExpansion(result, true)
    return
  }
  if (dirty.value) return
  const previous = latestAnnotation(result.id)
  selectedResultId.value = String(result.id)
  Object.assign(form, {
    kind:
      previous?.kind && ['FALSE_POSITIVE', 'IGNORE', 'TRACK'].includes(previous.kind)
        ? previous.kind
        : '',
    reason: previous?.reason || '',
    changeReason: '',
    responsibleUserId:
      previous?.responsibleUserId != null ? String(previous.responsibleUserId) : '',
    dueAt: previous?.dueAt ? new Date(previous.dueAt) : null
  })
  snapshot.value = JSON.stringify(form)
  void nextTick(() => {
    resultsTableRef.value?.toggleRowExpansion(result, true)
    formRef.value?.clearValidate()
  })
}
const submitAnnotation = async () => {
  if (
    readonly.value ||
    validating.value ||
    !currentReport.value ||
    !selectedResult.value ||
    !frozenResultsComplete.value
  )
    return
  const identity = `${props.task.id}:${props.task.version}:${props.task.currentReportId}:${selectedResultId.value}`
  validating.value = true
  try {
    if (!(await formRef.value?.validate().catch(() => false))) return
    if (
      readonly.value ||
      identity !==
        `${props.task.id}:${props.task.version}:${props.task.currentReportId}:${selectedResultId.value}` ||
      !currentReport.value ||
      !selectedResult.value ||
      !form.kind
    )
      return
    emit('annotate', {
      reportId: currentReport.value.id,
      resultId: selectedResult.value.id,
      kind: form.kind,
      reason: form.reason.trim(),
      ...(previousAnnotation.value ? { changeReason: form.changeReason.trim() } : {}),
      ...(form.kind === 'TRACK'
        ? { responsibleUserId: form.responsibleUserId, dueAt: form.dueAt!.getTime() }
        : {})
    })
  } finally {
    validating.value = false
  }
}
const users = ref<Awaited<ReturnType<typeof getSimpleUserList>>>([])
const usersLoading = ref(false)
const usersError = ref('')
let userSequence = 0
const userLabel = (id?: WireLong) => {
  if (id == null) return '未提供用户'
  const user = users.value.find((item) => sameId(item.id, id))
  return user ? `${user.nickname || user.username}（${id}）` : `用户 ${id}`
}
const loadUsers = async () => {
  if (readonly.value || usersLoading.value) return
  const current = ++userSequence
  const taskId = String(props.task.id)
  usersLoading.value = true
  usersError.value = ''
  try {
    const result = await getSimpleUserList()
    if (current !== userSequence || taskId !== String(props.task.id)) return
    users.value = result.filter((user) => user.status == null || user.status === 0)
  } catch {
    if (current === userSequence && taskId === String(props.task.id))
      usersError.value = '用户候选加载失败，已填写的标注内容保留，请重新加载。'
  } finally {
    if (current === userSequence) usersLoading.value = false
  }
}
const loadUsersOnOpen = (open: boolean) => {
  if (open && !users.value.length) void loadUsers()
}
watch(
  () => form.kind,
  (kind) => {
    formRef.value?.clearValidate()
    if (kind === 'TRACK' && !users.value.length) void loadUsers()
  }
)
watch(
  () => [String(props.task.id), String(props.task.version), String(props.task.currentReportId)],
  resetEditor
)
watch(
  () => `${props.task.id}:${props.task.currentReportId}`,
  () => {
    expandedResultIds.value = []
  }
)
watch(
  () => String(props.task.id),
  () => {
    userSequence++
    users.value = []
    usersError.value = ''
    usersLoading.value = false
  }
)
watch(dirty, (value) => emit('draftChanged', value))
onBeforeUnmount(() => {
  userSequence++
})
</script>

<style scoped>
.annotation-panel {
  min-width: 0;
}
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.section-heading h2,
.section-heading h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}
.section-heading h3 {
  font-size: 14px;
}
.section-heading .inspection-section-title {
  display: inline-block;
  padding-bottom: 6px;
  margin: 0;
  color: #1a2332;
  font-size: 14px;
  border-bottom: 2px solid var(--inspection-blue, #4a90e2);
}
.annotation-source {
  padding: 12px 20px;
  overflow-wrap: anywhere;
}
.output-detail {
  padding: 0 20px 16px;
}
.output-detail h4 {
  margin: 0 0 8px;
  font-size: 13px;
}
.output-detail pre {
  padding: 12px;
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  background: #fafbfc;
  border: 1px solid #f0f2f5;
  border-radius: 4px;
}
.help-text {
  margin: 8px 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.report-summary {
  margin-bottom: 16px;
}
.annotation-editor {
  margin: 0 16px 16px;
  padding: 16px;
  background: #fafbfc;
  border: 1px solid #e8ecf1;
  border-radius: 4px;
}
.annotation-editor :deep(.el-form-item) {
  margin-bottom: 14px;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}
.form-grid :deep(.el-select),
.form-grid :deep(.el-date-editor) {
  width: 100%;
}
.complete-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #f0f2f5;
}
.history-heading {
  margin-top: 8px;
}
.annotation-history {
  margin-top: 20px;
  --el-collapse-border-color: #f0f2f5;
}
.annotation-history :deep(.el-collapse-item__header) {
  color: #1a2332;
  font-size: 13px;
}
.current-tag {
  margin-left: 8px;
}
@media (max-width: 767px) {
  .section-heading,
  .complete-actions {
    align-items: flex-start;
    flex-direction: column;
  }
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>

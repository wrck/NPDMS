<template>
  <section class="annotation-panel" aria-labelledby="inspection-annotation-title">
    <header class="inspection-card__header">
      <h2 id="inspection-annotation-title">检出问题确认</h2>
      <el-tag v-if="currentReport && !umcMode"
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
      <el-descriptions v-if="!umcMode" :column="smallScreen ? 1 : 3" border class="report-summary">
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
        v-if="umcMode"
        :data="abnormalResults"
        :row-key="(row: ResultView) => String(row.id)"
        empty-text="当前报告暂无异常检出项"
      >
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column label="检出问题" min-width="260"
          ><template #default="{ row }">
            <el-button
              link
              type="primary"
              class="finding-link"
              @click="umcDetailId = String(row.id)"
              >{{ row.ruleName || '未提供项目名称' }}</el-button
            >
            <p class="help-text">{{
              row.externalRule?.info || row.detailMessage || '未提供判定说明'
            }}</p>
            <span class="help-text">{{ deviceLabel(row.deviceId) }}</span>
          </template></el-table-column
        >
        <el-table-column label="严重程度" width="120"
          ><template #default="{ row }">{{
            row.severityName || severityLabel(row.severityCode)
          }}</template></el-table-column
        >
        <el-table-column label="标注" min-width="130"
          ><template #default="{ row }">
            <el-tag :type="annotationType(latestAnnotation(row.id)?.kind)">{{
              kindLabel(latestAnnotation(row.id)?.kind)
            }}</el-tag>
            <span
              v-if="String(row.id) === selectedResultId && dirty"
              class="help-text"
              role="status"
              >待保存</span
            >
          </template></el-table-column
        >
        <el-table-column label="标注操作" width="115" :fixed="smallScreen ? false : 'right'"
          ><template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="readonly || !frozenResultsComplete || validating"
              @click="openEditor(row)"
              >{{ latestAnnotation(row.id) ? '修订标注' : '填写标注' }}</el-button
            >
          </template></el-table-column
        >
      </el-table>
      <el-table
        v-else
        :data="abnormalResults"
        :row-key="(row: ResultView) => String(row.id)"
        :expand-row-keys="expandedResultIds"
        @expand-change="(_row, rows) => (expandedResultIds = rows.map((row) => String(row.id)))"
        empty-text="当前报告暂无异常检出项"
      >
        <el-table-column type="expand" width="42"
          ><template #default="{ row }">
            <el-descriptions :column="smallScreen ? 1 : 2" class="annotation-source">
              <el-descriptions-item label="结果记录">{{ row.id }}</el-descriptions-item>
              <el-descriptions-item :label="row.sourceCode === 'UMC' ? 'UMC规则ID' : '规则修订'">{{
                row.sourceCode === 'UMC'
                  ? row.externalRule?.ruleid
                  : (row.ruleRevisionId ?? '未提供')
              }}</el-descriptions-item>
              <el-descriptions-item label="来源执行">{{
                row.executionId ?? '未提供'
              }}</el-descriptions-item>
              <el-descriptions-item label="报告记录">{{ currentReport.id }}</el-descriptions-item>
              <el-descriptions-item v-if="latestAnnotation(row.id)" label="最新标注记录">{{
                latestAnnotation(row.id)?.id
              }}</el-descriptions-item>
            </el-descriptions>
            <el-descriptions
              v-if="umcMode && latestAnnotation(row.id)"
              :column="smallScreen ? 1 : 2"
            >
              <el-descriptions-item label="标注依据">{{
                latestAnnotation(row.id)?.reason
              }}</el-descriptions-item>
              <el-descriptions-item label="责任人 / 期限"
                >{{
                  latestAnnotation(row.id)?.responsibleName ||
                  userLabel(latestAnnotation(row.id)?.responsibleUserId)
                }}
                /
                {{
                  latestAnnotation(row.id)?.dueAt
                    ? formatDate(latestAnnotation(row.id)?.dueAt)
                    : '未设置'
                }}</el-descriptions-item
              >
            </el-descriptions>
            <InspectionUmcRuleDetail v-if="row.externalRule" :rule="row.externalRule" />
            <div v-else class="output-detail"
              ><h4 class="inspection-section-title">检出输出摘要</h4
              ><pre>{{ row.outputSummary || '未提供输出摘要' }}</pre>
            </div>
          </template></el-table-column
        >
        <el-table-column v-if="!umcMode" type="index" label="序号" width="60" />
        <el-table-column label="设备 / SN" min-width="220"
          ><template #default="{ row }">{{ deviceLabel(row.deviceId) }}</template></el-table-column
        >
        <el-table-column label="检测规则" min-width="210"
          ><template #default="{ row }"
            >{{ row.ruleName || '未提供名称'
            }}<div v-if="row.sourceCode === 'UMC'" class="help-text"
              >UMC规则 {{ row.externalRule?.ruleid }} · {{ row.severityName }}</div
            ></template
          ></el-table-column
        >
        <el-table-column v-if="!umcMode" label="严重级别" width="110"
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
        <el-table-column v-if="!umcMode" label="原因 / 依据" min-width="210"
          ><template #default="{ row }">{{
            latestAnnotation(row.id)?.reason || '尚未标注'
          }}</template></el-table-column
        >
        <el-table-column v-if="!umcMode" label="责任人 / 期限" min-width="190"
          ><template #default="{ row }"
            ><template v-if="latestAnnotation(row.id)?.kind === 'TRACK'"
              >{{
                latestAnnotation(row.id)?.responsibleName ||
                userLabel(latestAnnotation(row.id)?.responsibleUserId)
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
              :disabled="readonly || !frozenResultsComplete || validating"
              @click="openEditor(row)"
              >{{ latestAnnotation(row.id) ? '修订标注' : '填写标注' }}</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <el-dialog
        :model-value="!!selectedResult"
        :title="`${previousAnnotation ? '修订标注' : '填写标注'} · ${selectedResult?.ruleName || '检出问题'}`"
        width="min(640px, 94vw)"
        append-to-body
        :close-on-click-modal="false"
        :close-on-press-escape="!disabled && !validating"
        :show-close="!disabled && !validating"
        :before-close="closeEditor"
        data-testid="inspection-annotation-editor"
      >
        <div v-if="selectedResult" class="annotation-editor">
          <slot name="recovery"></slot>
          <p v-if="dirty" class="help-text" role="status">当前标注尚未保存。</p>
          <p class="help-text">{{ deviceLabel(selectedResult.deviceId) }}</p>
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
                  <UserSelect
                    v-model="form.responsibleUserId"
                    :current-label="
                      sameId(form.responsibleUserId, userStore.getUser.id)
                        ? userStore.getUser.nickname
                        : ''
                    "
                    placeholder="选择责任人"
                    :context-key="String(task.id)"
                    :disabled="readonly"
                    @loaded="users = $event"
                  />
                </el-form-item>
                <el-form-item label="计划解决时间" prop="dueAt"
                  ><el-date-picker
                    v-model="form.dueAt"
                    type="datetime"
                    placeholder="选择计划解决时间"
                /></el-form-item>
              </div>
              <p v-if="previousAnnotation" class="help-text"
                >已闭环待办的责任人和计划解决时间不能修改。</p
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
          </el-form>
        </div>
        <template #footer>
          <el-button :disabled="disabled || validating" @click="closeEditor()">取消</el-button>
          <el-button
            type="primary"
            native-type="button"
            :loading="validating"
            :disabled="readonly || !frozenResultsComplete"
            @click="submitAnnotation"
            >保存标注</el-button
          >
        </template>
      </el-dialog>
      <p class="help-text"
        >只标注当前报告冻结的异常检出项。误报和不关注仍保留报告与标注历史；确认跟踪会建立或更新问题待办。</p
      >
    </template>

    <div v-if="currentReport && task.statusCode === 'ANNOTATION_PENDING'" class="complete-actions">
      <p class="help-text">完成当前报告的全部异常标注后，可确认完成标注。</p>
      <Teleport :to="actionsTarget || 'body'" :disabled="!actionsTarget">
        <el-button
          type="primary"
          :disabled="readonly || !frozenResultsComplete || dirty || validating"
          @click="emit('complete')"
          >确认完成标注</el-button
        >
      </Teleport>
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
          <el-table-column v-if="!umcMode" type="expand" width="42"
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
          <el-table-column label="检出问题" min-width="240" show-overflow-tooltip
            ><template #default="{ row }">
              <el-button
                v-if="annotationFinding(row)"
                link
                type="primary"
                @click="umcDetailId = String(row.resultId)"
              >
                {{ annotationFindingName(row) }}
              </el-button>
              <span v-else>{{ annotationFindingName(row) }}</span>
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
              row.kind === 'TRACK' ? row.responsibleName || userLabel(row.responsibleUserId) : '—'
            }}</template></el-table-column
          >
          <el-table-column label="计划解决时间" min-width="170"
            ><template #default="{ row }">{{
              row.dueAt ? formatDate(row.dueAt) : '—'
            }}</template></el-table-column
          >
          <el-table-column label="标注人" min-width="150"
            ><template #default="{ row }">{{
              row.annotatedByName || userLabel(row.annotatedBy)
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
    <InspectionUmcProjectItems
      v-model="expandedResultIds"
      v-model:detail-id="umcDetailId"
      :results="task.results"
      :device-label="deviceLabel"
      :show-items="false"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import type { FormInstance, FormRules, TagProps } from 'element-plus'
import type {
  TaskDetail,
  AnnotationInput,
  AnnotationView,
  ResultView,
  WireLong
} from '@/api/pms/inspection/tasks'
import UserSelect from '@/views/system/user/components/UserSelect.vue'
import { useUserStore } from '@/store/modules/user'
import { useMessage } from '@/hooks/web/useMessage'
import { formatDate } from '@/utils/formatTime'
import { getStrDictOptions } from '@/utils/dict'
import InspectionUmcRuleDetail from './InspectionUmcRuleDetail.vue'
import InspectionUmcProjectItems from './InspectionUmcProjectItems.vue'

defineOptions({ name: 'InspectionAnnotationPanel' })
const props = defineProps<{
  task: TaskDetail
  disabled: boolean
  umcMode?: boolean
  actionsTarget?: HTMLElement
}>()
const emit = defineEmits<{
  annotate: [payload: AnnotationInput]
  complete: []
  draftChanged: [dirty: boolean]
}>()
const smallScreen = useMediaQuery('(max-width: 767px)')
const userStore = useUserStore()
const message = useMessage()
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
const annotationFinding = (annotation: AnnotationView) => {
  const report = props.task.reports.find((item) => sameId(item.id, annotation.reportId))
  if (!report?.resultIds?.some((id) => sameId(id, annotation.resultId))) return undefined
  return props.task.results.find((item) => sameId(item.id, annotation.resultId))
}
const annotationFindingName = (annotation: AnnotationView) => {
  const result = annotationFinding(annotation)
  if (!result) return '来源检出问题不可用'
  return result.ruleName?.trim() || result.externalRule?.name?.trim() || '未提供检出问题名称'
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
const umcDetailId = ref('')
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
const mayDiscard = async () => {
  if (props.disabled || validating.value) return false
  if (!dirty.value) return true
  const identity = `${props.task.id}:${props.task.version}:${props.task.currentReportId}:${selectedResultId.value}`
  try {
    await message.confirm('当前标注尚未保存，确认放弃本次修改？', '未保存的标注')
    return (
      !props.disabled &&
      !validating.value &&
      identity ===
        `${props.task.id}:${props.task.version}:${props.task.currentReportId}:${selectedResultId.value}`
    )
  } catch {
    return false
  }
}
const closeEditor = async (done?: () => void) => {
  if (!(await mayDiscard())) return
  resetEditor()
  done?.()
}
const openEditor = async (result: ResultView) => {
  if (readonly.value || validating.value || !frozenResultsComplete.value) return
  if (selectedResultId.value === String(result.id)) return
  if (dirty.value && !(await mayDiscard())) return
  const previous = latestAnnotation(result.id)
  selectedResultId.value = String(result.id)
  Object.assign(form, {
    kind:
      previous?.kind && ['FALSE_POSITIVE', 'IGNORE', 'TRACK'].includes(previous.kind)
        ? previous.kind
        : '',
    reason: previous?.reason || '',
    changeReason: '',
    responsibleUserId: previous
      ? previous.responsibleUserId != null
        ? String(previous.responsibleUserId)
        : ''
      : userStore.getUser.id
        ? String(userStore.getUser.id)
        : '',
    dueAt: previous?.dueAt ? new Date(previous.dueAt) : null
  })
  snapshot.value = JSON.stringify(form)
  void nextTick(() => {
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
const users = ref<{ id: WireLong; nickname?: string; username?: string }[]>([])
const userLabel = (id?: WireLong) => {
  if (id == null) return '未提供用户'
  if (sameId(id, userStore.getUser.id) && userStore.getUser.nickname)
    return userStore.getUser.nickname
  if (sameId(id, props.task.ownerUserId) && props.task.ownerName) return props.task.ownerName
  const user = users.value.find((item) => sameId(item.id, id))
  return user?.nickname || user?.username || `用户 ${id}`
}
watch(
  () => form.kind,
  () => {
    formRef.value?.clearValidate()
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
    umcDetailId.value = ''
  }
)
watch(
  () => String(props.task.id),
  () => {
    users.value = []
  }
)
watch(dirty, (value) => emit('draftChanged', value))
</script>

<style scoped>
.finding-link {
  height: auto;
  text-align: left;
  white-space: normal;
  overflow-wrap: anywhere;
}

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
  font-size: 14px;
  color: #1a2332;
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
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.report-summary {
  margin-bottom: 16px;
}

.annotation-editor {
  min-width: 0;
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
  padding-top: 16px;
  margin-top: 20px;
  border-top: 1px solid #f0f2f5;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.history-heading {
  margin-top: 8px;
}

.annotation-history {
  --el-collapse-border-color: #f0f2f5;

  margin-top: 20px;
}

.annotation-history :deep(.el-collapse-item__header) {
  font-size: 13px;
  color: #1a2332;
}

.current-tag {
  margin-left: 8px;
}

@media (width <= 767px) {
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

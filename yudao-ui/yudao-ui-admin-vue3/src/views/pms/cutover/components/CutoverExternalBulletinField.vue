<template>
  <div class="external-bulletin-field" :aria-busy="!!busy">
    <el-alert v-if="!state?.interfaceCode" title="本项尚未配置外部接口，无法加载公告。" type="warning" :closable="false" show-icon />
    <el-alert v-else-if="!state.providerAvailable" title="外部接口尚未接通。可点击加载记录实际失败原因；未接通不代表没有公告。" type="warning" :closable="false" show-icon />
    <el-alert v-if="versionDirty" title="设备版本有未保存修改。加载时将先由页面保存版本；请保存版本并重新加载后再核查反馈。" type="info" :closable="false" show-icon />
    <el-alert v-else-if="state?.resultId != null && !state.contextCurrent" title="现有来源与当前设备或软件版本不一致，请重新加载并核查后再保存反馈。" type="warning" :closable="false" show-icon />

    <div class="actions">
      <el-button v-if="!readonly" :disabled="busy || !state?.interfaceCode" @click="load">
        {{ state?.resultId != null ? '重新加载外部公告' : '加载外部公告' }}
      </el-button>
      <el-tag v-if="state?.resultSourceCode === 'MANUAL'" type="success">人工核验证据已采用</el-tag>
      <el-tag v-else-if="state?.feedbackSaved" type="success">本项反馈已保存</el-tag>
      <el-tag v-else-if="state?.resultId != null" type="info">本项反馈尚未保存</el-tag>
    </div>

    <div v-if="selectedQueryIds.length" class="current-queries" aria-label="当前选中的逐设备查询结果">
      <section v-for="group in deviceGroups" :key="group.deviceId" class="device-query">
        <strong>设备 {{ deviceNames?.[group.deviceId] || group.deviceId }}</strong>
        <div v-for="query in group.queries" :key="String(query.id)" class="query-record">
          <div class="query-heading">
            <el-tag type="info">{{ contextLabel(query) }}</el-tag>
            <span>版本：{{ query.softwareVersion }}</span>
            <span>型号：{{ query.productModel || '未记录' }}</span>
          </div>
          <div class="query-times">开始：{{ time(query.startTime) }}；结束：{{ time(query.finishTime) }}</div>
          <div v-if="query.succeeded === true" class="query-status">
            {{ query.returnedRowCount === 0 ? '查询成功，无公告（0 条）' : `查询成功，返回 ${query.returnedRowCount ?? '未记录'} 条公告` }}
          </div>
          <div v-else-if="query.succeeded === false" class="query-failure" role="status">
            查询失败：{{ query.failureDetail || query.failureCode || '未提供失败原因' }}
            <span v-if="query.failureDetail && query.failureCode">（{{ query.failureCode }}）</span>
          </div>
          <div v-else class="query-status" role="status">查询进行中，尚未获得结果。</div>
        </div>
      </section>
      <el-alert v-if="selectedQueries.length !== selectedQueryIds.length" title="当前选中的查询记录不完整，请重新加载后核查。" type="warning" :closable="false" show-icon />
    </div>
    <p v-else class="help">尚未选中当前设备版本的查询结果。</p>

    <el-collapse v-if="historicalQueries.length" class="history">
      <el-collapse-item name="history" :title="`历史查询记录（${historicalQueries.length}）`">
        <div class="table-scroll">
          <el-table :data="historicalQueries" border>
            <el-table-column label="设备" min-width="130"><template #default="{ row }">{{ deviceNames?.[String(row.deviceId)] || row.deviceId }}</template></el-table-column>
            <el-table-column label="版本上下文" min-width="150"><template #default="{ row }">{{ contextLabel(row) }}<br />{{ row.softwareVersion }}</template></el-table-column>
            <el-table-column prop="productModel" label="型号" min-width="140" />
            <el-table-column label="查询时间" min-width="210"><template #default="{ row }">开始：{{ time(row.startTime) }}<br />结束：{{ time(row.finishTime) }}</template></el-table-column>
            <el-table-column label="查询结果" min-width="240">
              <template #default="{ row }">
                <span v-if="row.succeeded === true">{{ row.returnedRowCount === 0 ? '查询成功，无公告（0 条）' : `查询成功，返回 ${row.returnedRowCount ?? '未记录'} 条公告` }}</span>
                <span v-else-if="row.succeeded === false" class="query-failure">查询失败：{{ row.failureDetail || row.failureCode || '未提供失败原因' }}<span v-if="row.failureDetail && row.failureCode">（{{ row.failureCode }}）</span></span>
                <span v-else>查询进行中，尚未获得结果。</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-collapse-item>
    </el-collapse>

    <div v-if="bulletins.length" class="table-scroll" aria-label="只读公告来源及核查反馈">
      <el-table :data="bulletins" border row-key="sourceBulletinRowId">
        <el-table-column label="设备与软件版本" min-width="190">
          <template #default="{ row }">
            <template v-if="queryFor(row)">
              设备 {{ deviceNames?.[String(queryFor(row)?.deviceId)] || queryFor(row)?.deviceId }}<br />
              {{ contextLabel(queryFor(row)!) }}：{{ queryFor(row)?.softwareVersion }}
            </template>
            <span v-else>来源查询 {{ row.queryId }}（记录未提供）</span>
          </template>
        </el-table-column>
        <el-table-column prop="number" label="公告编号（原始）" min-width="150" />
        <el-table-column prop="subject" label="主题（原始）" min-width="220" />
        <el-table-column prop="description" label="问题描述（原始）" min-width="280" />
        <el-table-column prop="solution" label="解决方案（原始）" min-width="280" />
        <el-table-column label="是否涉及" min-width="150">
          <template #default="{ row }">
            <el-select v-if="!readonly" :model-value="feedbackFor(row).involved" :disabled="busy || !canFeedback" clearable placeholder="尚未核查" :aria-label="`公告${row.number || row.sourceBulletinRowId}是否涉及`" @update:model-value="updateFeedback({ row, involved: $event === true ? true : $event === false ? false : null })">
              <el-option label="涉及" :value="true" /><el-option label="不涉及" :value="false" />
            </el-select>
            <span v-else>{{ feedbackFor(row).involved === true ? '涉及' : feedbackFor(row).involved === false ? '不涉及' : '尚未核查' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="防范措施" min-width="280">
          <template #default="{ row }">
            <el-input v-if="!readonly" :model-value="feedbackFor(row).measures ?? ''" :disabled="busy || !canFeedback" type="textarea" :autosize="{ minRows: 2, maxRows: 6 }" :aria-label="`公告${row.number || row.sourceBulletinRowId}防范措施`" placeholder="填写本设备的核查措施" @update:model-value="updateFeedback({ row, measures: $event })" />
            <span v-else>{{ feedbackFor(row).measures || '未填写' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <p v-else-if="successfulEmpty" class="help">当前查询已全部成功，返回 0 条公告。<span v-if="!readonly">请确认已核查并保存本项反馈。</span></p>
    <p v-else-if="canFeedback" class="help">查询已成功，但当前公告行未提供，请重新加载并核查来源。</p>
    <p v-else-if="state?.resultSourceCode === 'MANUAL'" class="help">本项采用人工核验证据，原失败查询记录保留。</p>
    <p v-else-if="selectedQueries.some(query => query.succeeded === false)" class="query-failure">本次存在查询失败，尚不能确认有无公告或保存核查反馈。</p>

    <div v-if="!readonly && state?.resultSourceCode !== 'MANUAL'" class="save-feedback">
      <el-button type="primary" :disabled="busy || !canFeedback" @click="save">
        {{ successfulEmpty ? '确认已核查并保存反馈' : '保存本项反馈' }}
      </el-button>
      <span class="help">保存仅记录本项核查反馈；整个 P3 仍需在页面完成提交。</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElMessageBox } from 'element-plus'
import { formatDate } from '@/utils/formatTime'
import type { CutoverExternalItemState, CutoverExternalFeedback, CutoverExternalQuery, CutoverExternalBulletin, WireLong, WireDateTime } from '@/api/pms/cutover'

const props = defineProps<{
  state: CutoverExternalItemState | null
  modelValue: CutoverExternalFeedback[]
  readonly: boolean
  busy?: boolean
  versionDirty?: boolean
  deviceNames?: Record<string, string>
}>()
const emit = defineEmits<{
  'update:modelValue': [value: CutoverExternalFeedback[]]
  load: []
  save: []
}>()
const sameId = (left: WireLong, right: WireLong) => String(left) === String(right)
const selectedQueryIds = computed(() => props.state?.selectedQueryIds || [])
const selectedQueries = computed(() => (props.state?.queries || []).filter(query => selectedQueryIds.value.some(id => sameId(id, query.id))))
const historicalQueries = computed(() => (props.state?.queries || []).filter(query => !selectedQueryIds.value.some(id => sameId(id, query.id))))
const bulletins = computed(() => props.state?.bulletins || [])
const canFeedback = computed(() => !props.versionDirty && !!props.state?.contextCurrent && props.state.resultId != null && selectedQueryIds.value.length > 0 && selectedQueries.value.length === selectedQueryIds.value.length && selectedQueries.value.every(query => query.succeeded === true))
const successfulEmpty = computed(() => canFeedback.value && !bulletins.value.length && selectedQueries.value.every(query => query.returnedRowCount === 0))
const feedbackEdited = computed(() => props.modelValue.length !== bulletins.value.length || bulletins.value.some(row => {
  const feedback = props.modelValue.find(value => sameId(value.sourceBulletinRowId, row.sourceBulletinRowId))
  return !feedback || feedback.involved !== row.involved || feedback.measures !== row.measures
}))
const deviceGroups = computed(() => {
  const groups = new Map<string, CutoverExternalQuery[]>()
  for (const query of selectedQueries.value) {
    const key = String(query.deviceId)
    groups.set(key, [...(groups.get(key) || []), query])
  }
  return [...groups].map(([deviceId, queries]) => ({ deviceId, queries }))
})
const contextLabel = (query: CutoverExternalQuery) => query.versionContext === 'CURRENT' ? '当前版本' : '目标版本'
const time = (value: WireDateTime | null) => value == null ? '未记录' : formatDate(typeof value === 'string' && /^\d+$/.test(value) ? Number(value) : value)
const queryFor = (row: CutoverExternalBulletin) => props.state?.queries.find(query => sameId(query.id, row.queryId))
const feedbackFor = (row: CutoverExternalBulletin): CutoverExternalFeedback => props.modelValue.find(feedback => sameId(feedback.sourceBulletinRowId, row.sourceBulletinRowId)) || {
  sourceBulletinRowId: row.sourceBulletinRowId, involved: row.involved, measures: row.measures
}
const updateFeedback = (input: { row: CutoverExternalBulletin; involved?: boolean | null; measures?: string | null }) => {
  if (props.readonly || props.busy || !canFeedback.value) return
  const current = feedbackFor(input.row)
  const next = { ...current, ...('involved' in input ? { involved: input.involved ?? null } : { measures: input.measures ?? null }) }
  const found = props.modelValue.some(feedback => sameId(feedback.sourceBulletinRowId, input.row.sourceBulletinRowId))
  emit('update:modelValue', found
    ? props.modelValue.map(feedback => sameId(feedback.sourceBulletinRowId, input.row.sourceBulletinRowId) ? next : feedback)
    : [...props.modelValue, next])
}
const load = async () => {
  if (props.readonly || props.busy || !props.state?.interfaceCode) return
  if ((props.state.resultId != null && props.state.feedbackSaved) || feedbackEdited.value) {
    try {
      await ElMessageBox.confirm('刷新会生成新来源。原已保存反馈保留在历史；未保存的编辑将不被采用，需重新核查。', '确认重新加载', { confirmButtonText: '重新加载', cancelButtonText: '保留当前来源', type: 'warning' })
    } catch { return }
  }
  if (!props.readonly && !props.busy) emit('load')
}
const save = () => { if (!props.readonly && !props.busy && canFeedback.value) emit('save') }
</script>

<style scoped>
.external-bulletin-field { width: 100%; min-width: 0; }
.external-bulletin-field > .el-alert { margin-bottom: 12px; }
.actions, .save-feedback, .query-heading { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.actions, .current-queries, .history, .table-scroll { margin-bottom: 16px; }
.device-query { margin-bottom: 12px; padding: 12px; border: 1px solid var(--el-border-color); border-radius: var(--el-border-radius-base); }
.query-record { margin-top: 12px; }
.query-times, .query-status, .query-failure { margin-top: 8px; font-size: 13px; white-space: pre-wrap; overflow-wrap: anywhere; }
.query-times, .help { color: var(--el-text-color-secondary); }
.help { font-size: 13px; }
.query-failure { color: var(--el-color-danger); }
.table-scroll { max-width: 100%; overflow-x: auto; }
:deep(.el-table .cell) { white-space: pre-wrap; overflow-wrap: anywhere; }
.el-select { width: 100%; }
@media (max-width: 767px) {
  .save-feedback { align-items: flex-start; flex-direction: column; }
}
</style>

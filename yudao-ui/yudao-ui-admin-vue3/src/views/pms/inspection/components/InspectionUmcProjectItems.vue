<template>
  <section class="umc-project-items">
    <el-collapse
      v-if="showItems"
      v-model="expandedItemIds"
      class="inspection-items"
      data-testid="inspection-umc-items"
    >
      <el-collapse-item
        v-for="row in visibleResults"
        :key="String(row.id)"
        :name="String(row.id)"
        class="inspection-item"
        :data-outcome="row.outcome"
        data-testid="inspection-umc-item"
      >
        <template #title>
          <span class="item-heading">
            <span class="item-title">
              <el-tag class="item-level" :type="severityType(row)" size="small">{{
                severityLabel(row)
              }}</el-tag>
              <el-button
                link
                type="primary"
                class="item-name"
                aria-haspopup="dialog"
                data-testid="inspection-umc-item-name"
                @click.stop="detailResultId = String(row.id)"
                @keydown.stop
                @keyup.stop
              >
                <strong>{{
                  [
                    row.categoryName || row.externalRule?.type,
                    row.ruleName || row.externalRule?.name || '未提供项目名称'
                  ]
                    .filter(Boolean)
                    .join(' - ')
                }}</strong>
              </el-button>
            </span>
            <span class="item-actions">
              <slot name="actions" :row="row"></slot>
              <el-tag :type="outcomeType(row.outcome)" size="small">{{
                outcomeLabel(row.outcome)
              }}</el-tag>
            </span>
            <span v-if="deviceLabel" class="project-device-name">{{
              deviceLabel(row.deviceId)
            }}</span>
            <span v-if="row.externalRule?.info" class="item-description">{{
              row.externalRule.info
            }}</span>
          </span>
        </template>
        <div class="item-content">
          <template v-if="row.externalRule">
            <p v-if="row.externalRule.support !== true" class="attention">{{
              row.externalRule.support === false
                ? '本设备不支持此规则，不能视为通过。'
                : 'UMC未提供设备支持状态。'
            }}</p>
            <p v-if="row.externalRule.error" class="attention"
              >执行错误：{{ row.externalRule.error }}</p
            >
            <p v-if="row.externalRule.suggest" class="item-description"
              >处理建议：{{ row.externalRule.suggest }}</p
            >
            <dl
              v-if="row.externalRule.targetlist?.length"
              class="item-metrics"
              aria-label="巡检指标与全部对象值"
            >
              <div
                v-for="target in row.externalRule.targetlist"
                :key="target.id"
                class="metric-row"
              >
                <dt
                  >{{ target.name
                  }}<small :class="{ attention: target.result !== false }">{{
                    conclusion(target.result)
                  }}</small></dt
                >
                <dd>
                  <div
                    v-for="value in target.valuelist || []"
                    :key="value.id"
                    class="metric-value"
                    :class="{ attention: value.result === true }"
                  >
                    <span v-if="value.object" class="metric-object">{{ value.object }}：</span
                    >{{ value.value === '' ? '（空值）' : (value.value ?? '未提供') }}
                    <small>{{ conclusion(value.result) }}</small>
                  </div>
                  <span v-if="!target.valuelist?.length" class="help-text">本指标未返回对象值</span>
                  <p v-if="target.info">{{ target.info }}</p>
                  <p v-if="target.error" class="attention">执行错误：{{ target.error }}</p>
                </dd>
              </div>
            </dl>
            <p v-else class="help-text">本规则未返回指标。</p>
          </template>
          <template v-else>
            <p>{{ row.detailMessage || row.detailCode || '未提供判定说明' }}</p>
          </template>
          <slot name="annotation" :row="row"></slot>
        </div>
      </el-collapse-item>
    </el-collapse>
    <el-dialog
      v-model="detailVisible"
      :title="`${detailRow?.ruleName || detailRow?.externalRule?.name || '巡检项目'} · 检测依据与完整命令`"
      width="min(1000px, 94vw)"
      append-to-body
      destroy-on-close
    >
      <div v-if="detailRow" class="source-dialog-content">
        <p v-if="detailContext || deviceLabel" class="help-text">{{
          detailContext || deviceLabel?.(detailRow.deviceId)
        }}</p>
        <section aria-label="检测依据" class="source-dialog-section">
          <h3>检测依据</h3>
          <template v-if="detailRow.externalRule">
            <p v-if="!detailRow.externalRule.info" class="help-text">UMC未提供判定说明。</p>
            <InspectionUmcRuleDetail
              :rule="detailRow.externalRule"
              :show-commands="false"
              :show-identity="false"
            />
          </template>
          <p v-else>{{ detailRow.detailMessage || detailRow.detailCode || '未提供检测依据' }}</p>
        </section>
        <section aria-label="完整命令" class="source-dialog-section">
          <h3>完整命令</h3>
          <p class="source-command">{{ detailRow.externalRule?.cmd || '未提供执行命令' }}</p>
          <h4>完整命令输出</h4>
          <pre class="source-output">{{
            (detailRow.externalRule ? detailRow.externalRule.output : detailRow.outputSummary) ==
            null
              ? '未提供命令输出'
              : (detailRow.externalRule
                    ? detailRow.externalRule.output
                    : detailRow.outputSummary) === ''
                ? '（空输出）'
                : detailRow.externalRule
                  ? detailRow.externalRule.output
                  : detailRow.outputSummary
          }}</pre>
        </section>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog></section
  >
</template>
<script setup lang="ts">
import { computed, watch } from 'vue'
import type { TagProps } from 'element-plus'
import type { ResultView, WireLong } from '@/api/pms/inspection/tasks'
import InspectionUmcRuleDetail from './InspectionUmcRuleDetail.vue'
const props = withDefaults(
  defineProps<{
    results: ResultView[]
    modelValue: string[]
    detailId: string
    detailContext?: string
    deviceLabel?: (id: WireLong) => string
    showItems?: boolean
  }>(),
  { showItems: true }
)
const emit = defineEmits<{
  'update:modelValue': [ids: string[]]
  'update:detailId': [id: string]
}>()
const visibleResults = computed(() => props.results)
const expandedItemIds = computed({
  get: () => props.modelValue,
  set: (ids: Array<string | number>) => emit('update:modelValue', ids.map(String))
})
const detailResultId = computed({
  get: () => props.detailId,
  set: (id: string) => emit('update:detailId', id)
})
const detailRow = computed(() => props.results.find((row) => String(row.id) === props.detailId))
const detailVisible = computed({
  get: () => detailRow.value != null,
  set: (value: boolean) => {
    if (!value) emit('update:detailId', '')
  }
})
watch(detailRow, (row) => {
  if (!row && props.detailId) emit('update:detailId', '')
})
const severityLabel = (row: ResultView) => {
  const level = row.externalRule?.level
  return level === 3
    ? '致命'
    : level === 2
      ? '重要'
      : level === 1
        ? '一般'
        : level == null
          ? row.severityName || '未提供'
          : `UMC级别${level}`
}
// 沿用 pms_inspection_rule_severity 字典颜色及设备概览中既有的致命/重要颜色。
const severityType = (row: ResultView): TagProps['type'] => {
  const code =
    row.externalRule?.level == null ? row.severityCode : `UMC_LEVEL_${row.externalRule.level}`
  return code === 'FATAL' || code === 'UMC_LEVEL_3'
    ? 'danger'
    : code === 'SEVERE' || code === 'UMC_LEVEL_2'
      ? 'warning'
      : 'info'
}
const outcomeLabel = (outcome?: string) =>
  ({ PASS: '通过', ABNORMAL: '异常', UNJUDGED: '未判定', ERROR: '判定错误' })[outcome || ''] ||
  outcome ||
  '未判定'
const outcomeType = (outcome?: string): TagProps['type'] =>
  outcome === 'PASS'
    ? 'success'
    : ['ABNORMAL', 'ERROR'].includes(outcome || '')
      ? 'danger'
      : 'warning'
const conclusion = (value?: boolean) =>
  value === true ? '异常' : value === false ? '正常' : '未判定'
</script>
<style scoped>
.item-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.help-text {
  margin: 6px 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

dl,
dd {
  margin: 0;
}

dt {
  color: var(--el-text-color-secondary);
}

dd {
  min-width: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.attention {
  color: var(--el-color-danger);
}

.project-device-name {
  flex-basis: 100%;
  font-size: 12px;
  font-weight: normal;
  color: var(--el-text-color-secondary);
}

.inspection-items {
  display: grid;
  gap: 12px;
  border: 0;
}

.inspection-item {
  min-width: 0;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
}

.item-heading {
  flex: 1;
  min-width: 0;
}

.item-title {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}

.item-name {
  max-width: 100%;
  height: auto;
  padding: 0;
  font-size: inherit;
  text-align: left;
  white-space: normal;
  vertical-align: baseline;
}

.inspection-item :deep(.el-collapse-item__header) {
  height: auto;
  min-height: 48px;
  padding: 12px 16px;
  line-height: 1.6;
  border-bottom: 0;
  border-radius: 6px;
  gap: 12px;
}

.inspection-item :deep(.el-collapse-item__wrap) {
  border-bottom: 0;
  border-radius: 0 0 6px 6px;
}

.inspection-item :deep(.el-collapse-item__content) {
  padding: 0 16px 16px;
  font-size: inherit;
}

.item-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
  min-width: 0;
}

.item-heading > .item-description {
  min-width: 0;
  margin: 0;
  font-size: 13px;
  font-weight: normal;
  color: var(--el-text-color-regular);
  flex-basis: 100%;
}

.item-level {
  margin-right: 8px;
  vertical-align: middle;
}

.item-description,
.item-content > p {
  margin: 8px 0;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.item-metrics {
  margin-top: 12px;
}

.metric-row {
  display: grid;
  grid-template-columns: minmax(110px, 22%) minmax(0, 1fr);
  gap: 12px;
  padding: 8px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

.metric-row dt small {
  display: block;
  margin-top: 3px;
  font-size: 11px;
}

.metric-value {
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.metric-value small {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}

.metric-object {
  font-weight: 500;
}

.metric-row dd p {
  margin: 6px 0 0;
}

.source-dialog-content {
  max-height: 68vh;
  overflow: auto;
}

.source-dialog-section + .source-dialog-section {
  padding-top: 16px;
  margin-top: 20px;
  border-top: 1px solid var(--el-border-color-light);
}

.source-dialog-section h3 {
  margin: 0 0 12px;
  font-size: 15px;
}

.source-dialog-section h4 {
  margin: 16px 0 8px;
  font-size: 13px;
}

.source-command,
.source-output {
  margin: 0;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.source-output {
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

@media (width <= 600px) {
  .item-title,
  .item-actions {
    flex-basis: 100%;
  }

  .item-actions :deep(.el-button) {
    height: auto;
    text-align: left;
    white-space: normal;
  }

  .inspection-item :deep(.el-collapse-item__header) {
    padding: 12px;
  }

  .inspection-item :deep(.el-collapse-item__content) {
    padding: 0 12px 12px;
  }

  .metric-row {
    grid-template-columns: minmax(0, 1fr);
    gap: 4px;
  }

  .metric-row dt small {
    display: inline;
    margin-left: 8px;
  }
}
</style>

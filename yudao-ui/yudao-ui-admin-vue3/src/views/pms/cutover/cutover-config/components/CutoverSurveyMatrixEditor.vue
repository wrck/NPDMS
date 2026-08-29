<template>
  <section aria-labelledby="survey-matrix-title">
    <div class="matrix-heading">
      <div>
        <h3 id="survey-matrix-title">调研矩阵</h3>
        <p>十二类调研项和绑定规则均写回当前配置修订，不形成独立版本。</p>
      </div>
      <el-tag :type="coveredCategoryCount === 12 ? 'success' : 'danger'" effect="plain">
        核心类别 {{ coveredCategoryCount }}/12
      </el-tag>
    </div>

    <el-alert
      v-for="error in surveyErrors"
      :key="`${error.location}-${error.message}`"
      :title="error.location"
      :description="error.message"
      type="error"
      :closable="false"
      show-icon
      class="mb-8px"
    />

    <div class="category-list" role="list" aria-label="十二类核心调研内容">
      <el-tag
        v-for="(label, code) in SURVEY_CATEGORY_LABELS"
        :key="code"
        :type="hasSurveyCategory(code) ? 'success' : 'danger'"
        effect="plain"
        role="listitem"
      >
        {{ label }} · {{ hasSurveyCategory(code) ? '已配置' : '缺失' }}
      </el-tag>
    </div>

    <el-table :data="projection.items" border empty-text="暂无业务调研项">
      <el-table-column label="业务分类" min-width="230">
        <template #default="{ row }">
          <el-select
            v-model="row.businessCategoryCode"
            :disabled="readonly"
            filterable
            class="!w-full"
          >
            <el-option
              v-for="(label, code) in SURVEY_CATEGORY_LABELS"
              :key="code"
              :label="`${label}（${code}）`"
              :value="code"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column prop="stableItemKey" label="稳定项键" min-width="190" />
      <el-table-column prop="itemName" label="调研项" min-width="210" show-overflow-tooltip />
      <el-table-column label="界面控件" min-width="180">
        <template #default="{ row }">
          <template v-if="isFileCategory(row.businessCategoryCode)">
            <el-tag type="info" effect="plain">平台文件引用</el-tag>
            <el-button
              v-if="!readonly && row.interfaceFormat !== 'PmsFileArtifact'"
              link
              type="primary"
              @click="configureFileReference(row)"
              >设为文件引用</el-button
            >
          </template>
          <span v-else>{{ row.interfaceFormat }}</span>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="76" align="center">
        <template #default="{ row }">
          <el-switch v-model="row.enabled" :disabled="readonly" />
        </template>
      </el-table-column>
    </el-table>

    <el-card v-if="backgroundItem" shadow="never" class="background-card">
      <template #header>
        <div class="card-heading">
          <span>割接背景字段与条件显示</span>
          <el-button v-if="!readonly" type="primary" plain @click="configureBackground">
            应用标准六字段
          </el-button>
        </div>
      </template>
      <div class="background-fields" role="list" aria-label="割接背景六字段">
        <div v-for="field in backgroundFields" :key="field.code" role="listitem">
          <span>{{ backgroundFieldLabel(field.code) }}</span>
          <small v-if="field.visibleWhen">
            当 {{ backgroundFieldLabel(field.visibleWhen.field) }} = 是时显示
          </small>
          <small v-else>始终显示</small>
        </div>
      </div>
      <el-empty v-if="backgroundFields.length === 0" description="尚未配置割接背景字段" />
    </el-card>

    <div class="rule-heading">
      <h4>调研绑定规则（{{ projection.rules.length }}）</h4>
      <el-button
        type="primary"
        plain
        :disabled="readonly || selectedRules.length === 0"
        @click="bulkVisible = true"
      >
        批量编辑已选 {{ selectedRules.length }} 条
      </el-button>
    </div>
    <el-table
      :data="projection.rules"
      border
      empty-text="暂无调研绑定规则"
      @selection-change="onSelectionChange"
    >
      <el-table-column v-if="!readonly" type="selection" width="46" />
      <el-table-column prop="stableRuleKey" label="规则键" min-width="190" />
      <el-table-column prop="stableItemKey" label="调研项键" min-width="190" />
      <el-table-column label="维度条件" min-width="280">
        <template #default="{ row }">
          <span v-if="Object.keys(row.dimensionConditions).length === 0">未配置</span>
          <el-tag
            v-for="(values, dimension) in row.dimensionConditions"
            :key="dimension"
            effect="plain"
            class="mr-4px mb-4px"
            >{{ dimension }}：{{ conditionText(values) }}</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column label="必填" width="76" align="center">
        <template #default="{ row }">
          <el-switch v-model="row.requiredResult" :disabled="readonly" />
        </template>
      </el-table-column>
      <el-table-column label="优先级" width="116">
        <template #default="{ row }">
          <el-input-number v-model="row.priority" :disabled="readonly" :min="0" />
        </template>
      </el-table-column>
      <el-table-column label="启用" width="76" align="center">
        <template #default="{ row }">
          <el-switch v-model="row.enabled" :disabled="readonly" />
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="bulkVisible"
      title="批量编辑调研绑定"
      width="min(680px, 92vw)"
      append-to-body
    >
      <el-form label-width="100px">
        <el-form-item label="割接类型">
          <el-select v-model="bulk.cutoverTypeCodes" multiple filterable class="!w-full">
            <el-option
              v-for="option in getStrDictOptions(DICT_TYPE.PMS_CUTOVER_TYPE)"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="组网模式">
          <el-select v-model="bulk.networkModeCodes" multiple filterable class="!w-full">
            <el-option
              v-for="option in getStrDictOptions(DICT_TYPE.PMS_NETWORK_MODE)"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="设备类型">
          <el-select v-model="bulk.deviceTypeCodes" multiple filterable class="!w-full">
            <el-option
              v-for="option in getStrDictOptions('pms_device_type')"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="割接等级">
          <el-select v-model="bulk.levelCodes" multiple class="!w-full">
            <el-option
              v-for="level in MATRIX_LEVEL_CODES"
              :key="level"
              :label="level"
              :value="level"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="必填"><el-switch v-model="bulk.requiredResult" /></el-form-item>
        <el-form-item label="优先级"
          ><el-input-number v-model="bulk.priority" :min="0"
        /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="bulk.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bulkVisible = false">取消</el-button>
        <el-button type="primary" @click="applyBulk">应用到所选规则</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type {
  CutoverBindingRule,
  CutoverChecklistItem,
  CutoverConfiguration,
  CutoverValidationError
} from '@/api/pms/cutover/cutover-config'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import {
  MATRIX_LEVEL_CODES,
  SURVEY_CATEGORY_LABELS,
  applyBulkBinding,
  ensureCutoverBackgroundSchema,
  projectSurveyMatrix
} from './cutoverMatrix'

interface BackgroundField {
  code: string
  visibleWhen?: { field: string; equals: boolean }
}
const props = defineProps<{ readonly: boolean; validationErrors: CutoverValidationError[] }>()
const model = defineModel<CutoverConfiguration>({ required: true })
const projection = computed(() => projectSurveyMatrix(model.value))
const surveyErrors = computed(() =>
  props.validationErrors.filter((error) => error.location.startsWith('survey.'))
)
const coveredCategoryCount = computed(
  () =>
    new Set(
      projection.value.items
        .filter(
          (item) =>
            item.enabled &&
            !!item.businessCategoryCode &&
            item.businessCategoryCode in SURVEY_CATEGORY_LABELS
        )
        .map((item) => item.businessCategoryCode)
        .filter(Boolean)
    ).size
)
const hasSurveyCategory = (code: string) =>
  projection.value.items.some((item) => item.enabled && item.businessCategoryCode === code)
const backgroundItem = computed(() =>
  projection.value.items.find((item) => item.businessCategoryCode === 'CUTOVER_BACKGROUND')
)
const backgroundFields = computed(() =>
  Array.isArray(backgroundItem.value?.interfaceSchema.fields)
    ? (backgroundItem.value.interfaceSchema.fields as BackgroundField[])
    : []
)
const fieldLabels: Record<string, string> = {
  solvesOnlineIssue: '是否解决网上问题',
  issueTicketNo: '问题工单号',
  issueHandler: '工单处理人',
  repeatCutover: '是否二次割接',
  firstCutoverOwner: '首次割接保障人',
  backgroundDescription: '割接背景表述'
}
const backgroundFieldLabel = (code: string) => fieldLabels[code] || code
const configureBackground = () => {
  if (backgroundItem.value) ensureCutoverBackgroundSchema(backgroundItem.value.interfaceSchema)
}
const fileCategories = new Set([
  'CURRENT_TOPOLOGY',
  'CONNECTIVITY_TEST_CASE',
  'VENDOR_CONFIG_TRANSLATION'
])
const isFileCategory = (category?: string) => !!category && fileCategories.has(category)
const configureFileReference = (item: CutoverChecklistItem) => {
  item.interfaceFormat = 'PmsFileArtifact'
  item.interfaceSchema = { type: 'PmsFileArtifact' }
}
const conditionText = (value: unknown) =>
  Array.isArray(value) ? value.join('、') : String(value ?? '')
const selectedRules = ref<CutoverBindingRule[]>([])
const bulkVisible = ref(false)
const bulk = reactive({
  cutoverTypeCodes: [] as string[],
  networkModeCodes: [] as string[],
  deviceTypeCodes: [] as string[],
  levelCodes: [] as string[],
  requiredResult: true,
  priority: 10,
  enabled: true
})
const conditionValues = (rule: CutoverBindingRule, dimension: string) => {
  const value = rule.dimensionConditions[dimension]
  return Array.isArray(value) ? value.map(String) : []
}
const onSelectionChange = (rows: CutoverBindingRule[]) => {
  selectedRules.value = rows
  const first = rows[0]
  if (!first) return
  bulk.cutoverTypeCodes = conditionValues(first, 'CUTOVER_TYPE')
  bulk.networkModeCodes = conditionValues(first, 'NETWORK_MODE')
  bulk.deviceTypeCodes = conditionValues(first, 'DEVICE_TYPE')
  bulk.levelCodes = conditionValues(first, 'CUTOVER_LEVEL')
  bulk.requiredResult = first.requiredResult ?? false
  bulk.priority = first.priority
  bulk.enabled = first.enabled
}
const applyBulk = () => {
  applyBulkBinding(model.value, {
    ruleKeys: selectedRules.value.map((rule) => rule.stableRuleKey),
    ...bulk
  })
  bulkVisible.value = false
}
</script>

<style scoped>
.matrix-heading,
.rule-heading,
.card-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.matrix-heading h3,
h4 {
  margin: 16px 0 8px;
}

.matrix-heading p {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.background-card {
  margin-top: 16px;
}

.category-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 16px 0 12px;
}

.background-fields {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.background-fields > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
}

.background-fields small {
  color: var(--el-text-color-secondary);
}

@media (width <= 768px) {
  .matrix-heading,
  .rule-heading,
  .card-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .background-fields {
    grid-template-columns: 1fr;
  }
}
</style>

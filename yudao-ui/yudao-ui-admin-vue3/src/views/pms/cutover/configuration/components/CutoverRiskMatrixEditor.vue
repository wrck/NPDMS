<template>
  <section aria-labelledby="risk-matrix-title">
    <div class="matrix-heading">
      <div>
        <h3 id="risk-matrix-title">风险矩阵</h3>
        <p>普通风险与双机检查仍属于当前配置修订，保存和发布沿用根聚合。</p>
      </div>
      <el-tag :type="dualTotal === 97 ? 'success' : 'danger'" effect="plain">
        五类双机检查 {{ dualTotal }}/97 项
      </el-tag>
    </div>

    <div class="baseline-list" role="list" aria-label="五类双机检查数量">
      <el-tag
        v-for="(expected, code) in DUAL_BASELINES"
        :key="code"
        :type="dualCount(code) === expected ? 'success' : 'danger'"
        effect="plain"
        role="listitem"
      >
        {{ DUAL_LABELS[code] }} {{ dualCount(code) }}/{{ expected }}
      </el-tag>
    </div>

    <el-alert
      v-for="error in riskErrors"
      :key="`${error.location}-${error.message}`"
      :title="error.location"
      :description="error.message"
      type="error"
      :closable="false"
      show-icon
      class="mb-8px"
    />

    <h4>普通风险类别（{{ ordinaryCategoryCount }}/24）</h4>
    <div class="category-list" role="list" aria-label="普通风险基准类别">
      <el-tag
        v-for="(label, code) in RISK_CATEGORY_LABELS"
        :key="code"
        :type="hasRiskCategory(code) ? 'success' : 'danger'"
        effect="plain"
        role="listitem"
      >
        {{ label }} · {{ hasRiskCategory(code) ? '已配置' : '缺失' }}
      </el-tag>
    </div>
    <el-table :data="ordinaryItems" border empty-text="暂无普通风险项">
      <el-table-column label="业务分类" min-width="220">
        <template #default="{ row }">
          <el-select
            v-model="row.businessCategoryCode"
            :disabled="readonly"
            filterable
            class="!w-full"
          >
            <el-option
              v-for="(label, code) in RISK_CATEGORY_LABELS"
              :key="code"
              :label="`${label}（${code}）`"
              :value="code"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column prop="stableItemKey" label="稳定项键" min-width="190" />
      <el-table-column prop="itemName" label="风险项" min-width="220" show-overflow-tooltip />
      <el-table-column label="启用" width="76" align="center">
        <template #default="{ row }">
          <el-switch v-model="row.enabled" :disabled="readonly" />
        </template>
      </el-table-column>
    </el-table>

    <h4>双机部署检查</h4>
    <el-collapse>
      <el-collapse-item v-for="(_, code) in DUAL_BASELINES" :key="code" :name="code">
        <template #title>
          <span>{{ DUAL_LABELS[code] }}：{{ dualCount(code) }}/{{ DUAL_BASELINES[code] }} 项</span>
        </template>
        <el-table :data="dualItems(code)" border empty-text="当前分类暂无检查项">
          <el-table-column prop="stableItemKey" label="稳定项键" min-width="190" />
          <el-table-column prop="itemName" label="检查项" min-width="240" show-overflow-tooltip />
          <el-table-column label="所属子表" min-width="170">
            <template #default="{ row }">
              <el-select v-model="row.subtableCode" :disabled="readonly" class="!w-full">
                <el-option
                  v-for="(label, value) in DUAL_LABELS"
                  :key="value"
                  :label="label"
                  :value="value"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="启用" width="76" align="center">
            <template #default="{ row }">
              <el-switch v-model="row.enabled" :disabled="readonly" />
            </template>
          </el-table-column>
        </el-table>
      </el-collapse-item>
    </el-collapse>

    <div class="rule-heading">
      <h4>风险绑定规则（{{ projection.rules.length }}）</h4>
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
      empty-text="暂无风险绑定规则"
      @selection-change="onSelectionChange"
    >
      <el-table-column v-if="!readonly" type="selection" width="46" />
      <el-table-column prop="stableRuleKey" label="规则键" min-width="190" />
      <el-table-column prop="stableItemKey" label="风险项键" min-width="190" />
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
      title="批量编辑风险绑定"
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
  CutoverConfiguration,
  CutoverValidationError
} from '@/api/pms/cutover/cutover-config'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import {
  DUAL_BASELINES,
  DUAL_LABELS,
  MATRIX_LEVEL_CODES,
  RISK_CATEGORY_LABELS,
  applyBulkBinding,
  projectRiskMatrix
} from './cutoverMatrix'

const props = defineProps<{ readonly: boolean; validationErrors: CutoverValidationError[] }>()
const model = defineModel<CutoverConfiguration>({ required: true })
const projection = computed(() => projectRiskMatrix(model.value))
const ordinaryItems = computed(() =>
  projection.value.items.filter((item) => item.itemType === 'RISK')
)
const ordinaryCategoryCount = computed(
  () =>
    new Set(
      ordinaryItems.value
        .filter(
          (item) =>
            item.enabled &&
            !!item.businessCategoryCode &&
            item.businessCategoryCode in RISK_CATEGORY_LABELS
        )
        .map((item) => item.businessCategoryCode)
    ).size
)
const hasRiskCategory = (code: string) =>
  ordinaryItems.value.some((item) => item.enabled && item.businessCategoryCode === code)
const riskErrors = computed(() =>
  props.validationErrors.filter((error) => error.location.startsWith('risk.'))
)
const dualItems = (code: keyof typeof DUAL_BASELINES) =>
  projection.value.items.filter(
    (item) => item.itemType === 'DUAL_MACHINE_CHECK' && item.subtableCode === code
  )
const dualCount = (code: keyof typeof DUAL_BASELINES) =>
  dualItems(code).filter((item) => item.enabled).length
const dualTotal = computed(() =>
  Object.keys(DUAL_BASELINES).reduce(
    (total, code) => total + dualCount(code as keyof typeof DUAL_BASELINES),
    0
  )
)
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
.rule-heading {
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

.baseline-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 16px 0;
}

.category-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

@media (width <= 768px) {
  .matrix-heading,
  .rule-heading {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>

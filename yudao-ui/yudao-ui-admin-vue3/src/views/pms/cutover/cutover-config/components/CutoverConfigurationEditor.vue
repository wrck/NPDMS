<template>
  <el-form ref="formRef" :model="model" :rules="rules" label-width="112px" class="config-editor">
    <el-alert
      v-if="readonly"
      title="已发布或已停用修订为只读；如需修改，请复制为新草稿。"
      type="info"
      :closable="false"
      show-icon
      class="mb-16px"
    />
    <el-tabs v-model="activeTab">
      <el-tab-pane label="基本信息" name="basic">
        <el-row :gutter="16">
          <el-col :xs="24" :md="12">
            <el-form-item label="配置编码" prop="configurationCode">
              <el-input
                v-model="model.configurationCode"
                :disabled="readonly || !!model.id"
                maxlength="64"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="配置名称" prop="configurationName">
              <el-input v-model="model.configurationName" :disabled="readonly" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="变更说明">
              <el-input
                v-model="model.changeSummary"
                :disabled="readonly"
                type="textarea"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-tab-pane>

      <el-tab-pane :label="`动态维度（${model.dimensions.length}）`" name="dimensions">
        <div class="table-actions">
          <el-button v-if="!readonly" type="primary" plain @click="addDimension"
            >新增维度</el-button
          >
        </div>
        <el-table :data="model.dimensions" border empty-text="暂无维度定义">
          <el-table-column label="编码" min-width="150"
            ><template #default="{ row }"
              ><el-input v-model="row.code" :disabled="readonly" /></template
          ></el-table-column>
          <el-table-column label="名称" min-width="130"
            ><template #default="{ row }"
              ><el-input v-model="row.name" :disabled="readonly" /></template
          ></el-table-column>
          <el-table-column label="数据类型" width="120"
            ><template #default="{ row }"
              ><el-input v-model="row.dataType" :disabled="readonly" /></template
          ></el-table-column>
          <el-table-column label="允许值来源" min-width="190"
            ><template #default="{ row }"
              ><el-input v-model="row.valueSource" :disabled="readonly" /></template
          ></el-table-column>
          <el-table-column label="Owner" width="110"
            ><template #default="{ row }"
              ><el-input v-model="row.owner" :disabled="readonly" /></template
          ></el-table-column>
          <el-table-column label="上下文路径" min-width="180"
            ><template #default="{ row }"
              ><el-input v-model="row.contextPath" :disabled="readonly" /></template
          ></el-table-column>
          <el-table-column label="启用" width="72" align="center"
            ><template #default="{ row }"
              ><el-switch v-model="row.enabled" :disabled="readonly" /></template
          ></el-table-column>
          <el-table-column v-if="!readonly" label="操作" width="80" fixed="right"
            ><template #default="{ $index }"
              ><el-button link type="danger" @click="model.dimensions.splice($index, 1)"
                >删除</el-button
              ></template
            ></el-table-column
          >
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="`统一采集项（${model.items.length}）`" name="items">
        <div class="table-actions">
          <el-radio-group v-model="itemFilter" size="small">
            <el-radio-button label="ALL">全部</el-radio-button>
            <el-radio-button label="BUSINESS_SURVEY">业务调研</el-radio-button>
            <el-radio-button label="RISK">风险考察</el-radio-button>
            <el-radio-button label="DUAL_MACHINE_CHECK">双机检查</el-radio-button>
          </el-radio-group>
          <el-button v-if="!readonly" type="primary" plain @click="addItem">新增采集项</el-button>
        </div>
        <el-table :data="filteredItems" border empty-text="暂无采集项">
          <el-table-column label="稳定项键" min-width="170"
            ><template #default="{ row }"
              ><el-input v-model="row.stableItemKey" :disabled="readonly" /></template
          ></el-table-column>
          <el-table-column label="类型" min-width="145"
            ><template #default="{ row }"
              ><el-select v-model="row.itemType" :disabled="readonly" class="!w-full"
                ><el-option
                  v-for="option in itemTypes"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value" /></el-select></template
          ></el-table-column>
          <el-table-column label="项命名" min-width="170"
            ><template #default="{ row }"
              ><el-input v-model="row.itemName" :disabled="readonly" /></template
          ></el-table-column>
          <el-table-column label="业务分类码" min-width="190"
            ><template #default="{ row }"
              ><el-input v-model="row.businessCategoryCode" :disabled="readonly" /></template
          ></el-table-column>
          <el-table-column label="业务含义" min-width="190"
            ><template #default="{ row }"
              ><el-input v-model="row.itemDescription" :disabled="readonly" /></template
          ></el-table-column>
          <el-table-column label="界面格式" width="125"
            ><template #default="{ row }"
              ><el-input v-model="row.interfaceFormat" :disabled="readonly" /></template
          ></el-table-column>
          <el-table-column label="反馈格式" width="145"
            ><template #default="{ row }"
              ><el-input v-model="row.feedbackFormat" :disabled="readonly" /></template
          ></el-table-column>
          <el-table-column label="界面 Schema JSON" min-width="240"
            ><template #default="{ row }"
              ><el-input
                :model-value="jsonText(row.interfaceSchema)"
                :disabled="readonly"
                @change="updateItemJson(row, 'interfaceSchema', $event, '界面 Schema')" /></template
          ></el-table-column>
          <el-table-column label="工作方式" width="125"
            ><template #default="{ row }"
              ><el-select v-model="row.workMode" :disabled="readonly" class="!w-full"
                ><el-option label="人工" value="MANUAL" /><el-option
                  label="外部数据源"
                  value="EXTERNAL" /></el-select></template
          ></el-table-column>
          <el-table-column label="外部源配置 JSON" min-width="280"
            ><template #default="{ row }"
              ><el-input
                :model-value="jsonText(row.externalSourceConfig || {})"
                :disabled="readonly || row.workMode !== 'EXTERNAL'"
                @change="
                  updateItemJson(row, 'externalSourceConfig', $event, '外部源配置')
                " /></template
          ></el-table-column>
          <el-table-column label="所属子表" width="150"
            ><template #default="{ row }"
              ><el-select
                v-model="row.subtableCode"
                clearable
                :disabled="readonly || row.itemType !== 'DUAL_MACHINE_CHECK'"
                class="!w-full"
                ><el-option
                  v-for="mode in subtableOptions"
                  :key="mode"
                  :label="mode"
                  :value="mode" /></el-select></template
          ></el-table-column>
          <el-table-column label="必填" width="72" align="center"
            ><template #default="{ row }"
              ><el-switch v-model="row.required" :disabled="readonly" /></template
          ></el-table-column>
          <el-table-column label="启用" width="72" align="center"
            ><template #default="{ row }"
              ><el-switch v-model="row.enabled" :disabled="readonly" /></template
          ></el-table-column>
          <el-table-column label="排序" width="110"
            ><template #default="{ row }"
              ><el-input-number
                v-model="row.sortOrder"
                :disabled="readonly"
                :min="0"
                controls-position="right" /></template
          ></el-table-column>
          <el-table-column v-if="!readonly" label="操作" width="80" fixed="right"
            ><template #default="{ row }"
              ><el-button link type="danger" @click="removeItem(row)">删除</el-button></template
            ></el-table-column
          >
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="风险矩阵" name="risk">
        <CutoverRiskMatrixEditor
          v-model="model"
          :readonly="readonly"
          :validation-errors="validationErrors"
        />
      </el-tab-pane>

      <el-tab-pane label="调研矩阵" name="survey">
        <CutoverSurveyMatrixEditor
          v-model="model"
          :readonly="readonly"
          :validation-errors="validationErrors"
        />
      </el-tab-pane>

      <el-tab-pane :label="`绑定规则（${model.bindingRules.length}）`" name="rules">
        <div class="table-actions"
          ><el-button v-if="!readonly" type="primary" plain @click="addRule"
            >新增规则</el-button
          ></div
        >
        <el-table :data="model.bindingRules" border empty-text="暂无绑定规则">
          <el-table-column label="稳定规则键" min-width="170"
            ><template #default="{ row }"
              ><el-input v-model="row.stableRuleKey" :disabled="readonly" /></template
          ></el-table-column>
          <el-table-column label="采集项" min-width="190"
            ><template #default="{ row }"
              ><el-select
                v-model="row.stableItemKey"
                :disabled="readonly"
                filterable
                class="!w-full"
                ><el-option
                  v-for="item in model.items"
                  :key="item.stableItemKey"
                  :label="`${item.itemName}（${item.stableItemKey}）`"
                  :value="item.stableItemKey" /></el-select></template
          ></el-table-column>
          <el-table-column label="维度条件 JSON" min-width="280"
            ><template #default="{ row }"
              ><el-input
                :model-value="jsonText(row.dimensionConditions)"
                :disabled="readonly"
                @change="updateConditions(row, $event)" /></template
          ></el-table-column>
          <el-table-column label="优先级" width="110"
            ><template #default="{ row }"
              ><el-input-number
                v-model="row.priority"
                :disabled="readonly"
                :min="0"
                controls-position="right" /></template
          ></el-table-column>
          <el-table-column label="必填" width="72" align="center"
            ><template #default="{ row }"
              ><el-switch v-model="row.requiredResult" :disabled="readonly" /></template
          ></el-table-column>
          <el-table-column label="启用" width="72" align="center"
            ><template #default="{ row }"
              ><el-switch v-model="row.enabled" :disabled="readonly" /></template
          ></el-table-column>
          <el-table-column v-if="!readonly" label="操作" width="80" fixed="right"
            ><template #default="{ $index }"
              ><el-button link type="danger" @click="model.bindingRules.splice($index, 1)"
                >删除</el-button
              ></template
            ></el-table-column
          >
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="`方案章节（${model.planTemplateSections.length}）`" name="sections">
        <div class="table-actions"
          ><el-button v-if="!readonly" type="primary" plain @click="addSection"
            >新增章节</el-button
          ></div
        >
        <el-table :data="model.planTemplateSections" border empty-text="暂无方案章节">
          <el-table-column label="稳定章节键" min-width="170"
            ><template #default="{ row }"
              ><el-input v-model="row.stableSectionKey" :disabled="readonly" /></template
          ></el-table-column>
          <el-table-column label="章节标题" min-width="190"
            ><template #default="{ row }"
              ><el-input v-model="row.title" :disabled="readonly" /></template
          ></el-table-column>
          <el-table-column label="适用等级" min-width="190"
            ><template #default="{ row }"
              ><el-select v-model="row.levelCodes" multiple :disabled="readonly" class="!w-full"
                ><el-option
                  v-for="level in ['A', 'B', 'C', 'D']"
                  :key="level"
                  :label="level"
                  :value="level" /></el-select></template
          ></el-table-column>
          <el-table-column label="适用割接类型" min-width="240"
            ><template #default="{ row }"
              ><el-select
                v-model="row.cutoverTypeCodes"
                multiple
                collapse-tags
                :disabled="readonly"
                class="!w-full"
                ><el-option
                  v-for="option in getStrDictOptions(DICT_TYPE.PMS_CUTOVER_TYPE)"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value" /></el-select></template
          ></el-table-column>
          <el-table-column label="排序" width="110"
            ><template #default="{ row }"
              ><el-input-number
                v-model="row.sortOrder"
                :disabled="readonly"
                :min="0"
                controls-position="right" /></template
          ></el-table-column>
          <el-table-column label="必填" width="72"
            ><template #default="{ row }"
              ><el-switch v-model="row.required" :disabled="readonly" /></template
          ></el-table-column>
          <el-table-column v-if="!readonly" label="操作" width="80" fixed="right"
            ><template #default="{ $index }"
              ><el-button link type="danger" @click="model.planTemplateSections.splice($index, 1)"
                >删除</el-button
              ></template
            ></el-table-column
          >
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="`发布校验（${validationErrors.length}）`" name="validation">
        <el-empty v-if="validationErrors.length === 0" description="尚无校验错误" />
        <el-alert
          v-for="error in validationErrors"
          :key="`${error.location}-${error.message}`"
          :title="error.location"
          :description="error.message"
          type="error"
          :closable="false"
          show-icon
          class="mb-8px"
        />
      </el-tab-pane>
    </el-tabs>
  </el-form>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { FormInstance } from 'element-plus'
import type {
  CutoverBindingRule,
  CutoverChecklistItem,
  CutoverConfiguration,
  CutoverValidationError
} from '@/api/pms/cutover/cutover-config'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import CutoverRiskMatrixEditor from './CutoverRiskMatrixEditor.vue'
import CutoverSurveyMatrixEditor from './CutoverSurveyMatrixEditor.vue'
import { validationTarget } from './cutoverMatrix'

const props = defineProps<{ readonly: boolean; validationErrors: CutoverValidationError[] }>()
const message = useMessage()
const model = defineModel<CutoverConfiguration>({ required: true })
const formRef = ref<FormInstance>()
const activeTab = ref('basic')
const itemFilter = ref('ALL')
const rules = {
  configurationCode: [{ required: true, message: '请输入配置编码', trigger: 'blur' }],
  configurationName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }]
}
const itemTypes = [
  { label: '业务调研项', value: 'BUSINESS_SURVEY' },
  { label: '风险考察项', value: 'RISK' },
  { label: '双机部署检查项', value: 'DUAL_MACHINE_CHECK' }
] as const
const subtableOptions = ['VSM', 'SILENT_DUAL', 'DRP_DUAL', 'NORMAL_DUAL', 'CLUSTER']
const filteredItems = computed(() =>
  itemFilter.value === 'ALL'
    ? model.value.items
    : model.value.items.filter((item) => item.itemType === itemFilter.value)
)

const addDimension = () =>
  model.value.dimensions.push({
    code: '',
    name: '',
    dataType: 'STRING',
    valueSource: '',
    owner: 'CUT',
    contextPath: '',
    enabled: true
  })
const addItem = () =>
  model.value.items.push({
    stableItemKey: '',
    itemType: 'BUSINESS_SURVEY',
    businessCategoryCode: '',
    itemName: '',
    interfaceFormat: 'INPUT',
    interfaceSchema: {},
    feedbackFormat: 'TEXT',
    required: false,
    workMode: 'MANUAL',
    enabled: true,
    sortOrder: model.value.items.length * 10 + 10
  })
const addRule = () =>
  model.value.bindingRules.push({
    stableRuleKey: '',
    stableItemKey: '',
    dimensionConditions: {},
    priority: 0,
    requiredResult: false,
    enabled: true
  })
const addSection = () =>
  model.value.planTemplateSections.push({
    stableSectionKey: '',
    title: '',
    sortOrder: model.value.planTemplateSections.length * 10 + 10,
    cutoverTypeCodes: [],
    levelCodes: [],
    required: false
  })
const removeItem = (row: CutoverConfiguration['items'][number]) => {
  model.value.items.splice(model.value.items.indexOf(row), 1)
  model.value.bindingRules = model.value.bindingRules.filter(
    (rule) => rule.stableItemKey !== row.stableItemKey
  )
}
const jsonText = (value: Record<string, unknown>) => JSON.stringify(value || {})
const updateConditions = (row: CutoverBindingRule, value: string) => {
  row.dimensionConditions = parseJsonObject(value, '维度条件')
}
const updateItemJson = (
  row: CutoverChecklistItem,
  field: 'interfaceSchema' | 'externalSourceConfig',
  value: string,
  label: string
) => {
  row[field] = parseJsonObject(value, label)
}
const parseJsonObject = (value: string, label: string): Record<string, unknown> => {
  try {
    const parsed = JSON.parse(value || '{}')
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) return parsed
  } catch {
    // The invalid marker is persisted so server-side validation cannot publish stale old content.
  }
  message.error(`${label}必须是合法 JSON 对象`)
  return { __INVALID_JSON__: value }
}
const validate = () => formRef.value?.validate()
const showValidation = (errors: CutoverValidationError[] = props.validationErrors) => {
  activeTab.value = validationTarget(errors)
}
watch(
  () => props.validationErrors,
  (errors) => {
    if (errors.length > 0) showValidation(errors)
  }
)
defineExpose({ validate, showValidation })
</script>

<style scoped>
.config-editor {
  min-height: 420px;
}

.table-actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

@media (width <= 768px) {
  .table-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>

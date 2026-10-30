<template>
  <ContentWrap>
    <el-form inline>
      <el-form-item label="阶段编码">
        <el-input v-model="query.stageCode" placeholder="如 S4" clearable class="!w-140px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="签约方式">
        <el-select v-model="query.signingMethod" placeholder="全部" clearable class="!w-160px">
          <el-option v-for="option in signingOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="建议来源">
        <el-select v-model="query.sourceType" placeholder="全部" clearable class="!w-200px">
          <el-option v-for="(label, type) in sourceLabels" :key="type" :label="label" :value="type" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load" v-hasPermi="['pms:stage-plan-suggestion-rule:query']">查询</el-button>
        <el-button type="primary" plain @click="openSave()" v-hasPermi="['pms:stage-plan-suggestion-rule:save']">
          新增规则
        </el-button>
      </el-form-item>
    </el-form>
    <el-alert type="info" :closable="false" class="mb-8px"
      title="建议最迟完成 = 参照时间 - 偏移（参照时间按来源取计划验收时间/工期要求/参照阶段建议）；签约方式为空的行适用全部签约方式，精确行优先。" />
    <el-table :data="rules" v-loading="loading" empty-text="暂无规则行">
      <el-table-column prop="stageCode" label="阶段编码" width="110" />
      <el-table-column label="签约方式" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.signingMethod" :type="row.signingMethod === 'DIRECT_SIGN' ? 'primary' : 'warning'">
            {{ signingLabel(row.signingMethod) }}
          </el-tag>
          <el-tag v-else type="info">全部签约方式</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="建议来源" min-width="220">
        <template #default="{ row }">{{ sourceLabels[row.sourceType] || row.sourceType }}</template>
      </el-table-column>
      <el-table-column prop="referenceStageCode" label="参照阶段" width="100">
        <template #default="{ row }">{{ row.referenceStageCode || '—' }}</template>
      </el-table-column>
      <el-table-column label="偏移（提前）" width="140">
        <template #default="{ row }">
          {{ offsetText(row.offsetMonths, row.offsetDays) }}
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="260" show-overflow-tooltip />
      <el-table-column label="启用" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'danger'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openSave(row)" v-hasPermi="['pms:stage-plan-suggestion-rule:update']">修改</el-button>
          <el-button link type="danger" @click="handleDelete(row)" v-hasPermi="['pms:stage-plan-suggestion-rule:delete']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <el-dialog v-model="saveVisible" :title="saveForm.id ? '修改建议规则' : '新增建议规则'" width="560px">
    <el-form :model="saveForm" label-width="120px">
      <el-form-item label="阶段编码" required>
        <el-input v-model="saveForm.stageCode" placeholder="参与计划的阶段编码，如 S4" class="!w-300px" />
      </el-form-item>
      <el-form-item label="签约方式">
        <el-select v-model="saveForm.signingMethod" placeholder="全部签约方式" clearable class="!w-300px">
          <el-option v-for="option in signingOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="建议来源" required>
        <el-select v-model="saveForm.sourceType" class="!w-300px">
          <el-option v-for="(label, type) in sourceLabels" :key="type" :label="label" :value="type" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="saveForm.sourceType === 'STAGE_PLAN'" label="参照阶段" required>
        <el-input v-model="saveForm.referenceStageCode" placeholder="须晚于自身阶段，如 S5" class="!w-300px" />
      </el-form-item>
      <el-form-item label="偏移提前月数" required>
        <el-input-number v-model="saveForm.offsetMonths" :min="-60" :max="60" class="!w-300px" />
      </el-form-item>
      <el-form-item label="偏移提前天数" required>
        <el-input-number v-model="saveForm.offsetDays" :min="-365" :max="365" class="!w-300px" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="saveForm.remark" type="textarea" :rows="2" class="!w-300px" />
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="saveForm.enabled" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="saveVisible = false">取消</el-button>
      <el-button type="primary" :loading="acting" @click="handleSave">确定</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
defineOptions({ name: 'PmsStagePlanSuggestionRule' })

import * as RuleApi from '@/api/pms/engineering/stage-plan-suggestion-rule'
import { getDictOptions } from '@/utils/dict'

const message = useMessage()
const loading = ref(false)
const acting = ref(false)
const rules = ref<RuleApi.StageSuggestionRuleVO[]>([])
const query = ref<{ stageCode?: string; signingMethod?: string; sourceType?: string }>({})

const signingOptions = getDictOptions('pms_signing_method')
const signingLabel = (value: string) => signingOptions.find(option => option.value === value)?.label || value
const sourceLabels = {
  PMS_IMPORTED: '带入：计划验收时间（PMS/财务）',
  DURATION_REQUIRE: '锚工期要求：项目结束时间',
  STAGE_PLAN: '参照阶段建议'
}
const offsetText = (months: number, days: number) => {
  const parts: string[] = []
  if (months) parts.push(`${Math.abs(months)}个月`)
  if (days) parts.push(`${Math.abs(days) / 7}周`)
  const value = parts.join('+') || '0'
  return months < 0 || days < 0 ? `提前 ${value}` : `延后 ${value}`
}

const load = async () => {
  loading.value = true
  try {
    const page = await RuleApi.getStageSuggestionRulePage({ pageNo: 1, pageSize: 100, ...query.value })
    rules.value = page.list || []
  } finally {
    loading.value = false
  }
}

const saveVisible = ref(false)
const saveForm = ref<RuleApi.StageSuggestionRuleVO>(emptyForm())
function emptyForm(): RuleApi.StageSuggestionRuleVO {
  return { stageCode: '', signingMethod: null, sourceType: 'DURATION_REQUIRE', referenceStageCode: null, offsetMonths: 0, offsetDays: -14, remark: '', enabled: true }
}
const openSave = (row?: RuleApi.StageSuggestionRuleVO) => {
  saveForm.value = row ? { ...row } : emptyForm()
  saveVisible.value = true
}

const handleSave = async () => {
  acting.value = true
  try {
    if (saveForm.value.id) await RuleApi.updateStageSuggestionRule(saveForm.value)
    else await RuleApi.createStageSuggestionRule(saveForm.value)
    message.success(saveForm.value.id ? '规则已修改' : '规则已新增')
    saveVisible.value = false
    await load()
  } finally {
    acting.value = false
  }
}

const handleDelete = async (row: RuleApi.StageSuggestionRuleVO) => {
  await message.delConfirm()
  await RuleApi.deleteStageSuggestionRule(row.id!)
  message.success('规则已删除')
  await load()
}

onMounted(load)
</script>

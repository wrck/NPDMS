<template>
  <section aria-label="工勘独立支线判断" class="survey-preparation-branches">
    <h4>工前独立支线</h4>
    <div v-for="branch in evaluated" :key="branch.title">
      <span>{{ branch.title }}</span>
      <el-tag :type="branch.state === 'MATCHED' ? 'warning' : 'info'">
        {{ branch.state === 'MATCHED' ? '需独立办理' : branch.state === 'UNKNOWN' ? '相关工勘项尚未填写' : '条件未命中' }}
      </el-tag>
      <template v-if="definition(branch.title)">
        <el-button v-if="branch.state === 'MATCHED' && !readonly && !(kindOf(branch.title) === 'outsource' && survey?.outsourceRequestId)"
          :disabled="loading" v-hasPermi="[definition(branch.title)!.permission + ':create']"
          @click="launch?.(kindOf(branch.title)!)">{{ definition(branch.title)!.button }}</el-button>
        <template v-for="record in applications[kindOf(branch.title)!]" :key="record.id">
          <el-link type="primary" @click="view(kindOf(branch.title)!, record.id)">{{ record.code || record.name }} #{{ record.id }}</el-link>
          <dict-tag :type="DICT_TYPE.PMS_APPROVAL_STATUS" :value="record.status ?? ''" />
        </template>
      </template>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false">
      <el-button @click="load">重新加载申请记录</el-button>
    </el-alert>
    <MaterialExchangeDetailDialog v-if="detailKind === 'exchange'" v-model="detailVisible" :current="detail" />
    <MaterialRequisitionDetailDialog v-if="detailKind === 'material'" v-model="detailVisible" :current="detail" />
    <ExternalProcurementDetailDialog v-if="detailKind === 'procurement'" v-model="detailVisible" :current="detail" />
    <OutsourceRequestDetailDialog v-if="detailKind === 'outsource'" v-model="detailVisible" :current="detail" />
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import type { SiteSurveyVO } from '@/api/pms/engineering/site-survey/entity'
import { extractSurveyValues } from './siteSurveyForm'
import { surveyPreparationBranches, type SurveyPreparationBranch } from './surveyPreparationBranches'
import { branchApplications, loadBranchApplications, type BranchKind } from './surveyBranchApplications'
import { useNativeBusiness } from '@/components/ProjectBusiness/native/nativeBusinessContext'
import type { BusinessId } from '@/api/pms/platform/business'
import { checkPermi } from '@/utils/permission'
import { DICT_TYPE } from '@/utils/dict'
import MaterialExchangeDetailDialog from '@/views/pms/engineering/material-exch/MaterialExchangeDetailDialog.vue'
import MaterialRequisitionDetailDialog from '@/views/pms/engineering/material-req/MaterialRequisitionDetailDialog.vue'
import ExternalProcurementDetailDialog from '@/views/pms/engineering/ext-proc/ExternalProcurementDetailDialog.vue'
import OutsourceRequestDetailDialog from '@/views/pms/engineering/outsource/OutsourceRequestDetailDialog.vue'
const props = defineProps<{ getSurvey?: () => SiteSurveyVO; branches?: SurveyPreparationBranch[]; readonly?: boolean;
  launch?: (kind: string) => void; getApplicationRevision?: () => number }>()
const nativeBusiness = useNativeBusiness()
const getApplication = async (kind: BranchKind, id: BusinessId) => nativeBusiness
  ? (await nativeBusiness.getBridge(kind).read(id)).value
  : branchApplications[kind].get(id as number)
const survey = computed(() => props.getSurvey?.())
const kindOf = (title: string) => (Object.keys(branchApplications) as BranchKind[]).find(key => branchApplications[key].title === title)
const definition = (title: string) => { const kind = kindOf(title); return kind ? branchApplications[kind] : undefined }
const applications = ref<Record<BranchKind, any[]>>({ exchange: [], material: [], procurement: [], outsource: [] })
const loading = ref(false), error = ref('')
const detailVisible = ref(false)
const detailKind = ref<BranchKind>()
const detail = ref<any>({})
let detailSequence = 0
const view = async (kind: BranchKind, id: BusinessId) => {
  const request = ++detailSequence
  detailVisible.value = false
  try {
    const record = await getApplication(kind, id)
    if (request !== detailSequence) return
    detail.value = record
    detailKind.value = kind
    detailVisible.value = true
  } catch { if (request === detailSequence) error.value = '申请详情加载失败，请重试' }
}
let sequence = 0
const load = async () => {
  const current = ++sequence, survey = props.getSurvey?.()
  applications.value = { exchange: [], material: [], procurement: [], outsource: [] }
  error.value = ''
  loading.value = false
  if (!survey?.id || !survey.projectId) return
  loading.value = true
  try {
    const results = await Promise.all((Object.keys(branchApplications) as BranchKind[]).map(async kind =>
      [kind, checkPermi([branchApplications[kind].permission + ':query'])
        ? kind === 'outsource'
          ? survey.outsourceRequestId ? [await getApplication('outsource', survey.outsourceRequestId)] : []
          : await loadBranchApplications(kind, survey.projectId, survey.id!, nativeBusiness
            ? query => nativeBusiness.getBridge(kind).page(query) : undefined)
        : []] as const))
    if (current === sequence) applications.value = Object.fromEntries(results) as Record<BranchKind, any[]>
  } catch { if (current === sequence) error.value = '关联申请记录加载失败，请重试' }
  finally { if (current === sequence) loading.value = false }
}
watch(() => [props.getSurvey?.()?.id, props.getSurvey?.()?.projectId, props.getSurvey?.()?.version, props.getSurvey?.()?.outsourceRequestId, props.getApplicationRevision?.()], load, { immediate: true })
onBeforeUnmount(() => { sequence++; detailSequence++ })
const evaluated = computed(() => {
  const survey = props.getSurvey?.()
  return surveyPreparationBranches([...(props.branches || []),
    { title: branchApplications.outsource.title, when: { outsourceRequired: true } }],
    survey ? { ...extractSurveyValues(survey), outsourceRequired: survey.outsourceRequired } : {})
})
</script>
<style scoped>
.survey-preparation-branches { display: grid; gap: 8px; }
.survey-preparation-branches h4, .survey-preparation-branches p { margin: 0; }
.survey-preparation-branches p { color: var(--el-text-color-secondary); }
.survey-preparation-branches > div { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
</style>

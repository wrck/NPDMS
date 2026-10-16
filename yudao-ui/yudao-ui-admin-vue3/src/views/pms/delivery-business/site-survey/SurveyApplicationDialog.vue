<template>
  <component :is="dialogs[kind]" ref="formRef" :model-value="modelValue" :form="form"
    :source-survey-id="survey?.id || -1" :saving="saving" :error="error"
    @update:model-value="updateVisible" @save="save" />
</template>
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import MaterialRequisitionFormDialog from '@/views/pms/engineering/material-req/MaterialRequisitionFormDialog.vue'
import ExternalProcurementFormDialog from '@/views/pms/engineering/ext-proc/ExternalProcurementFormDialog.vue'
import OutsourceRequestFormDialog from '@/views/pms/engineering/outsource/OutsourceRequestFormDialog.vue'
import * as MaterialApi from '@/api/pms/engineering/material-req'
import { useNativeBusiness, useNativeBusinessApi } from '@/components/ProjectBusiness/native/nativeBusinessContext'
const nativeBusiness = useNativeBusiness()
const { createMaterialRequisition } = useNativeBusinessApi('material', MaterialApi)
import * as ProcurementApi from '@/api/pms/engineering/ext-proc'
const { createExternalProcurement } = useNativeBusinessApi('procurement', ProcurementApi)
import * as OutsourceApi from '@/api/pms/engineering/outsource'
const { createOutsourceRequest } = useNativeBusinessApi('outsource', OutsourceApi)
import type { SiteSurveyVO } from '@/api/pms/engineering/site-survey/entity'
import { useUserStore } from '@/store/modules/user'
import { useMessage } from '@/hooks/web/useMessage'
import { formatDate } from '@/utils/formatTime'
const props = defineProps<{ modelValue: boolean; kind: 'material' | 'procurement' | 'outsource';
  getSurvey: () => SiteSurveyVO; saveSurvey: () => Promise<SiteSurveyVO>; getExecution: () => Promise<any> }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; created: [kind: string] }>()
const dialogs = { material: MaterialRequisitionFormDialog, procurement: ExternalProcurementFormDialog, outsource: OutsourceRequestFormDialog }
const names = { material: '领料', procurement: '外采', outsource: '转包' }
const creates = { material: createMaterialRequisition, procurement: createExternalProcurement, outsource: createOutsourceRequest }
const userStore = useUserStore()
const message = useMessage()
const survey = computed(() => props.getSurvey())
const form = reactive<any>({})
const formRef = ref()
const saving = ref(false)
const error = ref('')
let closing = false
const updateVisible = async (value: boolean) => {
  if (saving.value || closing) return
  closing = true
  try {
    if (!value && nativeBusiness && !await nativeBusiness.requestLeave(props.kind)) return
    emit('update:modelValue', value)
  } finally { closing = false }
}
watch(() => props.modelValue, visible => {
  if (!visible) return
  Object.keys(form).forEach(key => delete form[key])
  const source = props.getSurvey()
  Object.assign(form, { projectId: source.projectId, code: '', name: `工勘${names[props.kind]}：${source.name}`, attachmentFiles: '', remark: source.id ? `来源工勘 #${source.id}` : '',
    ...(props.kind === 'material' ? { requisitionType: 'SPARE', unit: '个', stockStatus: 'IN_STOCK' }
      : props.kind === 'procurement' ? { procurementType: 'GOODS', unit: '个', currency: 'CNY' }
        : { outsourceType: 'LABOR', currency: 'CNY', workContent: source.constructionResource || source.conclusion || '现场工勘转包申请' }),
    applicantUserId: userStore.getUser.id, applyTime: formatDate(new Date()) })
  error.value = ''
}, { immediate: true })
const save = async () => {
  if (saving.value) return
  try { await formRef.value.validate() } catch { return }
  const projectId = form.projectId, kind = props.kind
  saving.value = true
  error.value = ''
  try {
    const source = await props.saveSurvey()
    if (String(source.projectId) !== String(projectId) || String(props.getSurvey().projectId) !== String(projectId))
      throw new Error('工勘所属项目已变化，请重新打开申请')
    const payload = { ...form, projectId, triggerSource: 'SITE_SURVEY', triggerRefId: source.id }
    if (kind === 'outsource') payload.siteSurveyExecution = await props.getExecution()
    await creates[kind]({ ...payload, ...await nativeBusiness?.formInput(kind) })
    message.success(`${names[kind]}申请创建成功`)
    emit('created', kind)
    emit('update:modelValue', false)
  } catch (failure: any) {
    const reason = failure?.response?.data?.msg || failure?.msg || failure?.message || (typeof failure === 'string' ? failure : '')
    error.value = reason && reason !== 'error' ? reason : '申请保存失败，请检查错误提示后重试'
  } finally { saving.value = false }
}
</script>

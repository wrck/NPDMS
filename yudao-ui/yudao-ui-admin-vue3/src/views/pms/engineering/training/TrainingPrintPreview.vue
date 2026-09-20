<template>
  <Dialog
    v-model="visible"
    title="培训记录打印预览"
    width="min(940px, 98vw)"
    :close-on-click-modal="false"
    :show-close="!exporting"
    :close-on-press-escape="!exporting"
  >
    <el-form inline>
      <el-form-item label="下载版式">
        <el-select
          v-model="selected"
          class="!w-340px"
          :disabled="exporting || loading"
          @change="selectLayout"
        >
          <el-option label="确认样张（标准表格）" value="approved" />
          <el-option v-if="boundSnapshot" label="记录绑定版式（历史快照）" value="bound" />
          <el-option
            v-for="item in templates"
            :key="item.templateId"
            :label="item.templateName"
            :value="String(item.templateId)"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <div v-loading="loading" class="print-scroll">
      <div
        v-if="decoded"
        ref="paper"
        class="training-print-paper"
        :class="{ 'approved-table': decoded.option.trainingPrintStyle === 'APPROVED_TABLE' }"
      >
        <form-create
          v-model="values"
          :rule="decoded.rule"
          :option="decoded.option"
          :disabled="true"
        />
      </div>
    </div>
    <template #footer>
      <el-button :disabled="exporting" @click="visible = false">关闭</el-button>
      <el-button
        type="primary"
        :loading="exporting"
        :disabled="!decoded || loading"
        @click="exportPdf"
        >下载 PDF</el-button
      >
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import dayjs from 'dayjs'
import { getTraining } from '@/api/pms/engineering/training'
import type { TrainingVO } from '@/api/pms/engineering/training'
import { decodeDynamicForm } from '@/views/pms/platform/dynamic-form/components/dynamicFormCodec'
import { registerDynamicFormComponents } from '@/views/pms/platform/dynamic-form/components/registerDynamicFormComponents'
import {
  trainingPrintValues,
  createTrainingPrintForm,
  PRINT_CATEGORY,
  APPROVED_PRINT_CODE,
  PRINT_ENGINE,
  type PrintSnapshot
} from './trainingPrintForm'
import * as DynamicFormApi from '@/api/pms/platform/dynamic-form'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import { checkPermi } from '@/utils/permission'

registerDynamicFormComponents()
const visible = ref(false),
  exporting = ref(false)
const paper = ref<HTMLElement>()
const decoded = shallowRef<ReturnType<typeof decodeDynamicForm>>()
const values = ref<Record<string, any>>({})
const filename = ref('培训记录.pdf')
const message = useMessage()
const loading = ref(false)
const selected = ref('approved')
const templates = ref<DynamicFormApi.DynamicFormSelectionVO[]>([])
const boundSnapshot = shallowRef<PrintSnapshot>()
let recordData: TrainingVO = {},
  projectLabel = '',
  openRequest = 0
const applyLayout = (snapshot: PrintSnapshot) => {
  const form = decodeDynamicForm(snapshot.formConfJson, snapshot.formRulesJson)
  form.option = {
    ...form.option,
    submitBtn: false,
    resetBtn: false,
    form: { ...(form.option.form as object), disabled: true }
  }
  decoded.value = form
  values.value = trainingPrintValues(recordData, projectLabel)
  if (snapshot.formConfJson.trainingPrintStyle === 'APPROVED_TABLE') {
    values.value.signTime = recordData.signTime
      ? dayjs(recordData.signTime).format('YYYY年MM月DD日')
      : ''
  }
}
const standardLayout = (): PrintSnapshot => ({
  engine: PRINT_ENGINE,
  ...createTrainingPrintForm(
    getStrDictOptions(DICT_TYPE.PMS_TRAINING_TYPE).map((item) => ({
      label: item.label,
      value: item.value
    }))
  )
})
const selectLayout = async () => {
  loading.value = true
  try {
    if (selected.value === 'approved') applyLayout(standardLayout())
    else if (selected.value === 'bound' && boundSnapshot.value) applyLayout(boundSnapshot.value)
    else {
      const template = templates.value.find((item) => String(item.templateId) === selected.value)
      if (!template?.currentPublishedRevisionId) throw new Error('Published revision missing')
      const revision = await DynamicFormApi.getRevision(template.currentPublishedRevisionId)
      applyLayout({
        engine: PRINT_ENGINE,
        formConfJson: revision.formConfJson,
        formRulesJson: revision.formRulesJson
      })
    }
  } catch {
    decoded.value = undefined
    message.error('打印模板加载失败，请重新选择')
  } finally {
    loading.value = false
  }
}
const open = async (record: TrainingVO, projectName: string, snapshot?: PrintSnapshot) => {
  const request = ++openRequest
  recordData = record
  projectLabel = projectName
  boundSnapshot.value = snapshot?.engine === PRINT_ENGINE ? snapshot : undefined
  const approvedBound = boundSnapshot.value?.formConfJson.trainingPrintStyle === 'APPROVED_TABLE'
  selected.value = approvedBound ? 'bound' : 'approved'
  templates.value = []
  filename.value = `${record.code || '培训记录'}.pdf`
  applyLayout(approvedBound ? boundSnapshot.value! : standardLayout())
  visible.value = true
  if (!checkPermi(['pms:dynamic-form-template:query'])) return
  loading.value = true
  try {
    const all: DynamicFormApi.DynamicFormSelectionVO[] = []
    for (let pageNo = 1; ; pageNo++) {
      const page = await DynamicFormApi.getTemplateSelection({ pageNo, pageSize: 100 })
      all.push(...page.list)
      if (all.length >= page.total || !page.list.length) break
    }
    if (request !== openRequest) return
    templates.value = all.filter((item) => item.categoryCode === PRINT_CATEGORY)
    const approved = templates.value.find((item) => item.templateCode === APPROVED_PRINT_CODE)
    if (approved && !approvedBound) {
      selected.value = String(approved.templateId)
      await selectLayout()
    }
  } catch {
    message.warning('已发布模板暂不可用，当前使用确认样张标准版式')
  } finally {
    if (request === openRequest) loading.value = false
  }
}
const exportPdf = async () => {
  if (exporting.value || loading.value || !paper.value) return
  exporting.value = true
  try {
    const current = await getTraining(recordData.id!)
    if (current.status === 3) {
      visible.value = false
      message.warning('已作废的培训记录不允许下载 PDF')
      return
    }
    await nextTick()
    await document.fonts.ready
    await Promise.all(Array.from(paper.value.querySelectorAll('img')).map((img) => img.decode()))
    const { default: html2pdf } = await import('html2pdf.js')
    const worker = html2pdf()
    const pdfFormat = { unit: 'mm', format: 'a4', orientation: 'portrait' as const, compress: true }
    const options: Parameters<typeof worker.set>[0] & {
      pagebreak: { mode: string[]; avoid: string[] }
    } = {
      filename: filename.value,
      margin: [10, 10, 10, 10],
      image: { type: 'png', quality: 1 },
      html2canvas: {
        scale: 2,
        backgroundColor: '#ffffff',
        useCORS: true,
        onclone: (document: Document) => {
          // Canvas rendering of native textareas loses line breaks; use equivalent text in the export clone.
          document
            .querySelectorAll<HTMLImageElement>('.training-print-paper img')
            .forEach((img) => {
              if (!img.naturalWidth || !img.naturalHeight) return
              const box = img.getBoundingClientRect()
              const scale = Math.min(box.width / img.naturalWidth, box.height / img.naturalHeight)
              img.style.width = `${img.naturalWidth * scale}px`
              img.style.height = `${img.naturalHeight * scale}px`
            })
          document
            .querySelectorAll<HTMLTextAreaElement>('.training-print-paper textarea')
            .forEach((textarea) => {
              const text = document.createElement('div')
              text.className = textarea.className
              text.textContent = textarea.value
              text.style.cssText = textarea.style.cssText
              Object.assign(text.style, {
                whiteSpace: 'pre-wrap',
                textAlign: 'left',
                overflowWrap: 'anywhere',
                height: 'auto',
                minHeight: `${textarea.getBoundingClientRect().height}px`,
                overflow: 'visible'
              })
              textarea.replaceWith(text)
            })
        }
      },
      jsPDF: pdfFormat,
      pagebreak: { mode: ['css', 'legacy'], avoid: ['.el-form-item', 'img', 'h2'] },
      enableLinks: false
    }
    await worker.set(options).from(paper.value).save()
  } catch {
    message.error('PDF 生成失败，请重试')
  } finally {
    exporting.value = false
  }
}
defineExpose({ open })
</script>

<style scoped>
.print-scroll {
  overflow: auto;
  background: #f1f3f5;
  padding: 20px;
}
.training-print-paper {
  box-sizing: border-box;
  width: 718px;
  padding: 20px;
  background: white;
  color: #111;
}
.training-print-paper :deep(.el-form-item) {
  break-inside: avoid;
  margin-bottom: 14px;
}
.training-print-paper :deep(.el-input__inner),
.training-print-paper :deep(.el-textarea__inner) {
  color: #111;
  -webkit-text-fill-color: #111;
}
.training-print-paper :deep(.el-input__wrapper),
.training-print-paper :deep(.el-textarea__inner) {
  background: white;
  box-shadow: none !important;
  border: 1px solid #cbd1da;
}
.training-print-paper :deep(.el-radio__input.is-checked .el-radio__inner) {
  background: #333;
  border-color: #333;
}
.training-print-paper :deep(.el-radio__input.is-checked .el-radio__inner::after) {
  background: white;
}
.training-print-paper :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background: #333;
  border-color: #333;
}
.training-print-paper :deep(.el-checkbox__input.is-checked .el-checkbox__inner::after) {
  border-color: white;
}
.training-print-paper :deep(.el-radio__label),
.training-print-paper :deep(.el-checkbox__label),
.training-print-paper :deep(.el-form-item__label) {
  color: #111 !important;
}
.training-print-paper :deep(.el-textarea__inner) {
  resize: none;
}
.training-print-paper :deep(img) {
  max-width: 100%;
  object-fit: contain;
}
.training-print-paper :deep(._fc-signature-btn) {
  visibility: hidden;
}
.approved-table {
  padding: 24px;
  font-family: Arial, 'Microsoft YaHei', sans-serif;
  font-size: 12px;
}
.approved-table :deep(.el-form-item) {
  margin: 0;
  padding: 0;
}
.approved-table :deep(.el-form-item__label) {
  font-size: 11px;
  line-height: 18px;
  margin-bottom: 4px;
  color: #64748b !important;
}
.approved-table :deep(._fc-table td) {
  padding: 12px;
  vertical-align: top;
}
.approved-table :deep(._fc-table table) {
  table-layout: fixed;
}
.approved-table :deep(.el-input__wrapper),
.approved-table :deep(.el-textarea__inner) {
  border: none;
  padding: 0;
  min-height: 20px;
}
.approved-table :deep(.el-input__inner) {
  font-size: 12px;
  height: 22px;
}
.approved-table :deep(.el-textarea__inner) {
  font-size: 12px;
  line-height: 1.7;
}
.approved-table :deep(.el-radio),
.approved-table :deep(.el-checkbox) {
  margin-right: 10px;
  height: 22px;
}
.approved-table :deep(.el-radio__label),
.approved-table :deep(.el-checkbox__label) {
  padding-left: 4px;
  font-size: 11px;
}
.approved-table :deep(.el-radio__inner),
.approved-table :deep(.el-checkbox__inner) {
  width: 10px;
  height: 10px;
  border-radius: 0;
}
.approved-table :deep(._fc-title) { height: auto; }
.approved-table :deep(._fc-title.h2) {
  font-size: 26px;
  font-weight: 500;
  margin: 12px 0 16px;
  line-height: 1.4;
  padding-bottom: 18px;
  border-bottom: 1px solid #245b8f;
}
.approved-table :deep(._fc-title.h5) {
  font-size: 12px;
  font-weight: normal;
  margin: 0;
  line-height: 1.6;
}
.approved-table :deep(._fc-title.h5.training-print-section) {
  color: #245b8f;
  border-left: 3px solid #245b8f;
  padding-left: 10px;
  margin: 24px 0 16px;
}
.approved-table :deep(._fc-signature) {
  border: none;
}
.approved-table :deep(._fc-signature-preview) {
  height: 48px;
}
.approved-table :deep(._fc-signature-preview img) {
  height: 48px;
  width: 100%;
}
.approved-table :deep(._fc-signature-preview) {
  border: none;
}
.approved-table :deep(.el-radio__input.is-checked .el-radio__inner),
.approved-table :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background: white;
  border-color: #333;
}
.approved-table :deep(.el-checkbox__input.is-checked .el-checkbox__inner::after) {
  border-color: #333;
}
.approved-table :deep(.el-radio__input.is-checked .el-radio__inner::after) {
  background: transparent;
  width: 3px;
  height: 5px;
  border: 1px solid #333;
  border-left: 0;
  border-top: 0;
  border-radius: 0;
  transform: translate(-50%, -65%) rotate(45deg);
}
</style>

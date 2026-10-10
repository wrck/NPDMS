<template>
  <Dialog
    v-model="visible"
    :title="`${documentLabel(props.kind)}打印预览`"
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
          <el-option label="Word V2.0 样张" value="approved" />
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
    <el-alert
      title="正式 PDF 下载前须保存并登记交付件；只读用户可查看当前预览，已登记文件请从记录详情的交付件列表下载。"
      type="info" :closable="false" class="mb-8px" />
    <div v-loading="loading" class="print-scroll">
      <div
        v-if="decoded"
        ref="paper"
        class="confirmation-print-paper"
        :class="{ 'confirmation-standard-print': standardPrint, 'confirmation-survey-print': standardPrint && props.kind === 'SATISFACTION', 'confirmation-word-print': wordPrint }"
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
        v-if="canRegister" type="primary" :loading="exporting"
        :disabled="!decoded || loading" @click="exportPdf">下载 PDF（自动登记交付件）</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">


import { generateUUID } from '@/utils'
import { getConfirmation, saveConfirmationPdf, type CompletionConfirmationDetail } from '@/api/pms/acceptance/completion-certificate'

import { decodeDynamicForm } from '@/views/pms/platform/dynamic-form/components/dynamicFormCodec'
import { registerDynamicFormComponents } from '@/views/pms/platform/dynamic-form/components/registerDynamicFormComponents'
import { printCategory, documentLabel, createConfirmationPrintForm, printValues, registerConfirmationPrintComponents, defaultPrintTemplateCode, type ConfirmationDocumentKind } from './confirmationPrintForm'
import { prepareWordPrintExport, addWordPrintPageDecorations, repeatWordPrintWatermarks } from './wordPrintPdf'
interface PrintSnapshot { engine: string; formConfJson: DynamicFormApi.JsonObject; formRulesJson: DynamicFormApi.JsonObject[] }
const props = defineProps<{ kind: ConfirmationDocumentKind; readonly?: boolean }>()
const PRINT_ENGINE = 'FORM_CREATE_ELEMENT_PLUS'
const PRINT_CATEGORY = computed(() => printCategory(props.kind))
const APPROVED_PRINT_CODE = computed(() => defaultPrintTemplateCode(props.kind))
import * as DynamicFormApi from '@/api/pms/platform/dynamic-form'

import { checkPermi } from '@/utils/permission'

registerDynamicFormComponents()
registerConfirmationPrintComponents()
const visible = ref(false),
  exporting = ref(false)
const paper = ref<HTMLElement>()
const decoded = shallowRef<ReturnType<typeof decodeDynamicForm>>()
const wordPrint = computed(() => decoded.value?.rule.some(rule => ['CompletionCertificatePrintV2', 'CompletionSatisfactionPrintV2'].includes(String(rule.type))))
const values = ref<Record<string, any>>({})
const filename = ref('确认表.pdf')
const message = useMessage()
const deliveryAttempt = shallowRef<{ file: File; key: string }>()
const canRegister = computed(() => !props.readonly && checkPermi(['pms:file:upload']) && checkPermi(['pms:acc-completion-certificate:update']))
const loading = ref(false)
const selected = ref('approved')
const templates = ref<DynamicFormApi.DynamicFormSelectionVO[]>([])
const standardPrint = computed(() => selected.value === 'approved' || templates.value.some(item =>
  item.templateCode === `${props.kind}_PRINT_STANDARD` && (String(item.templateId) === selected.value
    || selected.value === 'bound' && item.templateId === recordData?.certificate[props.kind === 'SATISFACTION' ? 'satisfactionPrintTemplateId' : 'completionPrintTemplateId'])))
const boundSnapshot = shallowRef<PrintSnapshot>()
let recordData: CompletionConfirmationDetail,
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
  values.value = printValues(recordData)

}
const standardLayout = (): PrintSnapshot => ({
  engine: PRINT_ENGINE,
  ...createConfirmationPrintForm(props.kind)
})
const selectLayout = async () => {
  deliveryAttempt.value = undefined
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
const open = async (record: CompletionConfirmationDetail, snapshot?: PrintSnapshot) => {
  const request = ++openRequest
  recordData = record
  deliveryAttempt.value = undefined
  boundSnapshot.value = snapshot?.engine === PRINT_ENGINE ? snapshot : undefined
  const approvedBound = !!boundSnapshot.value
  selected.value = approvedBound ? 'bound' : 'approved'
  templates.value = []
  filename.value = `${record.certificate.code || documentLabel(props.kind)}-${documentLabel(props.kind)}.pdf`
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
    templates.value = all.filter((item) => item.categoryCode === PRINT_CATEGORY.value)
    const approved = templates.value.find((item) => item.templateCode === APPROVED_PRINT_CODE.value)
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
const downloadPdf = (file: File) => {
  const url = URL.createObjectURL(file)
  const link = document.createElement('a')
  link.href = url
  link.download = file.name
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
const exportPdf = async () => {
  if (!canRegister.value) return
  if (exporting.value || loading.value || !paper.value) return
  exporting.value = true
  try {
    const currentDetail = await getConfirmation(recordData.certificate.id!)
    const current = currentDetail.certificate
    if (current.status === 4) {
      visible.value = false
      message.warning('已作废的确认记录不允许下载 PDF')
      return
    }
    if (current.id !== recordData.certificate.id || current.projectId !== recordData.certificate.projectId
        || current.version !== recordData.certificate.version) {
      throw new Error('确认记录已变化，请关闭预览后重新打开')
    }
    if (deliveryAttempt.value) {
      await saveConfirmationPdf(current.id!, current.version!, props.kind, deliveryAttempt.value.file, deliveryAttempt.value.key)
      downloadPdf(deliveryAttempt.value.file)
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
      margin: wordPrint.value ? [25, 23, 25, 18] : [10, 10, 10, 10],
      image: { type: 'png', quality: 1 },
      html2canvas: {
        scale: 2,
        backgroundColor: '#ffffff',
        useCORS: true,
        onclone: (document: Document) => {
          repeatWordPrintWatermarks(document)
          // Paint stripes on individual cells: whole-row backgrounds obscure rowspan category text in html2canvas.
          document.querySelectorAll<HTMLTableRowElement>('.confirmation-print-paper .survey-table tbody tr')
            .forEach((row, index) => {
              row.style.background = 'transparent'
              Array.from(row.children).forEach(cell => {
                (cell as HTMLElement).style.background = cell.classList.contains('category-cell') || index % 2 === 0 ? '#ffffff' : '#f7f8fa'
              })
            })
          // Canvas rendering of native textareas loses line breaks; use equivalent text in the export clone.
          document
            .querySelectorAll<HTMLImageElement>('.confirmation-print-paper img')
            .forEach((img) => {
              if (!img.naturalWidth || !img.naturalHeight) return
              const box = img.getBoundingClientRect()
              const scale = Math.min(box.width / img.naturalWidth, box.height / img.naturalHeight)
              img.style.width = `${img.naturalWidth * scale}px`
              img.style.height = `${img.naturalHeight * scale}px`
            })
          document
            .querySelectorAll<HTMLTextAreaElement>('.confirmation-print-paper textarea')
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
      pagebreak: { mode: ['css', 'legacy'], avoid: ['.el-form-item:not(:has(.survey-table)):not(:has(.word-print-sheet))', '.survey-table tr', '.word-print-table tr:not(:has(table))', '.word-device-table tr', 'img', 'h2'] },
      enableLinks: false
    }
    const exportCopy = wordPrint.value ? prepareWordPrintExport(paper.value) : undefined
    let blob: Blob
    try {
      const pdf = await worker.set(options).from(exportCopy?.element || paper.value).toPdf().get('pdf')
      if (wordPrint.value) addWordPrintPageDecorations(pdf, props.kind, paper.value.querySelector<HTMLImageElement>('.word-print-header img')!)
      blob = pdf.output('blob')
    } finally {
      exportCopy?.remove()
    }
    if (!canRegister.value) throw new Error('无生成交付件写权限')
    deliveryAttempt.value = { file: new File([blob], filename.value, { type: 'application/pdf' }), key: generateUUID() }
    await saveConfirmationPdf(current.id!, current.version!, props.kind, deliveryAttempt.value.file, deliveryAttempt.value.key)
    downloadPdf(deliveryAttempt.value.file)
  } catch (failure: any) {
    message.error(failure?.response?.data?.msg || failure?.message || 'PDF 生成或交付件登记失败，请重试')
  } finally {
    exporting.value = false
  }
}
const close = () => { if (exporting.value) return false; openRequest++; visible.value = false; return true }
defineExpose({ open, close, isExporting: () => exporting.value })
</script>

<style scoped>
.print-scroll {
  overflow: auto;
  background: #f1f3f5;
  padding: 20px;
}
.confirmation-print-paper {
  box-sizing: border-box;
  width: 718px;
  padding: 20px;
  background: white;
  color: #111;
}
.confirmation-print-paper :deep(.el-form-item) {
  break-inside: avoid;
  margin-bottom: 14px;
}
.confirmation-print-paper :deep(.el-input__inner),
.confirmation-print-paper :deep(.el-textarea__inner) {
  color: #111;
  -webkit-text-fill-color: #111;
}
.confirmation-print-paper :deep(.el-input__wrapper),
.confirmation-print-paper :deep(.el-textarea__inner) {
  background: white;
  box-shadow: none !important;
  border: 1px solid #cbd1da;
}
.confirmation-print-paper :deep(.el-radio__input.is-checked .el-radio__inner) {
  background: #333;
  border-color: #333;
}
.confirmation-print-paper :deep(.el-radio__input.is-checked .el-radio__inner::after) {
  background: white;
}
.confirmation-print-paper :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background: #333;
  border-color: #333;
}
.confirmation-print-paper :deep(.el-checkbox__input.is-checked .el-checkbox__inner::after) {
  border-color: white;
}
.confirmation-print-paper :deep(.el-radio__label),
.confirmation-print-paper :deep(.el-checkbox__label),
.confirmation-print-paper :deep(.el-form-item__label) {
  color: #111 !important;
}
.confirmation-print-paper :deep(.el-textarea__inner) {
  resize: none;
}
.confirmation-print-paper :deep(img) {
  max-width: 100%;
  object-fit: contain;
}
.confirmation-print-paper :deep(._fc-signature-btn) {
  visibility: hidden;
}
.confirmation-standard-print :deep(.el-form-item) { margin-bottom: 5px; }
.confirmation-standard-print :deep(.el-form-item__label) { font-size: 12px; line-height: 20px; height: auto; margin-bottom: 3px; }
.confirmation-standard-print :deep(.el-input__inner),
.confirmation-standard-print :deep(.el-textarea__inner) { font-size: 12px; }
.confirmation-survey-print :deep(table .el-form-item) { display: flex; flex-direction: row; align-items: flex-start; }
.confirmation-survey-print :deep(table .el-form-item__label) { width: 100px; flex-shrink: 0; margin: 4px 8px 0 0; }
.confirmation-survey-print :deep(table .el-form-item__content) { flex: 1; min-width: 0; }
.confirmation-word-print { width: 794px; padding: 0; }
.confirmation-word-print :deep(.el-form-item) { margin: 0; break-inside: auto; }
.confirmation-word-print :deep(.el-form-item__content) { display: block; width: 100%; }
</style>

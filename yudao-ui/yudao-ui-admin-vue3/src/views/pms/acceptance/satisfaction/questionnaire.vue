<template>
  <main ref="publicPage" class="public-page">
    <article class="questionnaire-shell" aria-labelledby="questionnaire-title">
      <header class="packet-header">
        <div class="packet-brand">
          <img :src="logo" alt="DPtech 迪普科技" />
          <span v-if="questionnaire" class="packet-status" :class="{ submitted: questionnaire.submitted || outcome }">{{ questionnaire.submitted || outcome ? '已提交' : '客户确认' }}</span>
        </div>
        <h1 id="questionnaire-title">{{ outcome ? '感谢您的反馈' : questionnaire?.completionCertificate ? '完工确认与满意度调查' : '项目满意度调查' }}</h1>
        <p v-if="questionnaire?.completionCertificate" class="packet-project">{{ questionnaire.completionCertificate.name }}</p>
        <p class="packet-intro">{{ questionnaire?.completionCertificate ? '请核对完工内容并评价本次服务，最后一次签字确认两份表单。' : '请评价本次项目交付服务，填写后签字提交。' }}</p>
        <p v-if="questionnaire" class="packet-expiry">链接有效至 {{ formatDate(questionnaire.expiresAt) }}。提交后内容不可修改。</p>
      </header>
      <el-skeleton v-if="loading" :rows="7" animated aria-label="正在加载确认表单" />
      <el-result v-else-if="errorMessage" icon="warning" title="问卷暂不可用" :sub-title="errorMessage" />
      <el-result v-else-if="outcome" :icon="outcome.passed ? 'success' : 'warning'" :title="questionnaire?.completionCertificate ? '两份表单已统一提交' : '提交成功'" :sub-title="outcome.passed ? '感谢您的评价，本次服务满意度已达标。' : '您的评价已保存，项目团队将跟进整改。'">
        <template #extra><p class="score-summary">满意度评价 {{ outcome.score }} 分</p></template>
      </el-result>
      <div v-else-if="questionnaire" class="questionnaire-form">
        <nav class="packet-navigation" aria-label="本页内容">
          <button v-if="questionnaire.completionCertificate" type="button" @click="scrollToSection(completionSection)">完工内容</button>
          <button type="button" @click="scrollToSection(surveySection)">服务评价</button>
          <button type="button" @click="scrollToSection(signatureSection)">签字确认</button>
        </nav>
        <section v-if="questionnaire.completionCertificate" id="completion-section" ref="completionSection" class="packet-section completion-section" aria-labelledby="completion-title">
          <div class="section-heading"><span class="section-number" aria-hidden="true">1</span><div><h2 id="completion-title">完工证明</h2><p>请核对项目、服务内容及设备明细。</p></div></div>
          <CompletionCertificateSheet :certificate="questionnaire.completionCertificate" :show-title="false" :show-hint="false" />
        </section>
        <section id="survey-section" ref="surveySection" class="packet-section survey-section" aria-labelledby="survey-title">
          <div class="section-heading"><span class="section-number" aria-hidden="true">{{ questionnaire.completionCertificate ? '2' : '1' }}</span><div><h2 id="survey-title">满意度调查表</h2><p>请按实际体验评价，每一项选择一个选项。</p></div></div>
          <QuestionnaireTable ref="questionFormApi" :definition="definition" :answers="answers" :disabled="submissionLocked" :show-signature="false" />
        </section>
        <section id="signature-section" ref="signatureSection" class="packet-section file-section" aria-labelledby="signature-title">
          <div class="section-heading"><span class="section-number" aria-hidden="true">{{ questionnaire.completionCertificate ? '3' : '2' }}</span><div><h2 id="signature-title">{{ questionnaire.completionCertificate ? '统一签字确认' : '客户签字确认' }}</h2><p>{{ questionnaire.completionCertificate ? '仅需签字一次，同时确认以上完工证明与满意度调查表。' : '请由客户本人核对并签字。' }}</p></div></div>
          <div class="customer-confirmation-form"><form-create v-model="signatureValues" :rule="signatureRules" :option="questionFormOption" /></div>
          <div v-if="!questionnaire.submitted" class="attachment-field"><p>补充附件（可选）</p><el-upload multiple :auto-upload="false" :disabled="submissionLocked" :on-change="onAttachmentChange" :on-remove="onAttachmentRemove"><el-button>选择附件</el-button></el-upload></div>
          <el-alert v-if="questionnaire.submitted" type="success" :closable="false" :title="`两份表单已统一提交 · ${formatDate(questionnaire.signedAt)}`" />
          <el-checkbox v-else-if="questionnaire.completionCertificate" v-model="confirmCompletionCertificate" :disabled="submissionLocked" class="joint-confirmation">我已核对完工证明及满意度调查表，以上签字同时确认两份表单。</el-checkbox>
        </section>
        <footer v-if="!questionnaire.submitted" class="submission-actions">
          <p>请由客户本人填写并签字。提交后内容不可修改。</p>
          <el-button type="primary" size="large" class="submit-button" :loading="submitting" @click="submit">{{ questionnaire.completionCertificate ? '签字并提交两份表单' : '签字并提交评价' }}</el-button>
        </footer>
      </div>
    </article>
  </main>
</template>

<script setup lang="ts">
import type { UploadFile } from 'element-plus'
import QuestionnaireTable from './QuestionnaireTable.vue'
import CompletionCertificateSheet from './CompletionCertificateSheet.vue'
import logo from '../completion-certificate/dptech-print-v2.png'
import { useRoute } from 'vue-router'
import { generateUUID } from '@/utils'
import {
  customerFormOption,
  signaturePngFile
} from '@/components/FormCreate/src/customerConfirmation'
import * as Api from '@/api/pms/acceptance/satisfaction'
import { formatDate } from '@/utils/formatTime'
import type {
  GrantFileFact,
  PublicQuestionnaire,
  QuestionnaireDefinition,
  SubmissionOutcome
} from '@/api/pms/acceptance/satisfaction'

defineOptions({ name: 'PmsSatisfactionQuestionnairePublic' })
const route = useRoute()
const message = useMessage()
const loading = ref(true)
const submitting = ref(false)
const preparedSubmission = ref<Record<string, unknown>>()
const submissionLocked = computed(() => submitting.value || !!preparedSubmission.value || !!questionnaire.value?.submitted)
const errorMessage = ref('')
const questionnaire = ref<PublicQuestionnaire>()
const publicPage = ref<HTMLElement>()
const completionSection = ref<HTMLElement>()
const surveySection = ref<HTMLElement>()
const signatureSection = ref<HTMLElement>()
const scrollToSection = async (element?: HTMLElement) => {
  await nextTick()
  const container = publicPage.value
  if (!container || !element) return
  container.scrollTo({
    top: container.scrollTop + element.getBoundingClientRect().top - container.getBoundingClientRect().top - 20
  })
}
const confirmCompletionCertificate = ref(false)
const definition = ref<QuestionnaireDefinition>({ schemaVersion: 1, questions: [] })
const answers = ref<Record<string, string | string[]>>({})
const questionFormApi = ref<InstanceType<typeof QuestionnaireTable>>()
const questionFormOption = computed(() => ({
  ...customerFormOption,
  form: { ...customerFormOption.form, disabled: submissionLocked.value }
}))
const signatureValues = ref({ signatureImageDataUrl: '' })
const signatureRules = computed(() => [
  {
    type: 'signaturePad',
    field: 'signatureImageDataUrl',
    title: '客户手写签字',
    props: { mobilePortrait: true, disabled: submissionLocked.value },
    validate: [{ required: true, message: '请手写签字' }]
  }
])
const attachmentFiles = ref<File[]>([])
const outcome = ref<SubmissionOutcome>()
const requestId = generateUUID()
const uploadOperationIds = new Map<string, string>()
const token = String(route.params.token || '')
const tenantId = String(route.query.tenantId || '')
const load = async () => {
  if (!token || !/^\d+$/.test(tenantId)) {
    errorMessage.value = '受控链接缺少有效租户信息。'
    loading.value = false
    return
  }
  try {
    questionnaire.value = await Api.inspectPublicQuestionnaire(token, tenantId)
    definition.value = JSON.parse(questionnaire.value.frozenQuestions)
    definition.value.questions.forEach((question) => {
      answers.value[question.code] = question.type === 'MULTIPLE_CHOICE' ? [] : ''
    })
    if (questionnaire.value.submitted) {
      for (const answer of JSON.parse(questionnaire.value.answerSnapshot || '{"answers":[]}').answers) answers.value[answer.questionCode] = answer.value
      signatureValues.value.signatureImageDataUrl = questionnaire.value.signatureImageDataUrl || ''
      confirmCompletionCertificate.value = true
    }
  } catch {
    errorMessage.value = '链接已过期、已失效或无权访问。'
  } finally {
    loading.value = false
  }
}
const onAttachmentChange = (file: UploadFile) => {
  if (file.raw && !attachmentFiles.value.includes(file.raw)) attachmentFiles.value.push(file.raw)
}
const onAttachmentRemove = (file: UploadFile) => {
  attachmentFiles.value = attachmentFiles.value.filter((item) => item !== file.raw)
}
const upload = async (
  file: File,
  policyKey: string,
  ordinal: number
): Promise<{ fact: GrantFileFact; responseId: number }> => {
  const slot = `${policyKey}:${ordinal}`
  let operationId = uploadOperationIds.get(slot)
  if (!operationId) {
    operationId = generateUUID().replace(/-/g, '')
    uploadOperationIds.set(slot, operationId)
  }
  const initialized = await Api.initializeGrantFile(token, tenantId, {
    requestId,
    policyKey,
    operationId,
    fileName: file.name,
    categoryCode: policyKey,
    declaredSizeBytes: file.size,
    declaredMediaType: file.type || 'application/octet-stream'
  })
  const fact = await Api.completeGrantFile(
    token,
    tenantId,
    initialized.sessionId,
    {
      requestId,
      responseId: initialized.responseId,
      policyKey,
      operationId,
      fileSlotKey: initialized.fileSlotKey,
      fileSequence: initialized.fileSequence,
      artifactId: initialized.artifactId
    },
    file
  )
  return { fact, responseId: initialized.responseId }
}
const submit = async () => {
  if (submitting.value || outcome.value || questionnaire.value?.submitted || !questionFormApi.value) return
  if (preparedSubmission.value) {
    submitting.value = true
    try { outcome.value = await Api.submitPublicResponse(token, tenantId, preparedSubmission.value) }
    finally { submitting.value = false }
    return
  }
  if (questionnaire.value?.completionCertificate && !confirmCompletionCertificate.value) {
    message.warning('请确认本次完工证明及满意度调查表')
    return
  }
  try { await questionFormApi.value.validate() }
catch (error) { message.warning(error instanceof Error ? error.message : '请完成必填问卷内容'); await scrollToSection(surveySection.value); return }
  if (!signatureValues.value.signatureImageDataUrl) {
    message.warning('请手写签字')
    return
  }
  for (const question of definition.value.questions) {
    const answer = answers.value[question.code]
    const length = typeof answer === 'string' ? answer.trim().length : answer?.length || 0
    if (question.required && !length) {
      message.warning(`请填写${question.title}`)
      return
    }
    if (question.type === 'TEXT' && length &&
      (length < (question.minLength ?? 0) || length > (question.maxLength ?? Infinity))) {
      message.warning(`请按要求填写${question.title}的字数`)
      return
    }
    if (
      Array.isArray(answer) &&
      answer.length &&
      (answer.length < (question.minSelections ?? 0) ||
        answer.length > (question.maxSelections ?? Infinity))
    ) {
      message.warning(`请按要求选择${question.title}`)
      return
    }
  }
  submitting.value = true
  try {
    const signature = await upload(
      signaturePngFile(signatureValues.value.signatureImageDataUrl),
      'SATISFACTION_SIGNATURE',
      1
    )
    const facts: GrantFileFact[] = [signature.fact]
    for (let index = 0; index < attachmentFiles.value.length; index++) {
      facts.push(
        (await upload(attachmentFiles.value[index], 'SATISFACTION_ATTACHMENT', index + 1)).fact
      )
    }
    const responseId = signature.responseId
    const files = facts.map((fact) => ({
      role: fact.policyKey === 'SATISFACTION_SIGNATURE' ? 'SIGNATURE' : 'ATTACHMENT',
      fileSlotKey: fact.fileSlotKey,
      sequence: fact.fileSequence,
      artifactId: fact.fileFact.artifactId,
      versionNo: fact.fileFact.versionNo,
      referenceKey: fact.fileFact.referenceKey,
      artifactVersion: fact.fileFact.fileFactVersion.artifactVersion,
      referenceVersion: fact.fileFact.fileFactVersion.referenceVersion,
      availabilityVersion: fact.fileFact.fileFactVersion.availabilityVersion,
      scopeVersion: fact.fileFact.scopeVersion,
      sha256: fact.fileFact.sha256
    }))
    const answerSnapshot = JSON.stringify({
      answers: definition.value.questions
        .filter((question) => {
          const value = answers.value[question.code]
          return Array.isArray(value)
            ? value.length > 0
            : typeof value === 'string' && value.length > 0
        })
        .map((question) => ({ questionCode: question.code, value: answers.value[question.code] }))
    })
    preparedSubmission.value = {
      requestId,
      responseId,
      // 客户联系人不再采集（界面不展示）；已有手写签字，落"手签"
      customerContactRef: '手签',
      confirmCompletionCertificate: questionnaire.value?.completionCertificate ? confirmCompletionCertificate.value : undefined,
      signatureImageDataUrl: signatureValues.value.signatureImageDataUrl,
      answerSnapshot,
      files
    }
    outcome.value = await Api.submitPublicResponse(token, tenantId, preparedSubmission.value)
  } finally {
    submitting.value = false
  }
}
onMounted(load)
</script>

<style scoped lang="scss">
.public-page { --packet-ink: #1d2e42; --packet-muted: #617184; --packet-line: #dfe6ee; --el-color-primary: #23649f; --el-color-primary-light-9: #f2f7fc; position: relative; box-sizing: border-box; height: 100%; overflow-y: auto; scroll-padding: 20px 0 120px; padding: 32px 24px; font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif; color: var(--packet-ink); background: #f3f6f9; }
.questionnaire-shell { box-sizing: border-box; width: min(1248px, 100%); margin: 0 auto; padding: 32px 40px; border: 1px solid var(--packet-line); border-top: 4px solid var(--el-color-primary); background: #fff; }
.packet-header { padding-bottom: 24px; border-bottom: 1px solid var(--packet-line); }
.packet-brand { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.packet-brand img { width: 132px; height: auto; }
.packet-status { flex-shrink: 0; padding: 6px 12px; border: 1px solid var(--packet-line); border-radius: 4px; color: var(--packet-muted); font-size: 13px; }
.packet-status.submitted { color: var(--el-color-success-dark-2); border-color: var(--el-color-success-light-5); background: var(--el-color-success-light-9); }
.packet-header h1 { margin: 24px 0 12px; font-size: 28px; font-weight: 650; line-height: 1.4; letter-spacing: .02em; }
.packet-project { margin: 0 0 12px; font-size: 16px; font-weight: 500; line-height: 1.6; overflow-wrap: anywhere; }
.packet-intro { max-width: 56em; margin: 0 0 8px; font-size: 14px; line-height: 1.7; }
.packet-expiry { margin: 0; color: var(--packet-muted); font-size: 12px; line-height: 1.7; }
.packet-navigation { display: flex; gap: 24px; padding: 8px 0; border-bottom: 1px solid var(--packet-line); }
.packet-navigation button { min-height: 44px; padding: 0; border: 0; background: transparent; font: inherit; font-size: 14px; color: var(--el-color-primary); cursor: pointer; }
.packet-navigation button:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 4px; }
.packet-section { padding: 28px 0; border-bottom: 1px solid var(--packet-line); scroll-margin-top: 16px; }
.section-heading { display: flex; align-items: flex-start; gap: 12px; margin-bottom: 20px; }
.section-number { display: flex; flex-shrink: 0; align-items: center; justify-content: center; width: 28px; height: 28px; border-radius: 4px; color: var(--el-color-primary); background: var(--el-color-primary-light-9); font-size: 15px; font-weight: 600; }
.section-heading h2 { margin: 0 0 6px; font-size: 20px; font-weight: 600; line-height: 1.4; }
.section-heading p { margin: 0; color: var(--packet-muted); font-size: 13px; line-height: 1.6; }
.attachment-field { margin: 20px 0; }
.attachment-field p { font-size: 14px; color: var(--packet-muted); }
.joint-confirmation { align-items: flex-start; box-sizing: border-box; width: 100%; height: auto; margin-top: 20px; padding: 16px; border: 1px solid var(--packet-line); border-radius: 4px; background: var(--el-color-primary-light-9); white-space: normal; }
.joint-confirmation :deep(.el-checkbox__label) { white-space: normal; line-height: 1.7; color: var(--packet-ink); }
.joint-confirmation :deep(.el-checkbox__input) { margin-top: 4px; }
.joint-confirmation :deep(.el-checkbox__inner) { width: 20px; height: 20px; }
.joint-confirmation :deep(.el-checkbox__inner::after) { top: 3px; left: 7px; }
.customer-confirmation-form :deep(._fc-signature-btn) { min-height: 100px; line-height: 100px; font-size: 16px; color: var(--el-color-primary); }
.customer-confirmation-form :deep(._fc-signature-preview) { height: 144px; line-height: 144px; }
.customer-confirmation-form :deep(._fc-signature-preview img) { height: 144px; max-width: 100%; object-fit: contain; }
.attachment-field :deep(.el-button) { min-height: 44px; }
.submission-actions { padding-top: 20px; }
.submission-actions p, .score-summary { margin: 0 0 12px; color: var(--packet-muted); font-size: 13px; line-height: 1.6; }
.submit-button { width: 100%; min-height: 48px; font-size: 15px; }
@media (width <= 900px) {
  .public-page { padding: 0; }
  .questionnaire-shell { min-height: 100svh; padding: 20px 16px calc(124px + env(safe-area-inset-bottom)); border-inline: 0; }
  .packet-header { padding-bottom: 20px; }
  .packet-brand img { width: 112px; }
  .packet-header h1 { margin-top: 20px; font-size: 24px; line-height: 1.5; letter-spacing: 0; }
  .packet-project { font-size: 15px; }
  .packet-navigation { justify-content: space-between; gap: 12px; }
  .packet-section { padding-block: 24px; }
  .section-heading { gap: 10px; margin-bottom: 16px; }
  .section-heading h2 { font-size: 18px; }
  .submission-actions { position: fixed; z-index: 10; right: 0; bottom: 0; left: 0; padding: 10px 16px calc(12px + env(safe-area-inset-bottom)); border-top: 1px solid var(--packet-line); background: #fff; }
  .submission-actions p { margin-bottom: 8px; font-size: 12px; }
}
</style>

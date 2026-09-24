<template>
  <main class="public-page">
    <section class="questionnaire-shell" aria-labelledby="questionnaire-title">
      <header>
        <span class="eyebrow">项目满意度调查</span>
        <h1 id="questionnaire-title">{{ outcome ? '感谢您的反馈' : '请评价本次项目交付' }}</h1>
        <p v-if="questionnaire"
          >链接有效至 {{ formatDate(questionnaire.expiresAt) }}。答案、签字与附件提交后不可修改。</p
        >
      </header>
      <el-skeleton v-if="loading" :rows="7" animated aria-label="正在加载满意度问卷" />
      <el-result
        v-else-if="errorMessage"
        icon="warning"
        title="问卷暂不可用"
        :sub-title="errorMessage"
      />
      <el-result
        v-else-if="outcome"
        :icon="outcome.passed ? 'success' : 'warning'"
        title="提交成功"
        :sub-title="
          outcome.passed ? '本次满意度已达标，感谢您的反馈。' : '答卷已保存，项目团队将跟进整改。'
        "
      >
        <template #extra
          ><p class="score-summary"
            >判定得分 {{ outcome.score }}，阈值 {{ outcome.threshold }}</p
          ></template
        >
      </el-result>
      <section v-else-if="questionnaire" class="questionnaire-form">
        <div class="customer-confirmation-form">
          <form-create v-model="answers" v-model:api="questionFormApi" :rule="questionRules" :option="questionFormOption" />
        </div>
        <section class="file-section">
          <h2>签字与附件</h2>
          <div class="customer-confirmation-form">
            <form-create
              v-model="signatureValues"
              :rule="signatureRules"
              :option="questionFormOption"
            />
          </div>
          <div class="attachment-field"
            ><p>补充附件（可选）</p>
            <el-upload
              multiple
              :auto-upload="false"
              :disabled="submitting"
              :on-change="onAttachmentChange"
              :on-remove="onAttachmentRemove"
              ><el-button>选择附件</el-button></el-upload
            >
          </div>
        </section>
        <p>请确认由客户本人填写并签字。提交后答卷不可修改。</p>
        <el-button
          type="primary"
          size="large"
          class="submit-button"
          :loading="submitting"
          @click="submit"
          >提交答卷</el-button
        >
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import type { UploadFile } from 'element-plus'
import type { Api as FormApi } from '@form-create/element-ui'
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
const errorMessage = ref('')
const questionnaire = ref<PublicQuestionnaire>()
const definition = ref<QuestionnaireDefinition>({ schemaVersion: 1, questions: [] })
const answers = ref<Record<string, string | string[]>>({})
const questionFormApi = ref<FormApi>()
const questionFormOption = computed(() => ({
  ...customerFormOption,
  form: { ...customerFormOption.form, disabled: submitting.value }
}))
const questionRules = computed(() =>
  definition.value.questions.map((question) => ({
    type:
      question.type === 'MULTIPLE_CHOICE'
        ? 'checkbox'
        : question.type === 'TEXT'
          ? 'input'
          : 'radio',
    field: question.code,
    title: question.title,
    info:
      question.type === 'MULTIPLE_CHOICE'
        ? `请选择 ${question.minSelections ?? 0} 至 ${question.maxSelections ?? question.options?.length ?? 0} 项`
        : '',
    options: question.options?.map((option) => ({ label: option.label, value: option.code })),
    props:
      question.type === 'TEXT'
        ? { type: 'textarea', rows: 4, maxlength: question.maxLength, showWordLimit: true }
        : {},
    validate: [{ required: question.required, message: `请填写${question.title}` }]
  }))
)
const signatureValues = ref({ signatureImageDataUrl: '' })
const signatureRules = [
  {
    type: 'signaturePad',
    field: 'signatureImageDataUrl',
    title: '客户手写签字',
    validate: [{ required: true, message: '请手写签字' }]
  }
]
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
  if (submitting.value || outcome.value || !questionFormApi.value) return
  try { await questionFormApi.value.validate() }
  catch { message.warning('请完成必填问卷内容'); return }
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
    outcome.value = await Api.submitPublicResponse(token, tenantId, {
      requestId,
      responseId,
      // 客户联系人不再采集（界面不展示）；已有手写签字，落"手签"
      customerContactRef: '手签',
      answerSnapshot,
      files
    })
  } finally {
    submitting.value = false
  }
}
onMounted(load)
</script>

<style scoped lang="scss">
.public-page {
  height: 100%;
  box-sizing: border-box;
  overflow-y: auto;
  font-family: 'Microsoft YaHei', 'PingFang SC', Arial, sans-serif;
  padding: 40px 16px;
  background: var(--el-fill-color-light);
}
.questionnaire-shell {
  box-sizing: border-box;
  width: min(760px, 100%);
  margin: 0 auto;
  padding: 28px;
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
  background: var(--el-bg-color);
}
header h1 {
  margin: 6px 0;
  font-size: 28px;
  color: var(--el-text-color-primary);
}
.eyebrow {
  color: var(--el-color-primary);
  font-weight: 600;
}
header p {
  color: var(--el-text-color-secondary);
}
.questionnaire-form {
  margin-top: 28px;
}
.file-section h2 {
  margin: 0 0 16px;
  font-size: 17px;
}
.attachment-field {
  margin-top: 20px;
}
.file-section {
  padding-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.submit-button {
  width: 100%;
  margin-top: 16px;
}
.score-summary {
  color: var(--el-text-color-secondary);
}
@media (width <= 600px) {
  .public-page {
    padding: 0;
  }
  .questionnaire-shell {
    min-height: 100vh;
    padding: 20px 16px;
    border: 0;
  }
  header h1 {
    font-size: 23px;
  }
}
</style>

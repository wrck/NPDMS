<template>
  <main class="public-page">
    <section class="questionnaire-shell" aria-labelledby="questionnaire-title">
      <header>
        <span class="eyebrow">项目满意度调查</span>
        <h1 id="questionnaire-title">请完成本次满意度问卷</h1>
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
      <el-form v-else label-position="top" class="questionnaire-form" @submit.prevent>
        <article
          v-for="(question, index) in definition.questions"
          :key="question.code"
          class="question-card"
        >
          <h2
            ><span>{{ index + 1 }}</span
            >{{ question.title }}<em v-if="question.required">必答</em></h2
          >
          <el-radio-group
            v-if="question.type === 'SINGLE_CHOICE' || question.type === 'RATING'"
            :model-value="singleAnswer(question.code)"
            class="option-stack"
            @update:model-value="setSingleAnswer(question.code, $event)"
          >
            <el-radio
              v-for="option in question.options"
              :key="option.code"
              :value="option.code"
              border
              >{{ option.label }}</el-radio
            >
          </el-radio-group>
          <el-checkbox-group
            v-else-if="question.type === 'MULTIPLE_CHOICE'"
            :model-value="multipleAnswer(question.code)"
            class="option-stack"
            @update:model-value="setMultipleAnswer(question.code, $event)"
          >
            <el-checkbox
              v-for="option in question.options"
              :key="option.code"
              :value="option.code"
              border
              >{{ option.label }}</el-checkbox
            >
          </el-checkbox-group>
          <el-input
            v-else
            :model-value="singleAnswer(question.code)"
            type="textarea"
            :rows="4"
            :maxlength="question.maxLength"
            show-word-limit
            @update:model-value="setSingleAnswer(question.code, $event)"
          />
          <p v-if="question.type === 'MULTIPLE_CHOICE'" class="constraint"
            >请选择 {{ question.minSelections }} 至 {{ question.maxSelections }} 项</p
          >
        </article>
        <section class="file-section">
          <h2>签字与附件</h2>
          <el-form-item label="客户联系人"
            ><el-input v-model="customerContactRef" maxlength="256"
          /></el-form-item>
          <el-form-item label="签字文件（必需）">
            <el-upload
              :auto-upload="false"
              :limit="1"
              :on-change="onSignatureChange"
              :on-remove="() => (signatureFile = undefined)"
              ><el-button>选择签字文件</el-button></el-upload
            >
          </el-form-item>
          <el-form-item label="补充附件（可选）">
            <el-upload
              multiple
              :auto-upload="false"
              :on-change="onAttachmentChange"
              :on-remove="onAttachmentRemove"
              ><el-button>选择附件</el-button></el-upload
            >
          </el-form-item>
        </section>
        <el-button
          type="primary"
          size="large"
          class="submit-button"
          :loading="submitting"
          @click="submit"
          >提交答卷</el-button
        >
      </el-form>
    </section>
  </main>
</template>

<script setup lang="ts">
import type { UploadFile } from 'element-plus'
import { useRoute } from 'vue-router'
import * as Api from '@/api/pms/project/satisfaction'
import type {
  GrantFileFact,
  PublicQuestionnaire,
  QuestionnaireDefinition,
  SubmissionOutcome
} from '@/api/pms/project/satisfaction'

defineOptions({ name: 'PmsSatisfactionQuestionnairePublic' })
const route = useRoute()
const message = useMessage()
const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref('')
const questionnaire = ref<PublicQuestionnaire>()
const definition = ref<QuestionnaireDefinition>({ schemaVersion: 1, questions: [] })
const answers = reactive<Record<string, string | string[]>>({})
const customerContactRef = ref('')
const signatureFile = ref<File>()
const attachmentFiles = ref<File[]>([])
const outcome = ref<SubmissionOutcome>()
const requestId = crypto.randomUUID()
const token = String(route.params.token || '')
const tenantId = String(route.query.tenantId || '')
const singleAnswer = (code: string) =>
  typeof answers[code] === 'string' ? (answers[code] as string) : ''
const multipleAnswer = (code: string) =>
  Array.isArray(answers[code]) ? (answers[code] as string[]) : []
const setSingleAnswer = (code: string, value: string | number | boolean | undefined) => {
  answers[code] = String(value ?? '')
}
const setMultipleAnswer = (code: string, value: Array<string | number>) => {
  answers[code] = value.map(String)
}

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
      answers[question.code] = question.type === 'MULTIPLE_CHOICE' ? [] : ''
    })
  } catch {
    errorMessage.value = '链接已过期、已失效或无权访问。'
  } finally {
    loading.value = false
  }
}
const onSignatureChange = (file: UploadFile) => {
  signatureFile.value = file.raw
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
  const operationId = `${requestId}:${policyKey}:${ordinal}`
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
  if (!signatureFile.value || !customerContactRef.value.trim()) {
    message.warning('请填写客户联系人并选择签字文件')
    return
  }
  submitting.value = true
  try {
    const signature = await upload(signatureFile.value, 'SATISFACTION_SIGNATURE', 1)
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
        .filter((question) =>
          Array.isArray(answers[question.code])
            ? answers[question.code].length
            : String(answers[question.code]).length
        )
        .map((question) => ({ questionCode: question.code, value: answers[question.code] }))
    })
    outcome.value = await Api.submitPublicResponse(token, tenantId, {
      requestId,
      responseId,
      customerContactRef: customerContactRef.value.trim(),
      answerSnapshot,
      files
    })
  } finally {
    submitting.value = false
  }
}
const formatDate = (value: string) => new Date(value).toLocaleString()
onMounted(load)
</script>

<style scoped lang="scss">
.public-page {
  min-height: 100vh;
  padding: 40px 16px;
  background: var(--el-fill-color-light);
}
.questionnaire-shell {
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
header p,
.constraint {
  color: var(--el-text-color-secondary);
}
.questionnaire-form {
  margin-top: 28px;
}
.question-card {
  padding: 20px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}
.question-card h2,
.file-section h2 {
  margin: 0 0 16px;
  font-size: 17px;
}
.question-card h2 span {
  display: inline-grid;
  width: 28px;
  height: 28px;
  margin-right: 10px;
  place-items: center;
  border-radius: 50%;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.question-card h2 em {
  margin-left: 8px;
  color: var(--el-color-danger);
  font-size: 12px;
  font-style: normal;
}
.option-stack {
  display: grid;
  gap: 10px;
}
.option-stack :deep(.el-radio),
.option-stack :deep(.el-checkbox) {
  width: 100%;
  margin: 0;
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

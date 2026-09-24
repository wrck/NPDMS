<template>
  <main class="public-page">
    <section class="training-shell" aria-labelledby="training-title">
      <header>
        <span class="service-label">现场培训 · 客户确认</span>
        <h1 id="training-title">{{ record?.status === 2 ? '感谢您的确认' : '请评价本次培训' }}</h1>
        <p>感谢您参与本次现场培训。欢迎核对培训内容、填写评价并反馈意见，手写签字确认后将归档至项目交付件。</p>
      </header>
      <el-skeleton v-if="loading" :rows="7" animated />
      <el-result
        v-else-if="errorMessage"
        icon="warning"
        title="链接暂不可用"
        :sub-title="errorMessage"
      />
      <template v-else-if="record">
        <section class="record-summary">
          <h2>{{ record.name }}</h2>
          <p class="record-code">{{ record.code }}</p>
          <dl>
            <div
              ><dt>培训类型</dt><dd>{{ record.trainingTypeLabels }}</dd></div
            >
            <div
              ><dt>培训时间</dt><dd>{{ record.trainingTime }}</dd></div
            >
            <div
              ><dt>培训工程师</dt><dd>{{ record.trainerName }}</dd></div
            >
          </dl>
          <p class="training-content">{{ record.content || '未填写培训内容' }}</p>
        </section>
        <el-alert
          v-if="record.status === 2"
          type="success"
          :closable="false"
          :title="confirmedTitle"
        />
        <div class="customer-confirmation-form">
          <form-create
            v-model="values"
            v-model:api="formApi"
            :rule="formRules"
            :option="formOption"
          />
        </div>
        <p v-if="record.status === 2 && !record.signatureImageDataUrl" class="record-code"
          >此历史记录确认时未采集手写签字图片。</p
        >
        <footer v-if="record.status !== 2">
          <p
            >链接有效至
            {{ formatDate(record.tokenExpiresAt) }}。提交后不可修改，请确认由本人签字。</p
          >
          <el-button type="primary" size="large" :loading="submitting" @click="submit"
            >签字并确认培训记录</el-button
          >
        </footer>
      </template>
    </section>
  </main>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import type { Api, Rule } from '@form-create/element-ui'
import { confirmPublicTraining, inspectPublicTraining } from '@/api/pms/engineering/training'
import type { TrainingPublicVO } from '@/api/pms/engineering/training'
import { formatDate } from '@/utils/formatTime'
import { customerFormOption } from '@/components/FormCreate/src/customerConfirmation'

defineOptions({ name: 'PmsTrainingRecordPublic' })
const route = useRoute()
const message = useMessage()
const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref('')
const record = ref<TrainingPublicVO>()
const formApi = ref<Api>()
const formRules = ref<Rule[]>([])
const values = ref<Record<string, any>>({})
const formOption = computed(() => ({
  ...customerFormOption,
  form: { ...customerFormOption.form, disabled: record.value?.status === 2 }
}))
// 历史外发链接的冻结规则仍采集签字人姓名，新外发不再有该输入
const confirmedTitle = computed(() => {
  const parts = [record.value?.signConfirmerName, record.value?.signTime ? formatDate(record.value.signTime) : ''].filter(Boolean)
  return `已确认 · ${parts.join(' · ')}`
})
const token = String(route.params.token || '')
const tenantId = String(route.query.tenantId || '')
const load = async () => {
  if (!token || !/^\d+$/.test(tenantId)) {
    errorMessage.value = '受控链接缺少有效租户信息。'
    loading.value = false
    return
  }
  try {
    record.value = await inspectPublicTraining(token, tenantId)
    // 确认表单按冻结规则渲染；签字人姓名不再展示，确认时自动填充"手签"
    formRules.value = (JSON.parse(record.value.confirmationFormRules || '[]') as Rule[]).filter(
      (rule) => rule.field !== 'signConfirmerName'
    )
    values.value = {
      ...JSON.parse(record.value.confirmationValues || '{}'),
      skillRating: record.value.skillRating,
      effectRating: record.value.effectRating,
      satisfactionRating: record.value.satisfactionRating,
      signOpinion: record.value.signOpinion,
      signConfirmerName: record.value.signConfirmerName,
      signatureImageDataUrl: record.value.signatureImageDataUrl
    }
    if (record.value.status === 2)
      formRules.value = formRules.value
        .filter(
          (rule) => rule.field !== 'signatureImageDataUrl' || !!record.value?.signatureImageDataUrl
        )
        .map((rule) => ({
          ...rule,
          props: { ...rule.props, disabled: true }
        }))
  } catch {
    errorMessage.value = '链接已过期、已失效或无权访问。'
  } finally {
    loading.value = false
  }
}
const submit = async () => {
  if (submitting.value || !formApi.value) return
  // 历史外发规则仍采集签字人姓名；已有手签时默认填充"手签"，客户无需再填姓名
  if (values.value.signatureImageDataUrl && !String(values.value.signConfirmerName ?? '').trim()) {
    values.value.signConfirmerName = '手签'
  }
  try {
    await formApi.value.validate()
  } catch {
    message.warning('请完成评价和手写签字')
    return
  }
  const { signatureImageDataUrl, ...answers } = values.value
  if (!signatureImageDataUrl) {
    message.warning('请手写签字')
    return
  }
  submitting.value = true
  try {
    await confirmPublicTraining(token, tenantId, {
      ...answers,
      signConfirmerName: String(answers.signConfirmerName ?? '').trim(),
      signatureImageDataUrl,
      confirmationValues: JSON.stringify(answers)
    })
    message.success('培训记录已确认')
    await load()
  } finally {
    submitting.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.public-page {
  height: 100%;
  box-sizing: border-box;
  overflow-y: auto;
  font-family: 'Microsoft YaHei', 'PingFang SC', Arial, sans-serif;
  background: #f3f6fa;
  padding: 32px 16px;
}
.training-shell {
  max-width: 720px;
  margin: auto;
  background: #fff;
  padding: 32px 40px;
  border-top: 4px solid var(--el-color-primary);
  box-sizing: border-box;
}
.service-label {
  color: var(--el-color-primary);
  font-size: 14px;
  font-weight: 600;
}
h1 {
  font-size: 26px;
  margin: 10px 0;
  color: var(--el-text-color-primary);
}
header p,
footer p,
.record-code {
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 1.7;
}
.record-summary {
  margin: 28px 0;
  padding: 20px;
  background: #f7f9fc;
}
h2 {
  font-size: 18px;
  margin: 0 0 8px;
  overflow-wrap: anywhere;
}
.record-code {
  margin: 0;
}
dl {
  display: flex;
  flex-wrap: wrap;
  gap: 20px 32px;
  margin: 20px 0;
}
dt {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-bottom: 6px;
}
dd {
  margin: 0;
  font-size: 14px;
}
.training-content {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.8;
  margin: 0;
}
.customer-confirmation-form {
  margin-top: 28px;
}
footer {
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}
footer .el-button {
  width: 100%;
  min-height: 48px;
  margin-top: 8px;
}
@media (max-width: 600px) {
  .public-page {
    padding: 0;
  }
  .training-shell {
    padding: 24px 18px;
    min-height: 100vh;
  }
  h1 {
    font-size: 23px;
  }
  .record-summary {
    padding: 16px;
  }
}
</style>

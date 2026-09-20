<template>
  <main class="public-page">
    <section class="training-shell" aria-labelledby="training-title">
      <header>
        <span class="eyebrow">现场培训记录确认</span>
        <h1 id="training-title">请确认本次现场培训</h1>
        <p v-if="record"
          >链接有效至 {{ formatDate(record.tokenExpiresAt) }}。提交评价与签字后不可修改，记录将同步归档至项目交付件。</p
        >
      </header>
      <el-skeleton v-if="loading" :rows="7" animated aria-label="正在加载培训记录" />
      <el-result v-else-if="errorMessage" icon="warning" title="链接暂不可用" :sub-title="errorMessage" />
      <el-result v-else-if="record?.status === 2" icon="success" title="培训记录已确认" sub-title="感谢您的反馈。">
        <template #extra>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="签字人">{{ record.signConfirmerName }}</el-descriptions-item>
            <el-descriptions-item label="确认时间">{{ formatDate(record.signTime) }}</el-descriptions-item>
            <el-descriptions-item label="技术水平及表达能力">{{ record.skillRating }}</el-descriptions-item>
            <el-descriptions-item label="培训内容及讲解效果">{{ record.effectRating }}</el-descriptions-item>
            <el-descriptions-item label="培训满意度">{{ record.satisfactionRating }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </el-result>
      <el-form v-else-if="record" label-position="top" class="training-form" @submit.prevent>
        <article class="record-card">
          <h2>培训信息</h2>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="培训名称">{{ record.name }}（{{ record.code }}）</el-descriptions-item>
            <el-descriptions-item label="培训类型">{{ record.trainingTypeLabels }}</el-descriptions-item>
            <el-descriptions-item label="培训时间">{{ record.trainingTime }}</el-descriptions-item>
            <el-descriptions-item label="培训工程师">{{ record.trainerName }}</el-descriptions-item>
            <el-descriptions-item label="培训内容">
              <pre class="content-pre">{{ record.content || '（未填写）' }}</pre>
            </el-descriptions-item>
          </el-descriptions>
        </article>
        <article class="record-card">
          <h2>客户填写区域</h2>
          <el-form-item label="培训工程师技术水平及表达能力" required>
            <el-radio-group v-model="form.skillRating">
              <el-radio v-for="option in qualityOptions" :key="option" :value="option" border>{{ option }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="培训内容及讲解效果" required>
            <el-radio-group v-model="form.effectRating">
              <el-radio v-for="option in qualityOptions" :key="option" :value="option" border>{{ option }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="培训满意度" required>
            <el-radio-group v-model="form.satisfactionRating">
              <el-radio v-for="option in satisfactionOptions" :key="option" :value="option" border>{{
                option
              }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="综合意见">
            <el-input v-model="form.signOpinion" type="textarea" :rows="3" maxlength="500" show-word-limit />
          </el-form-item>
          <el-form-item label="签字人姓名" required>
            <el-input v-model="form.signConfirmerName" maxlength="64" />
          </el-form-item>
        </article>
        <el-button type="primary" size="large" class="submit-button" :loading="submitting" @click="submit"
          >签字确认</el-button
        >
      </el-form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import { confirmPublicTraining, inspectPublicTraining } from '@/api/pms/engineering/training'
import type { TrainingPublicVO } from '@/api/pms/engineering/training'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'PmsTrainingRecordPublic' })
const route = useRoute()
const message = useMessage()
const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref('')
const record = ref<TrainingPublicVO>()
const token = String(route.params.token || '')
const tenantId = String(route.query.tenantId || '')
const qualityOptions = ['很好', '良好', '一般', '差']
const satisfactionOptions = ['非常满意', '较满意', '一般', '差']
const form = reactive({
  skillRating: '',
  effectRating: '',
  satisfactionRating: '',
  signOpinion: '',
  signConfirmerName: ''
})


const load = async () => {
  if (!token || !/^\d+$/.test(tenantId)) {
    errorMessage.value = '受控链接缺少有效租户信息。'
    loading.value = false
    return
  }
  try {
    record.value = await inspectPublicTraining(token, tenantId)
  } catch {
    errorMessage.value = '链接已过期、已失效或无权访问。'
  } finally {
    loading.value = false
  }
}
const submit = async () => {
  if (!form.skillRating || !form.effectRating || !form.satisfactionRating || !form.signConfirmerName.trim()) {
    message.warning('请完成三项评价并填写签字人姓名')
    return
  }
  submitting.value = true
  try {
    await confirmPublicTraining(token, tenantId, {
      skillRating: form.skillRating,
      effectRating: form.effectRating,
      satisfactionRating: form.satisfactionRating,
      signOpinion: form.signOpinion,
      signConfirmerName: form.signConfirmerName.trim()
    })
    message.success('确认成功')
    await load()
  } catch {
    message.error('确认失败：链接可能已过期或已使用')
  } finally {
    submitting.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.public-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding: 24px 12px;
}
.training-shell {
  max-width: 760px;
  margin: 0 auto;
  background: #fff;
  border-radius: 12px;
  padding: 24px;
}
.eyebrow {
  color: var(--el-color-primary);
  font-size: 13px;
}
.record-card {
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
.content-pre {
  margin: 0;
  white-space: pre-wrap;
  font-family: inherit;
}
.submit-button {
  width: 100%;
}
</style>

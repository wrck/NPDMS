<!-- src/components/ConnectorDesigner/StepRetry.vue -->
<script setup lang="ts">
/**
 * 连接器分步表单 — Step 6 重试与超时。
 *
 * <p>配置：maxAttempts（最大重试次数）、waitMillis（重试间隔）、
 * timeoutMillis（请求超时）、retryOnStatusCodes（触发重试的 HTTP 状态码）。</p>
 */
import { reactive, ref, watch } from 'vue'
import type { RetryConfig } from '@/api/lowcode-connector'

const props = defineProps<{
  retry: RetryConfig
}>()

const emit = defineEmits<{
  'update:retry': [value: RetryConfig]
}>()

const form = reactive<RetryConfig>({
  maxAttempts: props.retry.maxAttempts,
  waitMillis: props.retry.waitMillis,
  timeoutMillis: props.retry.timeoutMillis,
  retryOnStatusCodes: [...props.retry.retryOnStatusCodes]
})

watch(
  () => props.retry,
  (val) => {
    form.maxAttempts = val.maxAttempts
    form.waitMillis = val.waitMillis
    form.timeoutMillis = val.timeoutMillis
    form.retryOnStatusCodes = [...val.retryOnStatusCodes]
  },
  { deep: true }
)

watch(
  form,
  (val) => {
    emit('update:retry', {
      maxAttempts: val.maxAttempts,
      waitMillis: val.waitMillis,
      timeoutMillis: val.timeoutMillis,
      retryOnStatusCodes: [...val.retryOnStatusCodes]
    })
  },
  { deep: true }
)

// 状态码输入：以逗号分隔
const codesText = ref<string>(form.retryOnStatusCodes.join(','))

watch(codesText, (val) => {
  form.retryOnStatusCodes = val
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0)
    .map((s) => Number(s))
    .filter((n) => !Number.isNaN(n))
})

watch(
  () => props.retry.retryOnStatusCodes,
  (val) => {
    codesText.value = val.join(',')
  }
)
</script>

<template>
  <div class="step-retry">
    <el-form :model="form" label-width="160px" class="retry-form">
      <el-form-item label="最大重试次数">
        <el-input-number v-model="form.maxAttempts" :min="0" :max="10" />
        <div class="field-hint">0 表示不重试，建议 3</div>
      </el-form-item>
      <el-form-item label="重试间隔 (毫秒)">
        <el-input-number v-model="form.waitMillis" :min="0" :max="60000" :step="500" />
        <div class="field-hint">每次重试之间的等待时间</div>
      </el-form-item>
      <el-form-item label="请求超时 (毫秒)">
        <el-input-number v-model="form.timeoutMillis" :min="1000" :max="300000" :step="1000" />
        <div class="field-hint">单次请求超时时间，建议 30000</div>
      </el-form-item>
      <el-form-item label="触发重试状态码">
        <el-input v-model="codesText" placeholder="500,502,503" style="width: 240px" />
        <div class="field-hint">以逗号分隔的 HTTP 状态码，命中时触发重试</div>
      </el-form-item>
    </el-form>
  </div>
</template>

<script lang="ts">
export default { name: 'StepRetry' }
</script>

<style scoped lang="scss">
.step-retry {
  max-width: 640px;
  .field-hint {
    margin-top: 4px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}
</style>

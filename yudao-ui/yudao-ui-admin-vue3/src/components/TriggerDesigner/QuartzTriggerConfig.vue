<script setup lang="ts">
/**
 * Quartz 触发配置（包装 CronEditor 可视化编辑器）。
 *
 * <p>结构化配置仅含 cronExpression 一个字段，通过 CronEditor 进行可视化编辑。
 * CronEditor 内部将 cron 字符串与 5 字段编辑状态双向同步，并附带人类可读
 * 描述与下次执行时间预览。</p>
 */
import { reactive, watch } from 'vue'
import CronEditor from '@/components/CronEditor/index.vue'
import type { QuartzTriggerConfig } from '@/api/lowcode-trigger'

defineOptions({ name: 'QuartzTriggerConfigView' })

const props = defineProps<{
  /** 结构化 Quartz 配置（v-model） */
  modelValue: QuartzTriggerConfig
}>()

const emit = defineEmits<{ (e: 'update:modelValue', v: QuartzTriggerConfig): void }>()

const form = reactive<QuartzTriggerConfig>({
  cronExpression: props.modelValue.cronExpression || '0 0 * * * ?'
})

// 外部变更同步到本地
watch(
  () => props.modelValue,
  (v) => {
    if (v.cronExpression !== form.cronExpression) {
      form.cronExpression = v.cronExpression || '0 0 * * * ?'
    }
  }
)

// 本地变更向上同步
watch(
  () => form.cronExpression,
  (v) => {
    emit('update:modelValue', { cronExpression: v })
  }
)
</script>

<template>
  <div class="quartz-trigger-config">
    <div class="section-hint">
      配置定时触发器的 Quartz cron 表达式（6 字段：秒 分 时 日 月 周）。
    </div>
    <CronEditor v-model="form.cronExpression" />
  </div>
</template>

<style scoped lang="scss">
.quartz-trigger-config {
  max-width: 760px;
}

.section-hint {
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
</style>

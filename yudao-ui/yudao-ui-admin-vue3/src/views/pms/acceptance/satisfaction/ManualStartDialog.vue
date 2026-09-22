<template>
  <el-dialog :model-value="modelValue" title="发起满意度调查" width="min(760px, 94vw)"
    :close-on-click-modal="false" :before-close="close" @closed="emit('update:modelValue', false)">
    <el-alert title="首次发起将冻结所选问卷。后续修改模板不会改变本次调查；责任人沿用关联任务指派，未指派时由项目经理负责。"
      type="info" :closable="false" show-icon />
    <el-form label-position="top" class="mt-16px" :disabled="submitting">
      <el-form-item label="关联项目任务" required>
        <el-select v-model="projectTaskId" filterable placeholder="选择承接本次调查的未完成任务" class="!w-full">
          <el-option v-for="task in options.tasks" :key="task.id" :value="task.id" :label="`${task.stageCode} · ${task.name}`" />
        </el-select>
      </el-form-item>
      <el-form-item label="问卷模板" required>
        <el-select v-model="revisionId" filterable placeholder="选择已发布问卷" class="!w-full" :loading="loading">
          <el-option v-for="item in published" :key="item.revision.id" :value="item.revision.id"
            :label="`${item.template.name} · 第${item.revision.revisionNo}版`" />
        </el-select>
      </el-form-item>
      <el-alert v-if="!loading && !published.length" title="暂无已发布问卷。请在下方模板管理中配置并发布，再刷新列表。" type="warning" :closable="false" />
      <p v-if="selected">达标阈值：{{ selected.revision.threshold }}；规则版本：{{ selected.revision.ruleVersion }}</p>
      <el-collapse v-if="selected" class="mt-16px">
        <el-collapse-item title="预览客户问卷" name="preview">
          <p>预览所选发布版本，发起后客户填写相同题目。此处填写不会保存。</p>
          <QuestionnaireFields :frozen-questions="selected.revision.questionnaireJson" />
        </el-collapse-item>
      </el-collapse>
      <el-collapse v-model="expanded" class="mt-16px">
        <el-collapse-item title="管理问卷模板" name="templates"><TemplatePanel v-if="expanded.includes('templates')" /></el-collapse-item>
      </el-collapse>
      <el-button class="mt-12px" :loading="loading" @click="load">刷新已发布模板</el-button>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mt-12px" />
    <template #footer>
      <el-button :disabled="submitting" @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!projectTaskId || !selected" @click="submit">确认发起</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import * as Api from '@/api/pms/acceptance/satisfaction'
import TemplatePanel from './TemplatePanel.vue'
import QuestionnaireFields from './QuestionnaireFields.vue'
const props = defineProps<{ modelValue: boolean; options: Api.ManualStartOptions; submitting: boolean; error: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; submit: [value: Api.ManualStartSelection] }>()
const projectTaskId = ref<number>()
const revisionId = ref<number>()
const templates = ref<Api.TemplateView[]>([])
const loading = ref(false)
const expanded = ref<string[]>([])
const published = computed(() => templates.value.flatMap(template => template.revisions
  .filter(revision => template.status === 'PUBLISHED' && revision.status === 'PUBLISHED' && revision.id === template.currentRevisionId)
  .map(revision => ({ template, revision }))))
const selected = computed(() => published.value.find(item => item.revision.id === revisionId.value))
const load = async () => {
  loading.value = true
  try { templates.value = await Api.listTemplates() } catch { templates.value = [] }
  finally { loading.value = false }
}
const close = () => { if (!props.submitting) emit('update:modelValue', false) }
const submit = () => {
  if (!projectTaskId.value || !selected.value || props.submitting) return
  emit('submit', { projectTaskId: projectTaskId.value, templateId: selected.value.template.id, revisionId: selected.value.revision.id })
}
watch(() => props.modelValue, (open) => {
  if (!open) return
  projectTaskId.value = undefined
  revisionId.value = undefined
  expanded.value = []
  void load()
})
</script>

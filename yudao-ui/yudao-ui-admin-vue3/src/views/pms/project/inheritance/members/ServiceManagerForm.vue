<template>
  <el-form label-position="top" @submit.prevent="submit">
    <ProjectMemberServiceFields ref="fields" :project="project" />
    <el-form-item label="调整原因" required>
      <el-input v-model="reason" aria-label="调整原因" type="textarea" maxlength="500" show-word-limit />
    </el-form-item>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <div class="actions">
      <el-button :disabled="saving" @click="$emit('cancel')">取消</el-button>
      <el-button type="primary" native-type="submit" :loading="saving">保存服务经理</el-button>
    </div>
  </el-form>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { assignManager, type ProjectMasterVO } from '@/api/pms/project/projects'
import ProjectMemberServiceFields from '@/views/pms/project/project-master-detail/components/ProjectMemberServiceFields.vue'
import { createSubmissionIdempotencyState } from '@/views/pms/project/projects/submissionIdempotency'
const props = defineProps<{ project: ProjectMasterVO }>()
const emit = defineEmits<{ saved: []; cancel: [] }>()
const fields = ref<InstanceType<typeof ProjectMemberServiceFields>>()
const reason = ref(''), error = ref(''), saving = ref(false)
const submission = createSubmissionIdempotencyState()
const submit = async () => {
  if (saving.value) return
  const selected = fields.value?.selection()
  if (!selected || !reason.value.trim() || props.project.version == null) {
    error.value = '请完整选择服务经理、责任范围并填写原因'; return
  }
  saving.value = true
  error.value = ''
  const data = { ...selected, changeReason: reason.value.trim() }
  try {
    await assignManager(props.project.id!, data, props.project.version,
      submission.keyFor({ projectId: props.project.id, version: props.project.version, ...data }))
    emit('saved')
  } catch { error.value = '保存未成功。请核对人员资格或刷新项目版本后重试。' }
  finally { saving.value = false }
}
</script>
<style scoped>
.actions { display: flex; justify-content: flex-end; gap: .5rem; margin-top: 1rem; }
</style>

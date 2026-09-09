<template>
  <ContentWrap v-loading="loading">
    <div class="member-header">
      <h3>项目成员管理</h3>
      <div>
        <el-button :loading="loading" @click="load">刷新成员</el-button>
        <el-button
          v-hasPermi="['pms:project:assign']"
          type="primary"
          :disabled="!current || project.lifecycleStatus !== 'ACTIVE' || loading"
          @click="editing = true"
          >调整成员</el-button
        >
      </div>
    </div>
    <p>同角色成员具有相同操作权限；主责仅作责任标识。人员调整不推进阶段。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <div v-if="current?.members.length" class="manager-list">
      <div v-for="member in current.members" :key="member.assignmentId" class="manager-item">
        <span>{{ member.name || `用户 ${member.userId}` }}</span>
        <el-tag v-if="member.userId === current.primaryUserId">当前主责</el-tag>
        <el-tag v-else type="info">项目经理</el-tag>
      </div>
    </div>
    <el-empty
      v-else-if="current && !loading"
      description="尚未指派项目经理，可与服务经理同次指派，也可分次增补"
    />
    <el-text v-if="project.lifecycleStatus !== 'ACTIVE'" type="info"
      >项目已关闭，成员信息只读。</el-text
    >
    <el-dialog
      v-model="editing"
      title="调整项目成员"
      :width="dialogWidth"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <ProjectMemberForm
        v-if="current && editing"
        :project="project"
        :current="current"
        @cancel="editing = false"
        @saved="saved"
      />
    </el-dialog>
  </ContentWrap>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import { getProjectManagers, type ProjectManagers } from '@/api/pms/project/members'
import ProjectMemberForm from './ProjectMemberForm.vue'
const props = defineProps<{ project: ProjectMasterVO }>()
const emit = defineEmits<{ updated: [] }>()
const current = ref<ProjectManagers>()
const loading = ref(false),
  editing = ref(false),
  error = ref('')
const mobile = useMediaQuery('(max-width: 767px)')
const dialogWidth = computed(() => (mobile.value ? '96%' : '640px'))
const load = async () => {
  loading.value = true
  error.value = ''
  try {
    current.value = await getProjectManagers(props.project.id!)
  } catch {
    current.value = undefined
    error.value = '成员加载失败，请点击刷新成员重试。'
  } finally {
    loading.value = false
  }
}
const saved = async () => {
  editing.value = false
  await load()
  emit('updated')
}
onMounted(load)
</script>
<style scoped>
.member-header,
.manager-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}
h3 {
  font-size: 1rem;
  margin: 0;
}
p {
  color: var(--el-text-color-secondary);
}
.manager-list {
  display: grid;
  gap: 0.75rem;
}
.manager-item {
  padding: 0.75rem;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}
@media (width <= 767px) {
  .member-header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>

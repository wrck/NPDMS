<template>
  <ContentWrap>
    <div class="heading">
      <div><h3>项目交付件</h3><p>上传交付文件，或查看业务文档归集结果。交付件与生命周期实例使用同一份记录。</p></div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table v-loading="loading" :data="rows" row-key="id">
      <el-table-column prop="stageCode" label="阶段" width="90" />
      <el-table-column prop="name" label="交付件" min-width="190" />
      <el-table-column label="要求" width="80"><template #default="{ row }">{{ row.required ? '必需' : '可选' }}</template></el-table-column>
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 'ACCEPTED' ? 'success' : 'info'">{{ row.status === 'ACCEPTED' ? '已满足' : '待提交' }}</el-tag></template></el-table-column>
      <el-table-column label="材料" width="160"><template #default="{ row }"><el-button v-if="row.id" link type="primary" @click="dialog?.open(row.id)">上传 / 查看材料</el-button></template></el-table-column>
    </el-table>
    <ProjectDeliverableDialog ref="dialog" :project-id="projectId" @changed="changed" />
  </ContentWrap>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import * as ProjectsApi from '@/api/pms/project/projects'
import ProjectDeliverableDialog from './ProjectDeliverableDialog.vue'
const props = defineProps<{ projectId: number }>()
const emit = defineEmits<{ changed: [] }>()
const rows = ref<Awaited<ReturnType<typeof ProjectsApi.getProjectInstances>>['deliverables']>([])
const dialog = ref<InstanceType<typeof ProjectDeliverableDialog>>()
const loading = ref(false), error = ref('')
let generation = 0
const load = async () => {
  const current = ++generation
  loading.value = true; error.value = ''; rows.value = []
  try {
    const result = await ProjectsApi.getProjectInstances(props.projectId)
    if (generation === current) rows.value = result.deliverables
  } catch { if (generation === current) error.value = '项目交付件加载失败，请重试' }
  finally { if (generation === current) loading.value = false }
}
const changed = async () => { await load(); emit('changed') }
watch(() => props.projectId, load, { immediate: true })
</script>
<style scoped>
.heading { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.heading p { color: var(--el-text-color-secondary); }
</style>

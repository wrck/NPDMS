<template>
  <div v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form-item label="服务经理层级">
      <el-select v-model="levelCode" @change="clearCandidate"
        ><el-option label="办事处级（L1）" value="L1" /><el-option
          label="本地实施级（L2）"
          value="L2"
      /></el-select>
    </el-form-item>
    <el-form-item label="服务经理责任类型">
      <el-select v-model="assignmentType"
        ><el-option label="主责" value="PRIMARY" /><el-option label="协同" value="COLLABORATOR"
      /></el-select>
    </el-form-item>
    <el-form-item label="实施站点" :required="levelCode === 'L2'">
      <el-select
        v-model="siteId"
        clearable
        placeholder="L1可保持项目级，L2须选择站点"
        @change="clearCandidate"
      >
        <el-option
          v-for="site in sites"
          :key="site.siteId"
          :value="site.siteId"
          :label="site.siteNameSnapshot || `站点 ${site.siteId}`"
        />
      </el-select>
    </el-form-item>
    <el-form-item label="服务经理办事处" required>
      <el-select
        v-model="departmentId"
        aria-label="服务经理办事处"
        filterable
        @change="clearCandidate"
      >
        <el-option
          v-for="dept in departments"
          :key="dept.id"
          :value="dept.id"
          :label="`${dept.name}（${dept.code}）`"
        />
      </el-select>
    </el-form-item>
    <el-form-item label="服务经理" required>
      <UserSelect v-model="managerId" option-label="nickname-account" aria-label="服务经理" :scoped-source="managerSource" :disabled="!departmentId || (levelCode === 'L2' && !siteId)" :clearable="false" placeholder="按既有公司、办事处规则搜索" />
    </el-form-item>
  </div>
</template>
<script setup lang="ts">
import UserSelect from '@/views/system/user/components/UserSelect.vue'
import { computed, onMounted, ref } from 'vue'
import * as Projects from '@/api/pms/project/projects'
import * as Depts from '@/api/system/dept'
import type { ServiceManagerSelection } from '@/api/pms/project/members'
const props = defineProps<{ project: Projects.ProjectMasterVO }>()
const levelCode = ref<'L1' | 'L2'>('L1')
const assignmentType = ref<'PRIMARY' | 'COLLABORATOR'>('PRIMARY')
const siteId = ref<number>()
const departmentId = ref(props.project.departmentId)
const managerId = ref<number>()
const departments = ref<Depts.DeptVO[]>([])
const sites = ref<Projects.ProjectSiteVO[]>([])
const loading = ref(false), error = ref('')
const clearCandidate = () => { managerId.value = undefined }
const managerSource = computed(() => ({
  key: `service-manager:${props.project.id}:${departmentId.value}:${levelCode.value}:${siteId.value ?? ''}`,
  search: async ({ keyword, pageNo, pageSize }: { keyword: string; pageNo: number; pageSize: number }) => {
    const dept = departments.value.find(item => item.id === departmentId.value)
    if (!dept || (levelCode.value === 'L2' && !siteId.value)) return { list: [], total: 0 }
    const result = await Projects.getServiceManagerCandidates(props.project.id!, {
      departmentId: dept.id, departmentCode: dept.code, siteId: siteId.value, keyword, pageNo, pageSize
    })
    return { list: result.list.map(item => ({ ...item, id: item.userId })), total: result.total }
  }
}))
const selection = (): ServiceManagerSelection | undefined => {
  const dept = departments.value.find((item) => item.id === departmentId.value)
  if (!dept || !managerId.value || (levelCode.value === 'L2' && !siteId.value)) return undefined
  return {
    levelCode: levelCode.value,
    assignmentType: assignmentType.value,
    managerId: managerId.value,
    siteId: siteId.value,
    departmentId: dept.id,
    departmentCode: dept.code
  }
}
onMounted(async () => {
  loading.value = true
  try {
    ;[departments.value, sites.value] = await Promise.all([
      Depts.getSimpleDeptList(),
      Projects.getProjectSites(props.project.id!)
    ])
  } catch {
    error.value = '组织或站点加载失败，请取消后重试。'
  } finally {
    loading.value = false
  }
})
defineExpose({ selection })
</script>
<style scoped>
.el-select {
  width: 100%;
}
</style>

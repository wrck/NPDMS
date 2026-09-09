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
      <el-select
        v-model="managerId"
        aria-label="服务经理"
        filterable
        remote
        :remote-method="search"
        :loading="searching"
        placeholder="按既有公司、办事处规则搜索"
        @visible-change="(open: boolean) => open && search('')"
      >
        <el-option
          v-for="person in candidates"
          :key="person.userId"
          :value="person.userId"
          :label="`${person.nickname}（${person.username}）`"
        />
        <template #footer
          ><el-text v-if="total > candidates.length"
            >当前显示前20项，请输入更精确的姓名或账号</el-text
          ></template
        >
      </el-select>
    </el-form-item>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
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
const candidates = ref<Projects.ServiceManagerCandidateVO[]>([])
const loading = ref(false),
  searching = ref(false),
  error = ref(''),
  total = ref(0)
let sequence = 0
const clearCandidate = () => {
  sequence++
  managerId.value = undefined
  candidates.value = []
  searching.value = false
}
const search = async (keyword: string) => {
  const dept = departments.value.find((item) => item.id === departmentId.value)
  if (!dept || (levelCode.value === 'L2' && !siteId.value)) return
  const current = ++sequence
  searching.value = true
  try {
    const result = await Projects.getServiceManagerCandidates(props.project.id!, {
      departmentId: dept.id,
      departmentCode: dept.code,
      siteId: siteId.value,
      keyword,
      pageNo: 1,
      pageSize: 20
    })
    if (current === sequence) {
      candidates.value = result.list
      total.value = result.total
    }
  } catch {
    error.value = '服务经理候选加载失败，请重试。'
  } finally {
    if (current === sequence) searching.value = false
  }
}
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

<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="根项目" prop="rootProjectId">
        <PmsEntitySelect
          v-model="query.rootProjectId"
          :api="ProjectApi.getProjectPage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择根项目"
          class="!w-220px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="loadTree"><Icon icon="ep:search" />查询树</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table
      v-loading="loading"
      :data="treeData"
      row-key="id"
      :tree-props="{ children: 'children' }"
      default-expand-all
      empty-text="暂无项目树数据"
    >
      <el-table-column prop="code" label="项目编码" min-width="140" />
      <el-table-column prop="name" label="项目名称" min-width="180" />
      <el-table-column prop="parentId" label="父项目编号" width="120" />
      <el-table-column prop="category" label="项目分类" min-width="120">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_PROJECT_CATEGORY" :value="row.category" />
        </template>
      </el-table-column>
      <el-table-column prop="majorProjectFlag" label="重大项目" width="100">
        <template #default="{ row }">{{ row.majorProjectFlag ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column prop="managerUserId" label="项目经理" width="110">
        <template #default="{ row }">
          <UserTag :user-id="row.managerUserId" />
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_PROJECT_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="depth" label="深度" width="80" />
    </el-table>
  </ContentWrap>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { DICT_TYPE } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import * as ProjectTreeApi from '@/api/pms/project/project-tree'
import * as ProjectApi from '@/api/pms/project/project'
import UserTag from '@/components/UserTag/index.vue'
import type { ProjectTreeNodeVO } from '@/api/pms/project/project-tree'

defineOptions({ name: 'PmsProjectTree' })
const message = useMessage()
const loading = ref(false)
const query = reactive({ rootProjectId: undefined as number | undefined })
const treeData = ref<ProjectTreeNodeVO[]>([])

const loadTree = async () => {
  if (!query.rootProjectId) {
    message.warning('请先输入根项目编号')
    return
  }
  loading.value = true
  try {
    const data = await ProjectTreeApi.getProjectTree(query.rootProjectId)
    treeData.value = data ? [data] : []
  } finally {
    loading.value = false
  }
}
onMounted(() => {
  if (query.rootProjectId) loadTree()
})
</script>

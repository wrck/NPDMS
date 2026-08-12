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
        <el-button
          type="primary"
          @click="openCreateChild()"
          v-hasPermi="['pms:project-tree:query']"
          :disabled="!query.rootProjectId"
          ><Icon icon="ep:plus" />下挂子项目</el-button
        >
        <el-button
          type="warning"
          @click="openMove()"
          v-hasPermi="['pms:project-tree:move']"
          :disabled="!query.rootProjectId"
          ><Icon icon="ep:rank" />子树移动</el-button
        >
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

  <Dialog v-model="createChildVisible" title="下挂子项目" width="620px">
    <el-form ref="createChildFormRef" :model="createChildForm" :rules="createChildRules" label-width="120px">
      <el-form-item label="父项目" prop="parentId">
        <PmsEntitySelect
          v-model="createChildForm.parentId"
          :api="ProjectApi.getProjectPage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择父项目"
        />
      </el-form-item>
      <el-form-item label="项目编码" prop="code"><el-input v-model="createChildForm.code" /></el-form-item>
      <el-form-item label="项目名称" prop="name"><el-input v-model="createChildForm.name" /></el-form-item>
      <el-form-item label="客户" prop="customerId">
        <PmsEntitySelect
          v-model="createChildForm.customerId"
          :api="CustomerApi.getCustomerPage"
          :label-field="['code', 'name']"
          value-field="id"
          query-field="name"
          placeholder="请选择客户"
        />
      </el-form-item>
      <el-form-item label="排序号"><el-input-number v-model="createChildForm.sort" :min="0" controls-position="right" /></el-form-item>
      <el-form-item label="项目分类">
        <el-select v-model="createChildForm.category" class="!w-full" clearable>
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_PROJECT_CATEGORY)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="重大项目"><el-switch v-model="createChildForm.majorProjectFlag" /></el-form-item>
      <el-form-item label="项目经理">
        <PmsEntitySelect
          v-model="createChildForm.managerUserId"
          :api="UserApi.getUserPage"
          label-field="nickname"
          value-field="id"
          query-field="nickname"
          placeholder="请选择项目经理"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createChildVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveCreateChild">保存</el-button>
    </template>
  </Dialog>

  <Dialog v-model="moveVisible" title="子树移动" width="520px">
    <el-form ref="moveFormRef" :model="moveForm" :rules="moveRules" label-width="120px">
      <el-form-item label="待移动项目" prop="projectId">
        <PmsEntitySelect
          v-model="moveForm.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择待移动项目"
        />
      </el-form-item>
      <el-form-item label="目标父项目" prop="targetParentId">
        <PmsEntitySelect
          v-model="moveForm.targetParentId"
          :api="ProjectApi.getProjectPage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择目标父项目"
        />
      </el-form-item>
      <el-form-item label="变更原因"><el-input v-model="moveForm.reason" type="textarea" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="moveVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveMove">保存</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import * as ProjectTreeApi from '@/api/pms/project/project-tree'
import * as ProjectApi from '@/api/pms/project/project'
import * as CustomerApi from '@/api/pms/project/customer'
import * as UserApi from '@/api/system/user'
import ProjectTag from '@/components/ProjectTag/index.vue'
import UserTag from '@/components/UserTag/index.vue'
import type {
  ProjectTreeNodeVO,
  ProjectTreeCreateChildReqVO,
  ProjectTreeMoveReqVO
} from '@/api/pms/project/project-tree'

defineOptions({ name: 'PmsProjectTree' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const query = reactive({ rootProjectId: undefined as number | undefined })
const treeData = ref<ProjectTreeNodeVO[]>([])

const createChildVisible = ref(false)
const createChildFormRef = ref()
const createChildForm = reactive<ProjectTreeCreateChildReqVO>({
  parentId: undefined,
  code: '',
  name: '',
  customerId: undefined,
  sort: 0,
  category: '',
  majorProjectFlag: false,
  managerUserId: undefined
})
const createChildRules = {
  parentId: [{ required: true, message: '请选择父项目' }],
  code: [{ required: true, message: '请输入项目编码' }],
  name: [{ required: true, message: '请输入项目名称' }],
  customerId: [{ required: true, message: '请选择客户' }]
}

const moveVisible = ref(false)
const moveFormRef = ref()
const moveForm = reactive<ProjectTreeMoveReqVO>({
  projectId: undefined,
  targetParentId: undefined,
  reason: ''
})
const moveRules = {
  projectId: [{ required: true, message: '请选择待移动项目' }],
  targetParentId: [{ required: true, message: '请选择目标父项目' }]
}

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
const openCreateChild = () => {
  Object.assign(createChildForm, {
    parentId: query.rootProjectId,
    code: '',
    name: '',
    customerId: undefined,
    sort: 0,
    category: '',
    majorProjectFlag: false,
    managerUserId: undefined
  })
  createChildVisible.value = true
}
const saveCreateChild = async () => {
  await createChildFormRef.value.validate()
  saving.value = true
  try {
    await ProjectTreeApi.createChildProject(createChildForm)
    message.success('下挂子项目成功')
    createChildVisible.value = false
    await loadTree()
  } finally {
    saving.value = false
  }
}
const openMove = () => {
  Object.assign(moveForm, { projectId: query.rootProjectId, targetParentId: undefined, reason: '' })
  moveVisible.value = true
}
const saveMove = async () => {
  await moveFormRef.value.validate()
  saving.value = true
  try {
    await ProjectTreeApi.moveSubtree(moveForm)
    message.success('子树移动成功')
    moveVisible.value = false
    await loadTree()
  } finally {
    saving.value = false
  }
}
onMounted(() => {
  if (query.rootProjectId) loadTree()
})
</script>

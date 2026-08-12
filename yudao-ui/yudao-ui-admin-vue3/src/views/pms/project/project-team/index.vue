<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="项目" prop="projectId">
        <PmsEntitySelect
          v-model="query.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择项目"
          class="!w-220px"
        />
      </el-form-item>
      <el-form-item label="角色编码" prop="roleCode">
        <el-input v-model="query.roleCode" clearable class="!w-220px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-160px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.COMMON_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="open()" v-hasPermi="['pms:project-team:create']"
          ><Icon icon="ep:plus" />新增成员</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无团队成员数据">
      <el-table-column prop="projectId" label="项目编号" width="110">
        <template #default="{ row }">
          <ProjectTag :project-id="row.projectId" />
        </template>
      </el-table-column>
      <el-table-column prop="userId" label="用户编号" width="110">
        <template #default="{ row }">
          <UserTag :user-id="row.userId" />
        </template>
      </el-table-column>
      <el-table-column prop="roleCode" label="角色编码" min-width="140" />
      <el-table-column prop="roleName" label="角色名称" min-width="120" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.COMMON_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column prop="createTime" label="创建时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)" v-hasPermi="['pms:project-team:create']"
            >编辑</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:project-team:create']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>

  <Dialog v-model="visible" :title="form.id ? '编辑团队成员' : '新增团队成员'" width="560px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="项目" prop="projectId">
        <PmsEntitySelect
          v-model="form.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择项目"
        />
      </el-form-item>
      <el-form-item label="用户编号" prop="userId">
        <PmsEntitySelect
          v-model="form.userId"
          :api="UserApi.getUserPage"
          label-field="nickname"
          value-field="id"
          query-field="nickname"
          placeholder="请选择用户"
        />
      </el-form-item>
      <el-form-item label="角色编码" prop="roleCode"><el-input v-model="form.roleCode" /></el-form-item>
      <el-form-item label="角色名称"><el-input v-model="form.roleName" /></el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio :value="0">启用</el-radio>
          <el-radio :value="1">停用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import * as ProjectTeamApi from '@/api/pms/project/project-team'
import * as ProjectApi from '@/api/pms/project/project'
import * as UserApi from '@/api/system/user'
import ProjectTag from '@/components/ProjectTag/index.vue'
import UserTag from '@/components/UserTag/index.vue'
import type { ProjectTeamMemberVO } from '@/api/pms/project/project-team'

defineOptions({ name: 'PmsProjectTeam' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<ProjectTeamMemberVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  roleCode: '',
  status: undefined as number | undefined
})
const visible = ref(false)
const formRef = ref()
const form = reactive<ProjectTeamMemberVO>({
  roleCode: '',
  status: 0
})
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  userId: [{ required: true, message: '请输入用户编号' }],
  roleCode: [{ required: true, message: '请输入角色编码' }],
  status: [{ required: true, message: '请选择状态' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await ProjectTeamApi.getProjectTeamPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const open = (row?: ProjectTeamMemberVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: undefined,
      userId: undefined,
      roleCode: '',
      roleName: '',
      status: 0,
      remark: ''
    },
    row || {}
  )
  visible.value = true
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    form.id
      ? await ProjectTeamApi.updateProjectTeamMember(form)
      : await ProjectTeamApi.createProjectTeamMember(form)
    message.success('保存成功')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: ProjectTeamMemberVO) => {
  await message.delConfirm()
  await ProjectTeamApi.deleteProjectTeamMember(row.id!)
  message.success('删除成功')
  await load()
}
onMounted(load)
</script>

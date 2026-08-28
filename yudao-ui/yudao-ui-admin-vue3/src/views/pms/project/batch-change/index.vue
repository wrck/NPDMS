<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="批次编号" prop="batchNo">
        <el-input v-model="query.batchNo" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="源用户" prop="sourceUserId">
        <PmsEntitySelect
          v-model="query.sourceUserId"
          :api="UserApi.getUserPage"
          label-field="nickname"
          value-field="id"
          query-field="nickname"
          placeholder="请选择源用户"
          class="!w-200px"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-160px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_BATCH_CHANGE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openCreate()" v-hasPermi="['pms:team-batch-change:create']"
          ><Icon icon="ep:plus" />新建批量变更</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无批量变更数据">
      <el-table-column prop="batchNo" label="批次编号" min-width="180" />
      <el-table-column prop="sourceUserId" label="源用户编号" width="110" />
      <el-table-column prop="targetUserId" label="目标用户编号" width="110" />
      <el-table-column prop="scopeType" label="范围" width="100">
        <template #default="{ row }">
          <el-tag :type="row.scopeType === 'ALL' ? 'success' : 'primary'">
            {{ row.scopeType === 'ALL' ? '全部项目' : '指定项目' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_BATCH_CHANGE_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="totalCount" label="总数" width="80" />
      <el-table-column prop="successCount" label="成功" width="80" />
      <el-table-column prop="failureCount" label="失败" width="80" />
      <el-table-column prop="createTime" label="创建时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)" v-hasPermi="['pms:team-batch-change:query']"
            >明细</el-button
          >
          <el-button
            link
            type="success"
            @click="handleExecute(row)"
            v-hasPermi="['pms:team-batch-change:execute']"
            :disabled="row.status === 1 || row.status === 2"
            >执行</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:team-batch-change:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <!-- 新建批量变更对话框 -->
  <Dialog v-model="createVisible" title="新建批量变更" width="640px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="源用户" prop="sourceUserId">
        <PmsEntitySelect
          v-model="form.sourceUserId"
          :api="UserApi.getUserPage"
          label-field="nickname"
          value-field="id"
          query-field="nickname"
          placeholder="请选择源用户"
        />
      </el-form-item>
      <el-form-item label="目标用户" prop="targetUserId">
        <PmsEntitySelect
          v-model="form.targetUserId"
          :api="UserApi.getUserPage"
          label-field="nickname"
          value-field="id"
          query-field="nickname"
          placeholder="请选择目标用户"
        />
      </el-form-item>
      <el-form-item label="范围" prop="scopeType">
        <el-radio-group v-model="form.scopeType">
          <el-radio value="ALL">全部项目</el-radio>
          <el-radio value="SELECTED">指定项目</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="form.scopeType === 'SELECTED'" label="指定项目" prop="projectIds">
        <PmsEntitySelect
          v-model="form.projectIds"
          :api="ProjectApi.getProjectPage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择项目（可多选）"
          :multiple="true"
          class="!w-full"
        />
      </el-form-item>
      <el-form-item label="变更原因" prop="reason">
        <el-input v-model="form.reason" type="textarea" :rows="2" placeholder="请输入变更原因" />
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">创建</el-button>
    </template>
  </Dialog>

  <!-- 明细对话框 -->
  <Dialog v-model="detailVisible" title="批量变更明细" width="900px">
    <el-descriptions :column="3" border class="mb-15px">
      <el-descriptions-item label="批次编号">{{ current.batchNo }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <dict-tag :type="DICT_TYPE.PMS_BATCH_CHANGE_STATUS" :value="current.status ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="总数/成功/失败">
        {{ current.totalCount }} / {{ current.successCount }} / {{ current.failureCount }}
      </el-descriptions-item>
      <el-descriptions-item label="变更原因" :span="3">{{ current.reason || '-' }}</el-descriptions-item>
    </el-descriptions>
    <el-table :data="detailItems" max-height="420" empty-text="暂无明细">
      <el-table-column prop="projectName" label="项目名称" min-width="160" />
      <el-table-column prop="beforeRole" label="变更前角色" width="140" />
      <el-table-column prop="afterRole" label="变更后角色" width="140" />
      <el-table-column prop="status" label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'info'">
            {{ row.status === 1 ? '成功' : row.status === 2 ? '失败' : '待处理' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="errorMessage" label="失败原因" min-width="200" show-overflow-tooltip />
    </el-table>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { dateFormatter } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import * as BatchChangeApi from '@/api/pms/project/batch-change'
import * as ProjectApi from '@/api/pms/project/project'
import * as UserApi from '@/api/system/user'
import type { TeamBatchChangeItemVO, TeamBatchChangeVO } from '@/api/pms/project/batch-change'

defineOptions({ name: 'PmsTeamBatchChange' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<TeamBatchChangeVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  batchNo: '',
  sourceUserId: undefined as number | undefined,
  status: undefined as number | undefined
})
const createVisible = ref(false)
const formRef = ref()
const form = reactive<TeamBatchChangeVO>({
  sourceUserId: undefined!,
  targetUserId: undefined!,
  scopeType: 'SELECTED',
  projectIds: [],
  reason: '',
  remark: ''
})
const rules = {
  sourceUserId: [{ required: true, message: '请选择源用户' }],
  targetUserId: [{ required: true, message: '请选择目标用户' }],
  scopeType: [{ required: true, message: '请选择范围' }]
}
const detailVisible = ref(false)
const current = ref<Partial<TeamBatchChangeVO>>({})
const detailItems = ref<TeamBatchChangeItemVO[]>([])

const load = async () => {
  loading.value = true
  try {
    const data = await BatchChangeApi.getTeamBatchChangePage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openCreate = () => {
  Object.assign(form, {
    id: undefined,
    sourceUserId: undefined,
    targetUserId: undefined,
    scopeType: 'SELECTED',
    projectIds: [],
    reason: '',
    remark: ''
  })
  createVisible.value = true
}
const save = async () => {
  await formRef.value.validate()
  if (form.sourceUserId === form.targetUserId) {
    message.error('源用户与目标用户不能相同')
    return
  }
  saving.value = true
  try {
    await BatchChangeApi.createTeamBatchChange(form)
    message.success('创建成功，可点击执行开始批量变更')
    createVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const openDetail = async (row: TeamBatchChangeVO) => {
  current.value = row
  detailItems.value = await BatchChangeApi.getTeamBatchChangeItems(row.id!)
  detailVisible.value = true
}
const handleExecute = async (row: TeamBatchChangeVO) => {
  await message.confirm('确认执行该批量变更？将逐条更新团队成员。')
  const items = await BatchChangeApi.executeTeamBatchChange(row.id!)
  message.success(`执行完成：成功 ${items.filter((i) => i.status === 1).length} 条，失败 ${items.filter((i) => i.status === 2).length} 条`)
  detailItems.value = items
  current.value = row
  detailVisible.value = true
  await load()
}
const remove = async (row: TeamBatchChangeVO) => {
  await message.delConfirm()
  await BatchChangeApi.deleteTeamBatchChange(row.id!)
  message.success('删除成功')
  await load()
}
onMounted(load)
</script>

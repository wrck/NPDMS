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
      <el-form-item label="项目类型" prop="projectType">
        <el-select v-model="query.projectType" clearable class="!w-160px">
          <el-option value="DIRECT" label="直签" />
          <el-option value="INDIRECT" label="非直签" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_SCHEDULE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openCreate()" v-hasPermi="['pms:schedule-backward:create']"
          ><Icon icon="ep:plus" />新建倒排</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无工期倒排数据">
      <el-table-column prop="projectId" label="项目编号" width="100">
        <template #default="{ row }">
          <ProjectTag :project-id="row.projectId" />
        </template>
      </el-table-column>
      <el-table-column prop="targetDate" label="目标完工日期" width="130" />
      <el-table-column prop="projectType" label="项目类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.projectType === 'DIRECT' ? 'primary' : 'warning'">
            {{ row.projectType === 'DIRECT' ? '直签' : '非直签' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_SCHEDULE_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="conflictSummary" label="冲突汇总" min-width="220" show-overflow-tooltip />
      <el-table-column prop="createTime" label="创建时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)" v-hasPermi="['pms:schedule-backward:query']"
            >明细</el-button
          >
          <el-button
            link
            type="warning"
            @click="handleCalculate(row)"
            v-hasPermi="['pms:schedule-backward:calculate']"
            :disabled="row.status === 2"
            >计算</el-button
          >
          <el-button
            link
            type="success"
            @click="handleApply(row)"
            v-hasPermi="['pms:schedule-backward:apply']"
            :disabled="row.status !== 1"
            >应用</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:schedule-backward:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <!-- 新建倒排对话框 -->
  <Dialog v-model="createVisible" title="新建工期倒排" width="560px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
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
      <el-form-item label="目标完工日期" prop="targetDate">
        <el-date-picker v-model="form.targetDate" type="date" value-format="YYYY-MM-DD" placeholder="选择目标完工日期" />
      </el-form-item>
      <el-form-item label="项目类型" prop="projectType">
        <el-radio-group v-model="form.projectType">
          <el-radio value="DIRECT">直签（阶段紧凑）</el-radio>
          <el-radio value="INDIRECT">非直签（阶段间缓冲）</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">创建</el-button>
    </template>
  </Dialog>

  <!-- 明细对话框 -->
  <Dialog v-model="detailVisible" title="工期倒排阶段明细" width="960px">
    <el-descriptions :column="2" border class="mb-15px">
      <el-descriptions-item label="目标完工日期">{{ current.targetDate }}</el-descriptions-item>
      <el-descriptions-item label="项目类型">
        {{ current.projectType === 'DIRECT' ? '直签' : '非直签' }}
      </el-descriptions-item>
      <el-descriptions-item label="状态">
        <dict-tag :type="DICT_TYPE.PMS_SCHEDULE_STATUS" :value="current.status ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="冲突汇总" :span="2">
        <el-text v-if="current.conflictSummary" type="danger">{{ current.conflictSummary }}</el-text>
        <span v-else>无</span>
      </el-descriptions-item>
    </el-descriptions>
    <el-table :data="detailItems" max-height="420" empty-text="暂无阶段明细，请先计算">
      <el-table-column prop="sort" label="顺序" width="70" />
      <el-table-column prop="phaseName" label="阶段名称" min-width="140" />
      <el-table-column prop="plannedStartDate" label="计划开始" width="130" />
      <el-table-column prop="plannedEndDate" label="计划结束" width="130" />
      <el-table-column prop="recommendedLatestDate" label="建议最晚日期" width="130" />
      <el-table-column prop="hasConflict" label="冲突" width="80">
        <template #default="{ row }">
          <el-tag :type="row.hasConflict ? 'danger' : 'success'">
            {{ row.hasConflict ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="conflictReason" label="冲突原因" min-width="180" show-overflow-tooltip />
    </el-table>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { dateFormatter } from '@/utils/formatTime'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import * as ScheduleApi from '@/api/pms/project/schedule-backward'
import * as ProjectApi from '@/api/pms/project/project'
import ProjectTag from '@/components/ProjectTag/index.vue'
import type { ScheduleBackwardItemVO, ScheduleBackwardVO } from '@/api/pms/project/schedule-backward'

defineOptions({ name: 'PmsScheduleBackward' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<ScheduleBackwardVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  projectType: '',
  status: undefined as number | undefined
})
const createVisible = ref(false)
const formRef = ref()
const form = reactive<ScheduleBackwardVO>({
  projectId: undefined!,
  targetDate: '',
  projectType: 'DIRECT',
  remark: ''
})
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  targetDate: [{ required: true, message: '请选择目标完工日期' }],
  projectType: [{ required: true, message: '请选择项目类型' }]
}
const detailVisible = ref(false)
const current = ref<Partial<ScheduleBackwardVO>>({})
const detailItems = ref<ScheduleBackwardItemVO[]>([])

const load = async () => {
  loading.value = true
  try {
    const data = await ScheduleApi.getScheduleBackwardPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openCreate = () => {
  Object.assign(form, {
    id: undefined,
    projectId: undefined,
    targetDate: '',
    projectType: 'DIRECT',
    remark: ''
  })
  createVisible.value = true
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    await ScheduleApi.createScheduleBackward(form)
    message.success('创建成功，可点击计算进行工期倒排')
    createVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const openDetail = async (row: ScheduleBackwardVO) => {
  current.value = row
  detailItems.value = await ScheduleApi.getScheduleBackwardItems(row.id!)
  detailVisible.value = true
}
const handleCalculate = async (row: ScheduleBackwardVO) => {
  const items = await ScheduleApi.calculateScheduleBackward(row.id!)
  message.success('计算完成')
  detailItems.value = items
  current.value = { ...row, status: 1 }
  detailVisible.value = true
  await load()
}
const handleApply = async (row: ScheduleBackwardVO) => {
  await message.confirm('确认将倒排结果应用到项目阶段？将更新各阶段计划开始/结束时间。')
  await ScheduleApi.applyScheduleBackward(row.id!)
  message.success('应用成功')
  await load()
}
const remove = async (row: ScheduleBackwardVO) => {
  await message.delConfirm()
  await ScheduleApi.deleteScheduleBackward(row.id!)
  message.success('删除成功')
  await load()
}
onMounted(load)
</script>

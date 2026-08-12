<template>
  <div>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="项目编号" prop="projectId">
        <PmsEntitySelect
          v-model="query.projectId"
          :api="ProjectApi.getProjectPage"
          :label-field="['code', 'name']"
          value-field="id"
          query-field="name"
          placeholder="请选择项目"
          class="!w-220px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="load" v-hasPermi="['pms:project-panoramic:query']"
          ><Icon icon="ep:search" />查询全景</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap v-loading="loading">
    <el-empty v-if="!panoramic.id" description="请输入项目编号并查询全景" />
    <template v-else>
      <el-descriptions :column="3" border title="项目基本信息">
        <el-descriptions-item label="项目编号">{{ panoramic.id }}</el-descriptions-item>
        <el-descriptions-item label="项目编码">{{ panoramic.code || '-' }}</el-descriptions-item>
        <el-descriptions-item label="项目名称">{{ panoramic.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="项目分类">{{ panoramic.category || '-' }}</el-descriptions-item>
        <el-descriptions-item label="项目类型">{{ panoramic.projectType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="重大项目">
          {{ panoramic.majorProjectFlag ? '是' : '否' }}
        </el-descriptions-item>
        <el-descriptions-item label="项目经理编号"><UserTag :user-id="panoramic.managerUserId" /></el-descriptions-item>
        <el-descriptions-item label="项目状态">{{ panoramic.status ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ panoramic.createTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-descriptions :column="3" border title="客户信息" class="mt-12px">
        <el-descriptions-item label="客户编号"><CustomerTag :customer-id="panoramic.customerId" /></el-descriptions-item>
        <el-descriptions-item label="客户编码">{{ panoramic.customerCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户名称">{{ panoramic.customerName || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-row :gutter="12" class="mt-12px">
        <el-col :span="8">
          <el-card shadow="hover">
            <template #header>阶段汇总</template>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="阶段总数">{{ panoramic.phaseTotalCount ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="未开始">{{ panoramic.phaseNotStartedCount ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="进行中">{{ panoramic.phaseInProgressCount ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="已完成">{{ panoramic.phaseCompletedCount ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="已跳过">{{ panoramic.phaseSkippedCount ?? 0 }}</el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover">
            <template #header>任务汇总</template>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="任务总数">{{ panoramic.taskTotalCount ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="已完成">{{ panoramic.taskCompletedCount ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="进行中">{{ panoramic.taskInProgressCount ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="受阻">{{ panoramic.taskBlockedCount ?? 0 }}</el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover">
            <template #header>风险汇总</template>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="风险总数">{{ panoramic.riskTotalCount ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="高风险">{{ panoramic.riskHighCount ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="中风险">{{ panoramic.riskMediumCount ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="低风险">{{ panoramic.riskLowCount ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="已识别">{{ panoramic.riskIdentifiedCount ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="处理中">{{ panoramic.riskInProgressCount ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="已关闭">{{ panoramic.riskClosedCount ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="已发生">{{ panoramic.riskOccurredCount ?? 0 }}</el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="hover" class="mt-12px">
        <template #header>项目进度</template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="阶段进度">{{ progress.phaseProgress ?? 0 }}%</el-descriptions-item>
          <el-descriptions-item label="任务进度">{{ progress.taskProgress ?? 0 }}%</el-descriptions-item>
          <el-descriptions-item label="总体进度">
            <el-progress :percentage="progress.overallProgress ?? 0" :stroke-width="14" />
          </el-descriptions-item>
          <el-descriptions-item label="阶段/任务统计">
            阶段 {{ progress.phaseCompletedCount ?? 0 }}/{{ progress.phaseTotalCount ?? 0 }}，
            任务 {{ progress.taskCompletedCount ?? 0 }}/{{ progress.taskTotalCount ?? 0 }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="hover" class="mt-12px">
        <template #header>团队成员</template>
        <el-table :data="panoramic.teamMembers || []" empty-text="暂无团队成员">
          <el-table-column prop="userId" label="用户编号" width="120">
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
        </el-table>
      </el-card>
    </template>
  </ContentWrap>

  <ContentWrap>
    <template #header>
      <div class="h-3 flex justify-between">
        <span>项目列表（点击行查看全景）</span>
      </div>
    </template>
    <el-table v-loading="listLoading" :data="projectList" empty-text="暂无项目数据" @row-click="handleRowClick" highlight-current-row>
      <el-table-column prop="id" label="项目编号" width="100" />
      <el-table-column prop="code" label="项目编码" width="140" />
      <el-table-column prop="name" label="项目名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="category" label="项目分类" width="120" />
      <el-table-column prop="customerName" label="客户名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="managerUserId" label="项目经理" width="100">
        <template #default="{ row }">
          <UserTag :user-id="row.managerUserId" />
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="selectProject(row)">查看全景</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="listTotal"
      v-model:page="listQuery.pageNo"
      v-model:limit="listQuery.pageSize"
      @pagination="loadList"
    />
  </ContentWrap>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { DICT_TYPE } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import {
  getProjectPanoramic,
  getProjectProgress
} from '@/api/pms/project/project-panoramic'
import * as ProjectApi from '@/api/pms/project/project'
import UserTag from '@/components/UserTag/index.vue'
import CustomerTag from '@/components/CustomerTag/index.vue'
import type { ProjectPanoramicVO, ProjectProgressVO } from '@/api/pms/project/project-panoramic'

defineOptions({ name: 'PmsProjectPanoramic' })
const message = useMessage()
const loading = ref(false)
const listLoading = ref(false)
const query = reactive({ projectId: undefined as number | undefined })
const panoramic = reactive<ProjectPanoramicVO>({})
const progress = reactive<ProjectProgressVO>({})

// 项目列表
const projectList = ref<any[]>([])
const listTotal = ref(0)
const listQuery = reactive({ pageNo: 1, pageSize: 10 })

const loadList = async () => {
  listLoading.value = true
  try {
    const data = await ProjectApi.getProjectPage(listQuery)
    projectList.value = data.list
    listTotal.value = data.total
  } finally {
    listLoading.value = false
  }
}

const selectProject = (row: any) => {
  query.projectId = row.id
  load()
}

const handleRowClick = (row: any) => {
  selectProject(row)
}

const load = async () => {
  if (!query.projectId) {
    message.warning('请先选择项目')
    return
  }
  loading.value = true
  try {
    const data = await getProjectPanoramic(query.projectId)
    Object.assign(panoramic, data || {})
    try {
      const progressData = await getProjectProgress(query.projectId)
      Object.assign(progress, progressData || {})
    } catch (e) {
      // 进度查询失败不阻断全景展示
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadList)
</script>

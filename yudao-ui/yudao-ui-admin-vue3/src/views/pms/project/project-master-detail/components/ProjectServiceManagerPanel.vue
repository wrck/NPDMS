<template>
  <ContentWrap v-loading="loading">
    <div class="panel-header">
      <div>
        <h3 class="panel-title"><Icon icon="ep:user-filled" />服务经理责任分布</h3>
        <p class="panel-description">按当前项目树节点查看主责、协同及站点与办事处范围。</p>
      </div>
      <el-button plain :loading="loading" @click="loadRows">
        <Icon icon="ep:refresh" />刷新
      </el-button>
    </div>

    <div v-if="rows.length" class="table-scroll desktop-list">
      <el-table :data="rows" border size="small" row-key="projectId">
        <el-table-column label="项目节点" min-width="210">
          <template #default="{ row }">
            <div class="project-cell">
              <span class="project-code">{{ row.projectCode }}</span>
              <span>{{ row.projectName }}</span>
              <span class="project-depth">层级 {{ row.treeDepth }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="指派状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.assignmentStatus)" size="small">
              {{ statusLabel(row.assignmentStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="责任范围" min-width="420">
          <template #default="{ row }">
            <div v-if="row.responsibilities.length" class="responsibility-list">
              <div
                v-for="scope in row.responsibilities"
                :key="`${scope.levelCode}-${scope.siteId || 0}-${scope.departmentId}`"
                class="responsibility-item"
              >
                <div class="scope-line">
                  <el-tag size="small" effect="plain">{{ scope.levelCode }}</el-tag>
                  <span>{{ scope.siteId ? `站点 #${scope.siteId}` : '项目级' }}</span>
                  <span>{{ scope.departmentCode }} {{ scope.departmentName }}</span>
                </div>
                <div class="manager-line">
                  <span>主责：{{ scope.primaryManager?.memberName || '未指派' }}</span>
                  <span>
                    协同：{{
                      scope.collaborators.map((item) => item.memberName).join('、') || '无'
                    }}
                  </span>
                </div>
              </div>
            </div>
            <el-text v-else type="info">暂无有效责任</el-text>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="rows.length" class="mobile-list">
      <article v-for="row in rows" :key="row.projectId" class="responsibility-card">
        <header class="card-header">
          <div>
            <div class="project-code">{{ row.projectCode }}</div>
            <div class="card-title">{{ row.projectName }}</div>
          </div>
          <el-tag :type="statusType(row.assignmentStatus)" size="small">
            {{ statusLabel(row.assignmentStatus) }}
          </el-tag>
        </header>
        <div v-if="row.responsibilities.length" class="responsibility-list">
          <div
            v-for="scope in row.responsibilities"
            :key="`${scope.levelCode}-${scope.siteId || 0}-${scope.departmentId}`"
            class="responsibility-item"
          >
            <div class="scope-line">
              <el-tag size="small" effect="plain">{{ scope.levelCode }}</el-tag>
              <span>{{ scope.siteId ? `站点 #${scope.siteId}` : '项目级' }}</span>
            </div>
            <div>{{ scope.departmentCode }} {{ scope.departmentName }}</div>
            <div>主责：{{ scope.primaryManager?.memberName || '未指派' }}</div>
            <div>
              协同：{{ scope.collaborators.map((item) => item.memberName).join('、') || '无' }}
            </div>
          </div>
        </div>
        <el-text v-else type="info">暂无有效责任</el-text>
      </article>
    </div>

    <el-empty v-if="!loading && !rows.length" description="当前项目树暂无责任数据" />
    <Pagination
      v-if="total > query.pageSize"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      :total="total"
      @pagination="loadRows"
    />
  </ContentWrap>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import * as ProjectsApi from '@/api/pms/project/projects'
import type { ServiceManagerResponsibilityVO } from '@/api/pms/project/projects'

defineOptions({ name: 'ProjectServiceManagerPanel' })

const props = defineProps<{ projectId: number }>()

const loading = ref(false)
const rows = ref<ServiceManagerResponsibilityVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10 })

const STATUS_LABELS: Record<string, string> = {
  ASSIGNED: '已指派',
  PARTIAL: '部分指派',
  UNASSIGNED: '未指派'
}

const statusLabel = (status: string) => STATUS_LABELS[status] || status

const statusType = (status: string): 'success' | 'warning' | 'info' => {
  if (status === 'ASSIGNED') return 'success'
  if (status === 'PARTIAL') return 'warning'
  return 'info'
}

const loadRows = async () => {
  loading.value = true
  try {
    const page = await ProjectsApi.getServiceManagerResponsibilities(props.projectId, query)
    rows.value = page.list || []
    total.value = page.total || 0
  } finally {
    loading.value = false
  }
}

onMounted(loadRows)
</script>

<style lang="scss" scoped>
.panel-header {
  display: flex;
  padding-bottom: 8px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.panel-title {
  display: flex;
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  align-items: center;
  gap: 6px;
}

.panel-description {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.table-scroll {
  overflow-x: auto;
}

.project-cell,
.responsibility-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.project-code,
.project-depth {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.responsibility-item {
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);

  &:last-child {
    border-bottom: 0;
  }
}

.scope-line,
.manager-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.manager-line {
  margin-top: 4px;
  color: var(--el-text-color-regular);
}

.mobile-list {
  display: none;
}

@media (width <= 1199px) {
  .desktop-list {
    display: none;
  }

  .mobile-list {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .responsibility-card {
    padding: 12px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--el-border-radius-base);
  }

  .card-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 8px;
  }

  .card-title {
    font-weight: 600;
    color: var(--el-text-color-primary);
  }
}

@media (width <= 767px) {
  .panel-header {
    flex-direction: column;
  }

  .panel-header .el-button,
  .mobile-list {
    width: 100%;
  }

  .mobile-list {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>

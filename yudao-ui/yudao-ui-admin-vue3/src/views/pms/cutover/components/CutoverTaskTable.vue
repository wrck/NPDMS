<template>
  <el-table v-loading="loading" :data="rows" row-key="id" :empty-text="error ? '任务列表未加载成功' : '暂无符合条件的割接任务'" @row-dblclick="emit('open', $event)">
    <el-table-column prop="taskNo" label="任务编号" min-width="165" />
    <el-table-column prop="taskName" label="任务名称" min-width="200" show-overflow-tooltip />
    <el-table-column v-if="showProject" prop="projectName" label="项目" min-width="210" show-overflow-tooltip />
    <el-table-column label="来源" width="105"><template #default="{ row }">{{ sourceLabels[row.intakeSourceType] || row.intakeSourceType }}</template></el-table-column>
    <el-table-column label="等级" width="85"><template #default="{ row }">{{ row.manualGrade || '待确认' }}</template></el-table-column>
    <el-table-column label="当前阶段" min-width="160"><template #default="{ row }">{{ stageLabels[row.currentStage] || '—' }}</template></el-table-column>
    <el-table-column label="状态" min-width="120"><template #default="{ row }"><el-tag :type="row.taskStatus === 'ARCHIVED' ? 'success' : 'info'">{{ statusLabels[row.taskStatus] || row.taskStatus }}</el-tag></template></el-table-column>
    <el-table-column label="生成时间" min-width="170"><template #default="{ row }">{{ formatWireDateTime(row.generatedAt) }}</template></el-table-column>
    <el-table-column label="操作" width="90" fixed="right"><template #default="{ row }"><el-button link type="primary" data-testid="open-cutover-task" @click="emit('open', row)">详情</el-button></template></el-table-column>
  </el-table>
</template>

<script setup lang="ts">
import type { CutoverTaskListItem } from '@/api/pms/cutover'
import { formatWireDateTime } from '../shared/cutoverTaskInteraction'
import { sourceLabels, stageLabels, statusLabels } from '../shared/taskPresentation'

withDefaults(defineProps<{ rows: CutoverTaskListItem[]; loading?: boolean; error?: string | null; showProject?: boolean }>(), { showProject: true })
const emit = defineEmits<{ open: [task: CutoverTaskListItem] }>()
</script>

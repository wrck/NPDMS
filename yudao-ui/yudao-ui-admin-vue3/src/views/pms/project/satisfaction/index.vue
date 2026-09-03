<template>
  <ContentWrap>
    <header class="page-heading">
      <div>
        <h1>满意度管理</h1>
        <p>配置问卷、推进采集任务，并查看不可变判定与归档结果。</p>
      </div>
    </header>
    <el-tabs v-model="activeTab" class="workbench-tabs">
      <el-tab-pane label="问卷模板" name="templates"><TemplatePanel /></el-tab-pane>
      <el-tab-pane label="采集任务" name="tasks"><TaskPanel /></el-tab-pane>
      <el-tab-pane label="判定结果" name="results"><ResultPanel /></el-tab-pane>
    </el-tabs>
  </ContentWrap>
</template>

<script setup lang="ts">
import TemplatePanel from './TemplatePanel.vue'
import TaskPanel from './TaskPanel.vue'
import ResultPanel from './ResultPanel.vue'

defineOptions({ name: 'PmsSatisfactionWorkbench' })
const activeTab = ref('tasks')
</script>

<style scoped lang="scss">
.page-heading h1 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 24px;
}
.page-heading p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
}
.workbench-tabs {
  margin-top: 16px;
}
</style>

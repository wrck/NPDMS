<template>
  <section class="umc-rule-detail" aria-label="UMC执行结果明细">
    <dl class="rule-facts">
      <div v-if="showIdentity"
        ><dt>UMC规则ID</dt><dd>{{ rule.ruleid }}</dd></div
      >
      <div
        ><dt>设备支持</dt
        ><dd>{{ rule.support == null ? '未提供' : rule.support ? '支持' : '不支持' }}</dd></div
      >
      <div
        ><dt>执行错误</dt><dd>{{ rule.error || '无' }}</dd></div
      >
      <div
        ><dt>UMC结论</dt><dd>{{ conclusion(rule.result) }}</dd></div
      >
    </dl>
    <p v-if="showCommands && rule.cmd"><strong>执行命令：</strong>{{ rule.cmd }}</p>
    <p v-if="rule.info"><strong>判定说明：</strong>{{ rule.info }}</p>
    <p v-if="rule.suggest"><strong>处理建议：</strong>{{ rule.suggest }}</p>
    <el-collapse v-if="showCommands && rule.output != null" class="command-output">
      <el-collapse-item title="查看完整命令输出" name="output">
        <pre>{{ rule.output || '（空输出）' }}</pre>
      </el-collapse-item>
    </el-collapse>
    <p v-if="!rule.targetlist?.length">本规则未返回指标。</p>
    <table v-if="rule.targetlist?.length" class="metric-table" aria-label="全部巡检指标及对象值">
      <thead
        ><tr
          ><th scope="col">指标 / 指标结论</th><th scope="col">对象</th><th scope="col">指标值</th
          ><th scope="col">对象结论</th></tr
        ></thead
      >
      <tbody v-for="target in rule.targetlist" :key="target.id" class="metric">
        <tr
          v-for="(value, index) in target.valuelist?.length ? target.valuelist : [null]"
          :key="value?.id || 'empty'"
        >
          <th v-if="index === 0" scope="rowgroup" :rowspan="target.valuelist?.length || 1">
            {{ target.name }}<span class="target-conclusion">{{ conclusion(target.result) }}</span>
            <p v-if="target.info">{{ target.info }}</p>
            <p v-if="target.error">执行错误：{{ target.error }}</p>
          </th>
          <td>{{ value?.object || '—' }}</td>
          <td>{{
            !value
              ? '本指标未返回对象值'
              : value.value === ''
                ? '（空值）'
                : (value.value ?? '未提供')
          }}</td>
          <td>{{ value ? conclusion(value.result) : '未判定' }}</td>
        </tr>
      </tbody>
    </table>
  </section>
</template>
<script setup lang="ts">
import type { UmcRule } from '@/api/pms/inspection/reportResults'
withDefaults(defineProps<{ rule: UmcRule; showCommands?: boolean; showIdentity?: boolean }>(), {
  showCommands: true,
  showIdentity: true
})
const conclusion = (value?: boolean) =>
  value === true ? '异常' : value === false ? '正常' : '未判定'
</script>
<style scoped>
.umc-rule-detail {
  min-width: 0;
  padding: 12px 16px;
  overflow-wrap: anywhere;
}
.rule-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
  margin: 0;
}
.rule-facts div {
  display: flex;
  gap: 8px;
}
.rule-facts dt {
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
}
.rule-facts dd {
  margin: 0;
}
.metric-table {
  width: 100%;
  margin-top: 12px;
  border-collapse: collapse;
  table-layout: fixed;
}
.metric-table th,
.metric-table td {
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  text-align: left;
  vertical-align: top;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-weight: normal;
}
.metric-table thead {
  background: var(--el-fill-color-light);
}
.metric-table th:first-child {
  width: 30%;
}
.metric-table th:last-child {
  width: 14%;
}
.metric-table p {
  margin: 6px 0 0;
}
.target-conclusion {
  display: block;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
}
@media (max-width: 767px) {
  .rule-facts {
    grid-template-columns: minmax(0, 1fr);
  }
  .metric-table th,
  .metric-table td {
    padding: 8px 4px;
  }
}
.command-output pre {
  max-height: 360px;
  overflow: auto;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>

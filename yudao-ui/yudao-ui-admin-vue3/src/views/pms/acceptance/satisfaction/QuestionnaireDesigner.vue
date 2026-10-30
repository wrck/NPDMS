<template>
  <section class="designer" aria-label="问卷可视化配置">
    <h3>问卷题目</h3>
    <el-empty v-if="!model.questions.length" description="添加评分题、单选题、多选题或意见题" :image-size="60" />
    <section v-for="(q, index) in model.questions" :key="index" class="question-card">
      <header><strong>第 {{ index + 1 }} 题</strong><div>
        <el-button :disabled="index === 0" @click="move(index, -1)">上移</el-button>
        <el-button :disabled="index === model.questions.length - 1" @click="move(index, 1)">下移</el-button>
        <el-button type="danger" plain @click="model.questions.splice(index, 1)">删除题目</el-button>
      </div></header>
      <div class="fields">
        <el-form-item label="题目编码" required><el-input v-model="q.code" /></el-form-item>
        <el-form-item label="题型"><el-select v-model="q.type">
          <el-option label="评分题" value="RATING" /><el-option label="单选题" value="SINGLE_CHOICE" />
          <el-option label="多选题" value="MULTIPLE_CHOICE" /><el-option label="文字意见" value="TEXT" />
        </el-select></el-form-item>
      </div>
      <el-form-item label="题目标题" required><el-input v-model="q.title" placeholder="请输入客户看到的问题" /></el-form-item>
      <el-form-item label="是否必填"><el-switch v-model="q.required" /></el-form-item>
      <template v-if="q.type !== 'TEXT'">
        <div v-for="(option, optionIndex) in q.options" :key="optionIndex" class="option-fields">
          <el-form-item :label="`选项${optionIndex + 1}编码`"><el-input v-model="option.code" /></el-form-item>
          <el-form-item label="选项内容"><el-input v-model="option.label" /></el-form-item>
          <el-form-item label="分值"><el-input v-model="option.score" inputmode="decimal" /></el-form-item>
          <el-button type="danger" link @click="q.options.splice(optionIndex, 1)">删除选项</el-button>
        </div>
        <el-button @click="addOption(q)">添加选项</el-button>
        <div v-if="q.type === 'MULTIPLE_CHOICE'" class="fields">
          <el-form-item label="最少选择"><el-input-number v-model="q.minSelections" :min="1" :precision="0" /></el-form-item>
          <el-form-item label="最多选择"><el-input-number v-model="q.maxSelections" :min="1" :precision="0" /></el-form-item>
        </div>
        <el-form-item v-if="model.strategy === 'WEIGHTED_AVERAGE_V1'" label="题目权重"><el-input v-model="q.weight" inputmode="decimal" /></el-form-item>
      </template>
      <div v-else class="fields">
        <el-form-item label="最少字数"><el-input-number v-model="q.minLength" :min="0" :precision="0" /></el-form-item>
        <el-form-item label="最多字数"><el-input-number v-model="q.maxLength" :min="0" :precision="0" /></el-form-item>
      </div>
    </section>
    <el-button type="primary" plain @click="addQuestion">添加题目</el-button>
    <h3>评分规则</h3>
    <div class="fields">
      <el-form-item label="计分方式"><el-select v-model="model.strategy">
        <el-option label="各题分数求和" value="SUM_V1" /><el-option label="按权重计算平均分" value="WEIGHTED_AVERAGE_V1" />
      </el-select></el-form-item>
      <el-form-item label="得分小数位数"><el-input-number v-model="model.precision" :min="0" :max="2" :precision="0" /></el-form-item>
      <el-form-item label="舍入方式"><el-select v-model="model.roundingMode">
        <el-option label="四舍五入" value="HALF_UP" /><el-option label="四舍六入五成双" value="HALF_EVEN" /><el-option label="直接舍去" value="DOWN" />
      </el-select></el-form-item>
    </div>
    <p>文字意见不计分；多选题取所选选项的平均分。满分根据题目分值和权重自动计算。</p>
  </section>
</template>
<script setup lang="ts">
import { newQuestion, type DesignerModel, type DesignerQuestion } from './questionnaireDesigner'
const model = defineModel<DesignerModel>({ required: true })
const addQuestion = () => {
  let n = 1
  while (model.value.questions.some(q => q.code === `Q${n}`)) n++
  model.value.questions.push(newQuestion(`Q${n}`))
}
const addOption = (question: DesignerQuestion) => {
  let n = 1
  while (question.options.some(o => o.code === `O${n}`)) n++
  question.options.push({ code: `O${n}`, label: '', score: '0.00' })
}
const move = (index: number, offset: number) => {
  const [question] = model.value.questions.splice(index, 1)
  model.value.questions.splice(index + offset, 0, question)
}
</script>
<style scoped>
.question-card { padding: 16px; border: 1px solid var(--el-border-color); border-radius: 4px; margin: 16px 0; }
header { display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; }
.fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 16px; }
.option-fields { display: grid; grid-template-columns: 1fr 2fr 1fr auto; align-items: center; gap: 12px; }
@media (width < 640px) { .fields, .option-fields { grid-template-columns: 1fr; } }
</style>

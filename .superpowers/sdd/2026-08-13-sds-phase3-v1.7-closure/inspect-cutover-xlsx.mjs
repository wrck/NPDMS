import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const source = "需求/售后平台（割接+巡检）/1、割接平台/4、割接平台设计-数据整理（0807).xlsx";
const workbook = await SpreadsheetFile.importXlsx(await FileBlob.load(source));

for (const [sheetId, range] of [
  ["整体流程及说明", "A1:B10"],
  ["2.割接等级确认", "A27:J37"],
  ["4.割接方案", "A18:G30"],
  ["5.割接审批", "A64:H71"],
  ["6.割接跟踪与闭环", "A6:I25"],
]) {
  console.log(`\n=== ${sheetId}!${range} ===`);
  console.log((await workbook.inspect({
    kind: "region",
    sheetId,
    range,
    maxChars: 12000,
    tableMaxRows: 40,
    tableMaxCols: 12,
    tableMaxCellChars: 500,
  })).ndjson);
}

const checklistSheet = workbook.worksheets.getItem("3.2双机部署规范性检查表");
const rows = checklistSheet.getUsedRange().values;
const groups = [];
for (let index = 0; index < rows.length; index += 1) {
  const title = rows[index]?.[0];
  if (typeof title !== "string" || !title.endsWith("部署规范性检查表")) continue;
  let count = 0;
  for (let cursor = index + 2; cursor < rows.length; cursor += 1) {
    const label = rows[cursor]?.[0];
    if (label === "处理逻辑" || (typeof label === "string" && label.endsWith("部署规范性检查表"))) break;
    if (label) count += 1;
  }
  groups.push({ title, count, headingRow: index + 1 });
}
console.log("\n=== 双机规范有效检查项统计 ===");
console.log(JSON.stringify({ usedRows: rows.length, groups, total: groups.reduce((sum, group) => sum + group.count, 0) }, null, 2));

console.log("\n=== 保障语义匹配 ===");
console.log((await workbook.inspect({
  kind: "match",
  searchTerm: "保障任务|保障人员|派单|挂起|转单|接管",
  options: { useRegex: true, maxResults: 50 },
  maxChars: 12000,
})).ndjson);

'use strict';
const {classify}=require('./pr-risk.cjs');
function summarize(files, threads) {
  const risk=classify(files);
  const open=(threads||[]).filter(t=>!t.isResolved && !t.isOutdated);
  const high=open.filter(t=>(t.comments?.nodes||[]).some(c=>/\bP[01]\b/.test(c.body||'')));
  return {risk,open:open.length,high:high.length,needsHuman:risk.level==='high'||open.length>0};
}
function markdown(result) {
  const level={low:'低',medium:'中',high:'高'}[result.risk.level];
  return ['## PR 审查概览','',`- 变更风险：**${level}**`,`- 未解决的有效行级审查讨论：**${result.open}**`,`- 其中含 P0/P1 标记：**${result.high}**`,`- 建议：**${result.needsHuman?'人工复核':'继续按现有分支保护规则审核'}**`,'','> 本报告仅汇总 GitHub 已有审查讨论，不代表 Codex 已完成本次审查，也不会自动批准或合并。',''].join('\n');
}
module.exports={summarize,markdown};

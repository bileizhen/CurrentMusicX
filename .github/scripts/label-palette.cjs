'use strict';

// CurrentMusicX · Aurora / 曜光配色
// Hex without #, as required by GitHub's label API.
const STATUS_LABELS = Object.freeze({
  'status: investigating': {color: '9987C5', description: '正在复现问题或排查原因'},
  'status: in progress': {color: '5998CD', description: '已开始处理，正在实现修复或改进'},
  'status: blocked': {color: 'D79B63', description: '等待依赖或前置条件，暂时无法继续处理'},
  'status: needs verification': {color: '65AD9B', description: '修复已就绪，等待复测确认结果'},
});
const COLORS = Object.freeze({
  'risk: low': '3AAE95',
  'risk: medium': 'D5A34F',
  'risk: high': 'D86870',
  bug: 'D87582',
  fix: 'D87582',
  enhancement: '659ED5',
  feature: '659ED5',
  documentation: '8E91D3',
  maintenance: '8995A9',
  chore: '8995A9',
  ci: '8995A9',
  build: '8995A9',
  refactor: '8995A9',
  test: '8995A9',
  performance: 'AD86D1',
  perf: 'AD86D1',
  dependencies: '93AB79',
  breaking: 'CF626C',
  'breaking-change': 'CF626C',
  'module: playback': '907DCC',
  'module: lyrics': 'CE87B3',
  'module: network': '5EA8B8',
  'module: appearance': 'AB8ACA',
  'module: storage': 'BF9674',
  'module: general': '8B9CAE',
  'needs-info': 'D3A856',
  'needs follow-up': 'B0AE73',
  stale: '8C96A5',
  'bot: skip': '768292',
  'bot: auto-closed': '929CAB',
  'scope too large': 'D89465',
  announcement: '589FC5',
  'good first issue': '69B99D',
  'help wanted': '73B6C2',
  duplicate: '98A1AD',
  invalid: '969AA6',
  question: '9389D0',
  wontfix: '8D96A3',
  'wont-fix': '8D96A3',
  security: 'D26873',
  'priority: high': 'D26873',
  'priority: medium': 'D5A34F',
  'priority: low': '3AAE95',
  'provider-request': '77A6C0',
  'ignore-for-release': '8A93A3'
});
const FALLBACK = Object.freeze(['8194B0','8AA7A8','A196BC','A8A27F','B49BA4','90A1B7']);

/** Deterministic fallback keeps unknown/external labels visually cohesive. */
function labelColor(name) {
  if (typeof name !== 'string' || !name.trim()) throw new Error('Label name must be nonempty');
  const key=name.trim().toLowerCase();
  if (Object.hasOwn(STATUS_LABELS,key)) return STATUS_LABELS[key].color;
  if (Object.hasOwn(COLORS,key)) return COLORS[key];
  if (key.startsWith('risk:')) return 'B67E81';
  if (key.startsWith('module:')) return '8C9BBB';
  if (key.startsWith('priority:')) return 'A78E77';
  let hash=0;
  for (const char of key) hash=(hash*31+char.codePointAt(0))>>>0;
  return FALLBACK[hash%FALLBACK.length];
}
function colorChanges(labels) {
  if (!Array.isArray(labels)) throw new Error('Expected array of GitHub labels');
  return labels.filter(item=>item && typeof item.name==='string' &&
    String(item.color||'').toLowerCase()!==labelColor(item.name).toLowerCase())
    .map(item=>({name:item.name,before:item.color,after:labelColor(item.name)}));
}
module.exports={COLORS,STATUS_LABELS,labelColor,colorChanges};

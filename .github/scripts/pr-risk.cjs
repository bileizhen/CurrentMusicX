'use strict';
const { isInternalCiPath } = require('./android-ci-scope.cjs');

// Path-based triage is a review priority hint, not a security verdict.
const HIGH = [
  /^\.github\/workflows\/(?:android|ci-distribution|release-draft|pr-risk|triage|update-pr-branches)\.ya?ml$/i,
  /^\.github\/scripts\/(?:ci-release|release-draft|pr-risk|update-pr-branches)\.cjs$/i,
  /(?:^|\/)(?:signing|keystore|crypto|security|auth|migration)(?:\/|\.|-|$)/i,
  /^app\/src\/main\/AndroidManifest\.xml$/i,
  /(?:^|\/)(?:AudioTrack|AudioSink|AudioProcessor|PcmProcessor|BitPerfect|AudioEngine|AudioDecoder)\.(?:kt|java|cpp|c)$/i,
  /(?:^|\/)(?:gradle\.properties|settings\.gradle\.kts)$/i,
];
const MEDIUM = [
  /^app\/src\//i,
  /\.gradle(?:\.kts)?$/i,
  /^gradle\//i,
  /^\.github\/(?:workflows|scripts)\//i,
  /(?:^|\/)(?:network|api|repository|storage|update|download|database|permissions?|audio|player|playback)(?:\/|\.|$)/i,
];
function classify(files) {
  if (!Array.isArray(files) || !files.length) return {level:'high',reason:'无法可靠获取变更文件'};
  if (files.length > 100) return {level:'high',reason:'变更文件超过 100 个，需人工审查'};
  if (files.some(f => typeof f !== 'string' && (!f || typeof f.filename !== 'string' || (f.previous_filename !== undefined && typeof f.previous_filename !== 'string'))))
    return {level:'high',reason:'变更文件信息不完整'};
  const paths=files.flatMap(f=>typeof f==='string'?[f]:[f.filename,...(f.previous_filename?[f.previous_filename]:[])]);
  const high=paths.filter(p=>HIGH.some(re=>re.test(p)));
  if (high.length) return {level:'high',reason:'敏感路径：'+high.slice(0,5).join('、')};
  const medium=paths.filter(p=>MEDIUM.some(re=>re.test(p)));
  if (medium.length) return {level:'medium',reason:'应用或自动化逻辑：'+medium.slice(0,5).join('、')};
  return {level:'low',reason:'仅包含文档或非运行时代码等低风险路径'};
}
function isInternalCiOnly(files, changedFiles) {
  return Array.isArray(files) && files.length > 0 && files.length === changedFiles &&
    files.every(file => isInternalCiPath(file?.filename) &&
      (file.previous_filename === undefined ? file.status !== 'renamed' : isInternalCiPath(file.previous_filename)));
}
module.exports={classify,isInternalCiOnly};

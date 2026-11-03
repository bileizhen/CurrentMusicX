'use strict';

const HIGH = [
  /(^|\/)(audio|player|playback|decoder|dsp|bitperfect|bit-perfect|security|auth|crypto|database|migration|permissions?)(\/|\.|$)/i,
  /(^|\/)(signing|keystore|release|ci-distribution)(\/|\.|-|$)/i,
  /(^|\/)(AndroidManifest\.xml|gradle\.properties|settings\.gradle\.kts)$/i,
  /^\.github\/workflows\//,
  /^\.github\/scripts\/(?:ci-release|release-draft|pr-risk)\./,
];
const MEDIUM = [
  /^app\/src\//, /\.gradle(?:\.kts)?$/, /^gradle\//,
  /(^|\/)(network|api|repository|storage|update|download)(\/|\.|$)/i,
];
function classify(files) {
  if (!Array.isArray(files) || files.length === 0) return {level:'high',reason:'No reliable file list'};
  if (files.length > 100) return {level:'high',reason:'Large change set'};
  const paths = files.map(f => typeof f === 'string' ? f : f.filename).filter(x=>typeof x==='string');
  if (paths.length !== files.length) return {level:'high',reason:'Incomplete file metadata'};
  const high = paths.filter(p=>HIGH.some(r=>r.test(p)));
  if (high.length) return {level:'high',reason:'Sensitive paths: '+high.slice(0,5).join(', ')};
  const medium = paths.filter(p=>MEDIUM.some(r=>r.test(p)));
  if (medium.length) return {level:'medium',reason:'Application or build changes: '+medium.slice(0,5).join(', ')};
  return {level:'low',reason:'No sensitive or application paths detected'};
}
module.exports = {classify};

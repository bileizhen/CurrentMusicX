const fs = require('node:fs');

async function generate({github, context, version, number}) {
  if (!/^\d+\.\d+\.\d+$/.test(version) || !Number.isInteger(number) || number < 1 || number > 99999 ||
      !/^[a-f0-9]{40}$/.test(context.sha)) throw new Error('Invalid build identity');
  const repo = context.repo;
  const previous = (await github.rest.repos.getLatestRelease(repo)).data;
  const comparison = (await github.rest.repos.compareCommitsWithBasehead({
    ...repo, basehead: `${previous.tag_name}...${context.sha}`, per_page: 100, page: 1,
  })).data;
  const url = `https://github.com/${repo.owner}/${repo.repo}`;
  const changes = comparison.commits.map(commit => {
    if (!/^[a-f0-9]{40}$/.test(commit.sha)) throw new Error('Invalid commit identity');
    const title = commit.commit.message.split(/\r?\n/, 1)[0].replace(/[\[\]<>]/g, '').slice(0, 200);
    return `- ${title} ([${commit.sha.slice(0, 7)}](${url}/commit/${commit.sha}))`;
  });
  const summary = changes.join('\n');
  const notes = `## 更新说明\n\n主线 CI 构建 #${number}。以下为自正式版 ${previous.tag_name} 以来的提交记录。\n\n` +
    (summary.slice(0, 10_000) || '没有新增提交。') +
    (comparison.total_commits > changes.length ? '\n\n仅列出前 100 项提交。' : '') +
    (summary.length > 10_000 ? '\n\n说明较长，完整内容请查看下方改动链接。' : '') +
    `\n\n[查看完整改动](${url}/compare/${previous.tag_name}...${context.sha})\n\nCI 构建未经正式版验收。`;
  return {version: `${version}-ci.${number}`, notes};
}

async function bundle({github, context}) {
  const gradle = fs.readFileSync('app/build.gradle.kts', 'utf8');
  const version = gradle.match(/versionName\s*=\s*"(\d+\.\d+\.\d+)"/)?.[1];
  const notes = await generate({github, context, version, number: Number(process.env.CI_BUILD_NUMBER)});
  const directory = 'app/build/generated/installedReleaseNotes';
  fs.mkdirSync(directory, {recursive: true});
  fs.writeFileSync(`${directory}/installed-release-notes.json`, JSON.stringify(notes));
}

module.exports = {generate, bundle};

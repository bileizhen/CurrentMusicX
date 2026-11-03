'use strict';

const REPO = 'bileizhen/CurrentMusicX';
const REVIEW_BOT = 'chatgpt-codex-connector[bot]';
const REQUEST_AUTHORS = new Set(['github-actions[bot]', 'bileizhen']);
const EXPIRY_MS = 6 * 60 * 60 * 1000;
const SHA_RE = /^[0-9a-f]{40}$/;

function requestBody(sha) {
  if (!SHA_RE.test(sha)) throw new Error('Invalid PR head SHA');
  return '@codex review\n\n<!-- currentmusic:codex-review-request sha=' + sha + ' -->';
}

function requestSha(comment) {
  if (!REQUEST_AUTHORS.has(comment?.user?.login)) return null;
  const m = String(comment.body || '').match(/^@codex review\r?\n\r?\n<!-- currentmusic:codex-review-request sha=([0-9a-f]{40}) -->\s*$/);
  return m?.[1] || null;
}

function reviewState(comment) {
  if (comment?.user?.login !== REVIEW_BOT) return null;
  const m = String(comment.body || '').match(/<!-- codex-review-state:v1 ([^\r\n]+) -->/);
  if (!m) return null;
  try {
    const state = JSON.parse(m[1])?.code_review;
    if (!SHA_RE.test(state?.commit_sha) || state.status !== 'completed') return null;
    return state.commit_sha;
  } catch {
    return null;
  }
}

function eligible(pr) {
  return pr?.state === 'open' && pr.draft === false &&
    pr.base?.ref === 'main' && pr.base?.repo?.full_name === REPO &&
    pr.head?.repo?.full_name && pr.head.repo.full_name !== REPO &&
    SHA_RE.test(pr.head.sha);
}

function plan(pr, comments, {allowCreate=false, now=Date.now()}={}) {
  const completed = new Set(comments.map(reviewState).filter(Boolean));
  const requests = comments.map(comment => ({comment,sha:requestSha(comment)})).filter(x => x.sha);
  const attempted = new Set(requests.map(x => x.sha));
  const toDelete = requests.filter(({comment,sha}) => {
    const created = Date.parse(comment.created_at);
    return pr.state !== 'open' || completed.has(sha) ||
      (Number.isFinite(created) && now-created >= EXPIRY_MS);
  }).map(x => x.comment.id);
  const create = !!(allowCreate && eligible(pr) &&
    !completed.has(pr.head.sha) && !attempted.has(pr.head.sha));
  return {toDelete,create,completed:completed.has(pr.head?.sha)};
}

async function reconcile({github,context,core,number,allowCreate=false,now=Date.now()}) {
  const repo=context.repo;
  const pr=(await github.rest.pulls.get({...repo,pull_number:number})).data;
  if (pr.base?.repo?.full_name !== REPO) return;
  const comments=await github.paginate(github.rest.issues.listComments,{
    ...repo,issue_number:number,per_page:100
  });
  const result=plan(pr,comments,{allowCreate,now});
  for (const comment_id of result.toDelete) {
    await github.rest.issues.deleteComment({...repo,comment_id});
    core.info('PR #' + number + ': 已清理自动审查触发评论 ' + comment_id);
  }
  if (result.create) {
    await github.rest.issues.createComment({
      ...repo,issue_number:number,body:requestBody(pr.head.sha)
    });
    core.info('PR #' + number + ': 已发送 Codex 审查请求，尚不能确认 Codex 接受机器人指令');
  } else {
    core.info('PR #' + number + ': 无需新增审查请求（已审查、已有请求或不符合条件）');
  }
}

async function run({github,context,core}) {
  if (context.repo.owner + '/' + context.repo.repo !== REPO) return;
  const event=context.eventName;
  if (event === 'issue_comment') {
    if (!context.payload.issue?.pull_request ||
        context.payload.comment?.user?.login !== REVIEW_BOT) return;
    await reconcile({github,context,core,number:context.payload.issue.number});
    return;
  }
  if (event === 'pull_request_target') {
    await reconcile({github,context,core,number:context.payload.pull_request.number,
      allowCreate:context.payload.action !== 'closed'});
    return;
  }
  if (event === 'workflow_dispatch') {
    const number=Number(context.payload.inputs?.pr_number);
    if (!Number.isSafeInteger(number) || number < 1) throw new Error('无效 PR 编号');
    await reconcile({github,context,core,number,allowCreate:true});
    return;
  }
  if (event === 'schedule' || event === 'push') {
    const prs=await github.paginate(github.rest.pulls.list,{
      ...context.repo,state:'open',base:'main',per_page:100
    });
    let count=0;
    for (const pr of prs) {
      if (!pr.head?.repo || pr.head.repo.full_name === REPO) continue;
      // Only the first install push requests existing fork PRs. Scheduled runs ONLY clean up.
      await reconcile({github,context,core,number:pr.number,
        allowCreate:event==='push' && count<10});
      count++;
    }
  }
}
module.exports={requestBody,requestSha,reviewState,eligible,plan,run,EXPIRY_MS};

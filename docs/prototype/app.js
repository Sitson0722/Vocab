/* Interaction prototype only. No FSRS implementation, external service, or Android data access. */
"use strict";
const STORE = "vocab-flow-prototype-v1";
const cards = [
  { id: "charge-fee", word: "charge", kind: "recall", dimension: "C", label: "在语境里看懂", meaning: "收取费用", phrase: "charge someone for something", ipa: "/tʃɑːrdʒ/", sentence: "The hotel charges extra for breakfast.", prompt: "这里的 charges 表达什么意思？先在心里回答。", explanation: "酒店的早餐需要额外付费。charge 在这里表示收费。", reason: "这个义项到了复习时间，先看看换个场景还能不能理解。" },
  { id: "depend-on", word: "depend", kind: "input", dimension: "P", label: "把表达想起来", meaning: "依赖；取决于", phrase: "depend on", ipa: "/dɪˈpend/", sentence: "Our plans depend ____ the weather.", prompt: "补全“我们的计划取决于天气”中的介词。", answer: "on", hint: "这个搭配与 rely on 使用相同的介词。", explanation: "depend on 表示“取决于”。这里练的是整个搭配，不只是 depend 的中文意思。", reason: "这次检查你能否补全核心搭配，目标单词本身已经给出。" },
  { id: "subtle-difference", word: "subtle", kind: "teach", dimension: null, label: "认识一个新表达", meaning: "细微而不易察觉的", phrase: "a subtle difference", ipa: "/ˈsʌtəl/", sentence: "There is a subtle difference between the two colors.", prompt: "两种颜色有细微的差别，不仔细看很难发现。", explanation: "先记住这个搭配就够了。之后会换个场景，再让你自己想起来。", reason: "这是首次教学。“看懂了”只记录学过，后面还会安排回忆。" },
  { id: "maintain-condition", word: "maintain", kind: "choice", dimension: "C", label: "确认这句话的意思", meaning: "保持；维持", phrase: "maintain a steady speed", ipa: "/meɪnˈteɪn/", sentence: "The driver maintained a steady speed on the highway.", prompt: "司机在高速公路上怎么开车？", choices: ["一直保持稳定的车速", "逐渐提高车速", "突然停下了车"], answer: 0, explanation: "maintain a steady speed，就是持续保持稳定的车速。", reason: "通过一道短选择，确认你理解句子中这个词的作用。" },
  { id: "abandon-plan", word: "abandon", kind: "input", dimension: "P", label: "把表达想起来", meaning: "放弃", phrase: "abandon a plan", ipa: "/əˈbændən/", sentence: "After several failed attempts, they decided to ____ the plan.", prompt: "用学过的表达补全：几次尝试失败后，他们决定放弃这个计划。", answer: "abandon", alternatives: ["give up", "drop"], hint: "目标表达以 a 开头，共 7 个字母。", explanation: "abandon the plan 表示放弃计划，不再继续推进。", reason: "这次隐藏目标词，看看能否根据意思主动想到英文。" }
];
const initial = () => ({ version: 1, minutes: 10, session: null, rounds: 0 });
let memoryOnly = false;
function readState() {
  try {
    const data = JSON.parse(localStorage.getItem(STORE));
    if (!data || data.version !== 1 || ![5, 10, 15].includes(data.minutes) || !Number.isInteger(data.rounds) || data.rounds < 0) return initial();
    const s = data.session;
    if (s && (!Number.isInteger(s.index) || s.index < 0 || s.index > cards.length || !Array.isArray(s.results) || s.results.length > cards.length || !["question", "revealed", "feedback"].includes(s.phase) || typeof s.hinted !== "boolean" || typeof s.revealed !== "boolean" || typeof s.uncertain !== "boolean" || typeof s.draft !== "string" || typeof s.id !== "string")) return initial();
    if (s && (s.results.some((r, i) => r.index !== i || !["GOOD", "HARD", "AGAIN", "ASSISTED", "ALTERNATIVE", "SKIP", "VOID", "TAUGHT"].includes(r.outcome)) || s.results.length !== s.index + (s.phase === "feedback" ? 1 : 0) || (s.index === cards.length && s.phase !== "question"))) return initial();
    return data;
  } catch { return initial(); }
}
let state = readState();
let view = state.session ? (state.session.index === cards.length ? "summary" : "study") : "today";
let selectedWord = "charge";
let toastTimer;
let transitionUntil = 0;
const screen = document.getElementById("screen");
const navigation = document.getElementById("navigation");
const escapeHtml = value => String(value).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const button = (text, action, style = "btn", extra = "") => `<button type="button" class="${style}" data-action="${action}" ${extra}>${text}</button>`;
function persist() {
  try { localStorage.setItem(STORE, JSON.stringify(state)); }
  catch { if (!memoryOnly) notify("浏览器未允许保存；本次仍可体验，关闭页面后进度可能丢失。"); memoryOnly = true; }
}
function notify(message) {
  const toast = document.getElementById("toast");
  toast.textContent = message; toast.hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => { toast.hidden = true; }, 4500);
}
function start() {
  if (!state.session || state.session.index === cards.length) {
    state.session = { id: String(Date.now()), index: 0, phase: "question", hinted: false, revealed: false, uncertain: false, draft: "", results: [] };
  }
  view = "study"; persist(); render();
}
function navigationHtml() {
  return [["today", "◷", "今天"], ["words", "▤", "词库"], ["mine", "○", "我的"]].map(([id, icon, label]) => `<button type="button" data-action="nav" data-view="${id}" ${view === id || (view === "word" && id === "words") ? 'aria-current="page"' : ""}><span class="nav-icon" aria-hidden="true">${icon}</span>${label}</button>`).join("");
}
function today() {
  const continuing = state.session && state.session.index < cards.length;
  const completed = state.session && state.session.index === cards.length;
  return `<div class="row topline"><h2 style="margin:0">今天</h2>${button(`每天 ${state.minutes} 分钟`, "settings", "time-button")}</div>
    <p class="kicker">一点一点，成为自己的表达</p><h2>把认识的词，<br>变成用得出的词。</h2><p class="hero-copy">先复习，再学一点新的。<br>安排已经做好，跟着眼前这一步就好。</p>
    <section class="hero"><div class="sprout" aria-hidden="true"><i class="stem"></i><i class="leaf"></i><i class="leaf second"></i></div><h3>${continuing ? "接着刚才，继续就好。" : completed ? "这一轮，已经完成。" : "今天，从一小轮开始。"}</h3><p>${continuing ? `上次停在第 ${state.session.index + 1} 步，已保留进度。` : completed ? "给记忆一点时间，也给自己一点余地。" : "先练习熟悉的表达，再认识一个新意思。"}</p>${button(continuing ? "继续学习 →" : completed ? "再体验一轮 →" : "开始今天的学习 →", "start")}</section>
    <p class="small muted center">每轮最多 5 步 · 中途也可以收工</p><div class="quiet-note"><span class="icon" aria-hidden="true">↗</span><div><strong>认识，还要能用出来</strong><p>复习中会穿插短表达。没想起来也没关系，我们会再巩固。</p></div></div>`;
}
function headerStudy() {
  const s = state.session;
  return `<div class="row session-top"><span>本轮 ${s.index + 1} / ${cards.length}</span>${button("先收工", "pause", "text-btn subtle")}</div><div class="progress" role="progressbar" aria-label="本轮已完成步骤" aria-valuenow="${s.index}" aria-valuemin="0" aria-valuemax="${cards.length}">${cards.map((_, i) => `<i class="${i < s.index ? "done" : i === s.index ? "active" : ""}"></i>`).join("")}</div>`;
}
function details(card) { return `<details class="detail"><summary>为什么这样练？</summary><p>${card.reason}</p></details>`; }
function answerCard(card) {
  return `<section class="question-card"><span class="tag">${card.kind === "teach" ? "先认识它" : "核对一下"}</span><h2 class="answer-word">${card.word}</h2><p class="pronunciation">${card.ipa}</p><p class="meaning">${card.meaning}</p><p class="phrase">${card.phrase}</p><div class="rule"></div><p class="english" style="font-size:22px">${card.kind === "input" ? card.sentence.replace("____", `<mark>${card.answer}</mark>`) : card.sentence}</p><p class="prompt">${card.kind === "teach" ? card.prompt : card.explanation}</p>${details(card)}</section>`;
}
function study() {
  const s = state.session, card = cards[s.index];
  if (!card) { view = "summary"; return summary(); }
  let html = headerStudy() + `<div class="study-label"><span>${card.label}</span><span class="tag">${card.kind === "teach" ? "新表达" : card.dimension === "C" ? "看得懂" : "用得出"}</span></div>`;
  if (s.phase === "feedback") {
    const result = s.results[s.index];
    const messages = {
      GOOD: ["这次，想起来了。", "保持这个节奏。之后还会隔一段时间，换个情境再试。"],
      HARD: ["答对了，再巩固一下。", "这次还不太确定，后续会再安排一次检查。"],
      AGAIN: ["先把这个表达补回来。", "看一眼用法就好。后面隔开再练，不需要现在反复背。"],
      ASSISTED: ["借助提示完成了。", "这次算练习。之后会再试一次不看提示的回忆。"],
      ALTERNATIVE: ["你的表达也成立。", "这句话没错；目标表达还需要另外检查一次。"],
      VOID: ["这道题先放下。", "已标记问题，本题不影响学习进度。"],
      TAUGHT: ["先认识它就够了。", "接下来会安排回忆。"],
      SKIP: ["这题先跳过。", "学习进度保持不变。"]
    };
    const message = messages[result.outcome];
    html += `<div class="feedback ${["GOOD", "TAUGHT"].includes(result.outcome) ? "" : "warm"}" role="status"><strong>${message[0]}</strong><p>${message[1]}</p></div>`;
    if (result.answer) html += `<p class="small muted">你的回答：${escapeHtml(result.answer)}</p>`;
    html += answerCard(card) + button(s.index === cards.length - 1 ? "完成这一轮" : "继续 →", "next");
    if (result.outcome !== "VOID") html += button("这道题有问题", "report", "text-btn subtle full");
    return html;
  }
  if (card.kind === "teach") return html + answerCard(card) + button("看懂了，继续 →", "teach") + button("再解释一下", "explain", "text-btn full") + (s.hinted ? '<div class="hint" role="status">想象两件很相似的衣服，仔细看才发现颜色有一点点差别。这种不明显的差别，就是 a subtle difference。</div>' : "");
  if (s.phase === "revealed") {
    if (s.hinted) return html + answerCard(card) + '<p class="helper">用过提示后，这次有没有想起来？</p><div class="grade-row">' + button("仍没想起", "grade-again", "") + button("借提示想起", "grade-assisted", "") + '</div><p class="helper">这次先练习，后面再试独立回忆。</p>';
    return html + answerCard(card) + '<p class="helper">回想看答案之前，你是哪种情况？</p><div class="grade-row">' + button("没想起", "grade-again", "") + button("费劲想起", "grade-hard", "") + button("独立想起", "grade-good", "") + '</div><p class="helper">看到答案才熟悉，就选“没想起”。</p>';
  }
  html += `<section class="question-card"><p class="english">${card.sentence}</p><p class="prompt">${card.prompt}</p>`;
  if (s.hinted) html += `<div class="hint" role="status">${card.hint || "charge 在这里和酒店额外收取的钱有关。"}<br>这次借助提示完成，之后再独立试一次。</div>`;
  if (card.kind === "choice") html += `<div class="choices">${card.choices.map((choice, i) => button(choice, "choice", "choice", `data-choice="${i}"`)).join("")}</div>`;
  html += "</section>";
  if (card.kind === "recall") html += button("核对答案", "reveal");
  if (card.kind === "input") html += `<form id="answer-form"><label class="answer-label" for="answer">${card.word === "depend" ? "填入缺少的介词" : "填入英文表达"}</label><input class="answer-input" id="answer" name="answer" autocomplete="off" autocapitalize="none" spellcheck="false" placeholder="试着想一想…" value="${escapeHtml(s.draft)}" required><button class="btn" type="submit">提交答案</button></form>`;
  if (card.kind !== "recall") html += `<button type="button" class="text-btn full" data-action="uncertain" aria-pressed="${s.uncertain}">${s.uncertain ? "已标记不确定 · 仍可以试着回答" : "有点不确定"}</button>`;
  html += `<div class="split-actions">${button("想不起来", "forgot", "text-btn subtle")}${card.kind === "choice" ? "" : button(s.hinted ? "已使用提示" : "给一点提示", "hint", "text-btn", s.hinted ? "disabled" : "")}</div><div class="split-actions">${button("跳过", "skip", "text-btn subtle")}${button("这道题有问题", "report", "text-btn subtle")}</div>`;
  return html;
}
function record(outcome, answer = "", source = "NONE") {
  const s = state.session;
  if (!s || s.index >= cards.length || s.results.length > s.index) return;
  const card = cards[s.index];
  const evidenceScope = card.kind === "teach" ? "ENCOUNTER" : card.id === "depend-on" ? "PATTERN_COMPLETION" : card.dimension === "P" ? "FORM_RECALL" : "CONTEXT_MEANING";
  s.results.push({ id: `${s.id}:${s.index}`, index: s.index, senseId: card.id, evidenceScope, outcome, answer, source, hinted: s.hinted, answerRevealed: s.revealed, uncertain: s.uncertain });
  s.phase = "feedback"; s.revealed = true; persist();
}
function next() {
  const s = state.session;
  if (s.results.length !== s.index + 1) return;
  s.index++; s.phase = "question"; s.hinted = false; s.revealed = false; s.uncertain = false; s.draft = "";
  if (s.index === cards.length) { state.rounds++; view = "summary"; }
  persist(); render();
}
function submitAnswer(answer) {
  const s = state.session, card = cards[s.index];
  if (s.phase !== "question" || card.kind !== "input") return;
  const normalized = answer.trim().toLowerCase().replace(/[.!?。！？，,]+$/u, "").replace(/\s+/g, " ");
  if (!normalized) { notify("先填入你的答案，也可以点“想不起来”。"); return; }
  const outcome = normalized === card.answer ? (s.hinted ? "ASSISTED" : s.uncertain ? "HARD" : "GOOD") : card.alternatives?.includes(normalized) ? "ALTERNATIVE" : "AGAIN";
  record(outcome, answer, "OBSERVED"); render();
}
function summary() {
  const results = state.session?.results || [];
  const practiced = results.filter(r => !["SKIP", "VOID", "TAUGHT"].includes(r.outcome)).length;
  const repair = results.filter(r => ["AGAIN", "ASSISTED", "HARD", "ALTERNATIVE"].includes(r.outcome)).length;
  const taught = results.filter(r => r.outcome === "TAUGHT").length;
  const skipped = results.filter(r => ["SKIP", "VOID"].includes(r.outcome)).length;
  return `<div class="summary-art" aria-hidden="true">✓</div><p class="kicker center">完成一小轮，就是进展</p><h2 class="center">这一轮，先到这里。</h2><p class="muted small center">练习不必一口气做很多。<br>下次再想起来，表达就会更熟一点。</p><div class="metrics"><div class="metric"><strong>${practiced}</strong><span>练习的义项</span></div><div class="metric"><strong>${taught}</strong><span>新认识的义项</span></div></div><div class="quiet-note"><span class="icon" aria-hidden="true">↻</span><div><strong>${repair ? `${repair} 个表达，再巩固一下` : "让记忆休息一下"}</strong><p>${repair ? "这轮的薄弱项已记下。后续需要隔开再试，这次不连续追着考。" : "一次顺利不等于永久记住，之后还需要间隔复习。"}${skipped ? `另有 ${skipped} 步跳过或标记问题，没有计成成功。` : ""}</p></div></div><div style="height:26px"></div>${button("今天先收工", "finish")}${button("再体验一轮", "start", "text-btn full")}<p class="helper">已完成本轮，不代表全部义项已掌握。</p>`;
}
function words() {
  return '<div class="row"><h2>词库</h2><span class="tag">已收录范围</span></div><p class="small muted">每个意思分开学，也分开记录。</p><div class="word-list">' + cards.map(c => `<button type="button" class="word" data-action="word" data-word="${c.word}"><div class="row"><strong>${c.word}</strong><span class="state-label">${c.kind === "teach" && !state.session?.results.some(r => r.senseId === c.id && r.outcome === "TAUGHT") ? "待学" : "学习中"}</span></div><p>${c.meaning}</p><div class="word-footer">${c.word === "charge" ? "已收录 2 个义项 · 1 个尚未学" : "查看用法与两项能力"} <span aria-hidden="true">↗</span></div></button>`).join("") + '</div>';
}
function wordDetail() {
  const card = cards.find(c => c.word === selectedWord);
  const result = state.session?.results.find(r => r.senseId === card.id);
  return `${button("← 返回词库", "back-words", "text-btn")}<h2 class="answer-word">${card.word}</h2><p class="pronunciation">${card.ipa}</p><section class="sense-card"><h3>01 · ${card.meaning}</h3><p class="phrase">${card.phrase}</p><div class="ability"><span>看得懂</span><span>待验证</span></div><div class="ability"><span>用得出</span><span>待验证</span></div><div class="rule"></div><p class="muted">${result ? "本轮有学习记录。还需要隔一段时间，用不同语境检查。" : "学过和真正记稳之间，还需要几次间隔回忆。"}</p><p class="small muted">这里展示的是演示义项，不是真实掌握评估。</p></section>${card.word === "charge" ? '<section class="sense-card"><div class="row"><h3>02 · 指控</h3><span class="tag">待学</span></div><p class="phrase">charge someone with a crime</p><p class="muted">和“收费”分开记录，后续再逐步学习。</p></section>' : ""}<div class="quiet-note"><div><strong>每一个意思，都有自己的进度</strong><p>认识一个常用意思，不代表这个词的其他意思都学会了。</p></div></div>`;
}
function mine() {
  return `<h2>我的</h2><p class="small muted">让学习配合你的生活节奏。</p><section class="setting-card"><h3>每天留一点时间</h3><p class="small muted">默认安排好复习和新学，不用自己配比。</p><div class="segmented" role="group" aria-label="每日学习时间">${[5, 10, 15].map(n => button(`${n} 分钟`, "minutes", "", `data-minutes="${n}" aria-pressed="${n === state.minutes}"`)).join("")}</div><p class="small muted" style="margin:10px 0 0">原型中保存偏好；演示轮固定为 5 步。</p></section><section class="setting-card"><h3>学习记录</h3><div class="ability"><span>已完成的演示轮</span><span>${state.rounds} 轮</span></div><p class="small muted">练习次数与掌握程度分开看。延迟检查通过后，才能知道记得是否稳定。</p></section><details class="detail"><summary>更多设置</summary><p>正式应用在这里提供内容导入、AI 服务和预算、备份恢复。原型不连接这些服务。</p>${button("重置演示进度", "reset", "btn secondary")}</details>`;
}
function render() {
  const pages = { today, study, summary, words, word: wordDetail, mine };
  screen.innerHTML = pages[view]();
  navigation.hidden = ["study", "summary"].includes(view);
  navigation.innerHTML = navigationHtml();
  screen.focus({ preventScroll: true });
}
document.addEventListener("input", event => {
  if (event.target.id === "answer" && view === "study") { state.session.draft = event.target.value; persist(); }
});
document.addEventListener("submit", event => {
  if (event.target.id !== "answer-form") return;
  event.preventDefault();
  if (Date.now() < transitionUntil) return;
  transitionUntil = Date.now() + 250;
  submitAnswer(document.getElementById("answer").value);
});
document.addEventListener("click", event => {
  const target = event.target.closest("button[data-action]");
  if (!target || !document.contains(target) || target.disabled) return;
  const action = target.dataset.action;
  const advances = ["start", "next", "teach", "skip", "choice", "grade-again", "grade-hard", "grade-good", "grade-assisted", "forgot", "reveal"];
  if (advances.includes(action)) { if (Date.now() < transitionUntil) return; transitionUntil = Date.now() + 250; }
  const s = state.session, card = s && cards[s.index];
  switch (action) {
    case "nav": view = target.dataset.view; break;
    case "settings": view = "mine"; break;
    case "start": start(); return;
    case "pause": view = "today"; persist(); notify("已保存，下次接着这一题。 "); break;
    case "finish": view = "today"; break;
    case "reveal": s.revealed = true; s.phase = "revealed"; persist(); break;
    case "grade-good": record("GOOD", "", "SELF_REPORTED"); next(); return;
    case "grade-hard": record("HARD", "", "SELF_REPORTED"); next(); return;
    case "grade-assisted": record("ASSISTED", "", "SELF_REPORTED"); break;
    case "grade-again": case "forgot": record("AGAIN", "", card.kind === "recall" ? "SELF_REPORTED" : "USER_FORGOT"); break;
    case "hint": s.hinted = true; persist(); break;
    case "uncertain": s.uncertain = !s.uncertain; persist(); break;
    case "explain": s.hinted = true; persist(); break;
    case "teach": record("TAUGHT"); next(); return;
    case "skip": record("SKIP"); next(); return;
    case "choice": record(Number(target.dataset.choice) === card.answer ? (s.uncertain ? "HARD" : "GOOD") : "AGAIN", card.choices[Number(target.dataset.choice)], "OBSERVED"); break;
    case "next": next(); return;
    case "report": if (s.phase === "feedback") { s.results[s.index].outcome = "VOID"; persist(); } else record("VOID"); break;
    case "word": selectedWord = target.dataset.word; view = "word"; break;
    case "back-words": view = "words"; break;
    case "minutes": state.minutes = Number(target.dataset.minutes); persist(); notify(`已保存每天 ${state.minutes} 分钟的偏好。`); break;
    case "reset": state = initial(); persist(); view = "today"; notify("演示已重置。 "); break;
  }
  render();
});
render();

---
type: project-status-page
status: active
phase: project-command-center
source_project_file: PROJECT.md
detail_page_role: collaboration-status
last_updated: 2026-04-18
obsidianUIMode: preview
cssclasses:
  - project-status-page
  - wide-page
---
```dataviewjs
const { Notice } = obsidian;
const _fs = require('fs');
const _os = require('os');
const _path = require('path');
const { spawn, execFileSync } = require('child_process');

const basePath = app.vault.adapter.basePath.replace(/\\/g, '/');
const activeFile = app.workspace.getActiveFile();
const currentNotePath = activeFile?.path || dv.current()?.file?.path || 'Status.md';
const sourceNote = currentNotePath;
const projectFolder = currentNotePath.includes('/') ? currentNotePath.slice(0, currentNotePath.lastIndexOf('/')) : '';
const projectLeaf = projectFolder ? projectFolder.split('/').pop() : 'Project';
const sourceDoc = activeFile?.name || dv.current()?.file?.name || 'Status.md';
const projectDir = projectFolder
  ? _path.join(basePath, ...projectFolder.split('/'))
  : basePath;
const cicRoot = basePath + '/Claude-Impediment-Correction-v2';
const storageKey = 'nx::project-command-center::' + String(projectFolder || projectLeaf || 'project')
  .toLowerCase()
  .replace(/[^a-z0-9]+/g, '-')
  .replace(/^-+|-+$/g, '');
const feedbackLogFile = _path.join(projectDir, 'project-feedback-log.json');

window._db = window._db || {};
window._db.basePath = window._db.basePath || basePath;
window._db.cicRoot = window._db.cicRoot || cicRoot;
window._db._getPwsh = window._db._getPwsh || function () {
  const pwshPath = 'C:\\Program Files\\PowerShell\\7\\pwsh.exe';
  return _fs.existsSync(pwshPath) ? pwshPath : 'pwsh.exe';
};
window._db._ensureLaunchRunner = window._db._ensureLaunchRunner || function () {
  const runnerPath = _path.join(_os.tmpdir(), 'claude-launch-runner.ps1');
  const runner = [
    'param([Parameter(Mandatory=$true)][string]$PayloadPath)',
    '$ErrorActionPreference = "Stop"',
    '$payload = Get-Content -Raw -LiteralPath $PayloadPath | ConvertFrom-Json',
    'if (-not (Test-Path -LiteralPath ([string]$payload.workDir))) { throw "Directory not found: $($payload.workDir)" }',
    'if ($payload.title) { $Host.UI.RawUI.WindowTitle = [string]$payload.title }',
    'Set-Location -LiteralPath ([string]$payload.workDir)',
    '$exe = [string]$payload.executable',
    '$argList = @()',
    'if ($payload.args) { foreach ($arg in @($payload.args)) { $argList += [string]$arg } }',
    '& $exe @argList',
    '$exitCode = if ($LASTEXITCODE -ne $null) { [int]$LASTEXITCODE } else { 0 }',
    'Remove-Item -LiteralPath $PayloadPath -ErrorAction SilentlyContinue',
    'exit $exitCode'
  ].join('\r\n');
  const current = _fs.existsSync(runnerPath) ? _fs.readFileSync(runnerPath, 'utf8') : null;
  if (current !== runner) _fs.writeFileSync(runnerPath, runner, 'utf8');
  return runnerPath;
};
window._db._spawnWt = window._db._spawnWt || function (title, args, opts) {
  opts = opts || {};
  const safeTitle = title.replace(/[&|<>^"]/g, '_');
  const proc = spawn('wt.exe', ['new-tab', '--title', safeTitle, '--profile', 'Claude Session', ...args], {
    detached: true,
    stdio: 'ignore',
    shell: false
  });
  proc.on('error', (err) => {
    if (Array.isArray(opts.cleanupPaths)) {
      for (const cleanupPath of opts.cleanupPaths) {
        try { _fs.unlinkSync(cleanupPath); } catch (_e) {}
      }
    }
    new Notice('Failed to launch: ' + err.message);
  });
  proc.unref();
  new Notice('Launching ' + safeTitle + '...');
};
window._db.launchArgsTerminal = window._db.launchArgsTerminal || function (title, workDir, executable, args) {
  const winDir = workDir.replace(/\//g, '\\');
  if (!_fs.existsSync(winDir)) {
    new Notice('Error: Directory not found: ' + workDir, 8000);
    return;
  }
  const payloadPath = _path.join(_os.tmpdir(), 'claude-launch-' + Date.now() + '.json');
  _fs.writeFileSync(payloadPath, JSON.stringify({
    title,
    workDir: winDir,
    executable,
    args: Array.isArray(args) ? args.map((value) => String(value)) : []
  }), 'utf8');
  const runnerPath = window._db._ensureLaunchRunner();
  window._db._spawnWt(title, [
    '--startingDirectory', winDir,
    window._db._getPwsh(),
    '-NoLogo',
    '-NoExit',
    '-ExecutionPolicy', 'Bypass',
    '-File', runnerPath,
    payloadPath
  ], { cleanupPaths: [payloadPath] });
};
window._db.launch = window._db.launch || function (title, workDir, prompt, opts) {
  opts = opts || {};
  const args = ['--chrome', '--effort', 'max'];
  if (opts.skipPerms) args.push('--dangerously-skip-permissions');
  args.push(prompt);
  window._db.launchArgsTerminal(title, workDir, 'claude', args);
};
window._db._runNodeJson = window._db._runNodeJson || function (scriptPath, args, opts) {
  opts = opts || {};
  const argv = Array.isArray(args) ? args.map((value) => String(value)) : [];
  const stdout = execFileSync('node', [scriptPath, ...argv], {
    cwd: opts.cwd || window._db.cicRoot,
    encoding: 'utf8',
    windowsHide: true
  });
  return JSON.parse(stdout);
};
window._db.launchGoverned = window._db.launchGoverned || function (mode, opts) {
  opts = opts || {};
  const flowScript = _path.join(window._db.cicRoot, 'scripts', 'get-governed-flow-spec.mjs');
  const argv = ['--mode', mode, '--workspace-root', window._db.basePath];
  if (opts.targetRepo) argv.push('--target-repo', opts.targetRepo);
  if (opts.projectName) argv.push('--project-name', opts.projectName);
  if (opts.sourceNote) argv.push('--source-note', opts.sourceNote);
  if (opts.extraInstruction) argv.push('--extra-instruction', opts.extraInstruction);
  const spec = window._db._runNodeJson(flowScript, argv, { cwd: window._db.cicRoot });
  window._db.launch(spec.title, spec.workDir, spec.prompt, {
    skipPerms: spec.skipPerms,
    notice: opts.notice
  });
  return spec;
};

const projectFile = _path.join(projectDir, 'PROJECT.md');
const changelogFile = _path.join(projectDir, 'CHANGELOG.md');
const platformVisionFile = _path.join(projectDir, 'PLATFORM-VISION.md');
const decisionsFile = _path.join(projectDir, 'knowledge', 'DECISIONS.md');
const lessonsFile = _path.join(projectDir, 'knowledge', 'LESSONS-LEARNED.md');
const remoteOperabilityFile = _path.join(projectDir, 'documentation', '13-remote-operability.md');
const calendarMilestoneFile = _path.join(projectDir, 'documentation', '24-calendar-plan-of-day-milestone.md');
const reviewFeedbackFile = _path.join(projectDir, 'documentation', 'review', 'codex-handoff-tyler-review-feedback.md');

function formatAbsolute(date) {
  const actual = toDate(date);
  if (!actual) return 'Unknown';
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit'
  }).format(actual);
}

function toDate(value) {
  if (!value) return null;
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

function formatRelative(date) {
  const actual = toDate(date);
  if (!actual) return 'Unknown';
  const diffMs = Date.now() - actual.getTime();
  const diffMinutes = Math.max(1, Math.round(diffMs / 60000));
  if (diffMinutes < 60) return diffMinutes + 'm ago';
  const diffHours = Math.round(diffMinutes / 60);
  if (diffHours < 24) return diffHours + 'h ago';
  const diffDays = Math.round(diffHours / 24);
  return diffDays + 'd ago';
}

const FRESHNESS_PROFILES = {
  action: { freshHours: 72, agingHours: 168 },
  snapshot: { freshHours: 168, agingHours: 336 },
  reference: { freshHours: 336, agingHours: 720 }
};

function computeFreshnessStatus(date, profile = 'reference') {
  const actual = toDate(date);
  if (!actual) return 'Unavailable';
  const thresholds = FRESHNESS_PROFILES[profile] || FRESHNESS_PROFILES.reference;
  const diffHours = (Date.now() - actual.getTime()) / 3600000;
  if (diffHours <= thresholds.freshHours) return 'Fresh';
  if (diffHours <= thresholds.agingHours) return 'Aging';
  return 'Stale';
}

function emptyDraft() {
  return { approvals: '', changeRequests: '', questions: '', updatedAt: '' };
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function readFileSafe(filePath) {
  try {
    return _fs.readFileSync(filePath, 'utf8');
  } catch (_error) {
    return '';
  }
}

function getFileMtime(filePath) {
  try {
    return _fs.statSync(filePath).mtime;
  } catch (_error) {
    return null;
  }
}

function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function extractResumeField(text, label) {
  const match = String(text || '').match(new RegExp('\\*\\*' + escapeRegExp(label) + ':\\*\\*\\s*([\\s\\S]*?)(?=\\n\\*\\*[^\\n]+:\\*\\*|\\n\\*\\*Session |\\n## |$)'));
  return match ? match[1].trim() : '';
}

function extractProjectSessions(text) {
  const lines = String(text || '').split(/\r?\n/);
  const sessions = [];
  for (let index = 0; index < lines.length; index += 1) {
    const match = lines[index].match(/^\*\*Session ([0-9-]+) — (.+):\*\*$/);
    if (!match) continue;
    const session = { date: match[1], title: match[2].trim(), bullets: [] };
    index += 1;
    while (index < lines.length && !/^\*\*Session /.test(lines[index]) && !/^## /.test(lines[index])) {
      if (lines[index].startsWith('- ')) session.bullets.push(lines[index].slice(2).trim());
      index += 1;
    }
    index -= 1;
    sessions.push(session);
  }
  return sessions;
}

function shortenPhase(text) {
  const firstSentence = String(text || '').trim().split(/(?<=[.?!])\s+/)[0] || '';
  if (!firstSentence) return 'Project-page recovery and rollout prep.';
  return firstSentence.length > 108 ? firstSentence.slice(0, 105).trim() + '...' : firstSentence;
}

function clipText(text, max = 200) {
  const value = String(text || '').trim();
  if (!value) return '';
  return value.length > max ? value.slice(0, max - 3).trim() + '...' : value;
}

function firstMeaningfulParagraph(text) {
  const paragraphs = String(text || '')
    .split(/\r?\n\r?\n/)
    .map((value) => cleanInlineMarkdown(value))
    .filter(Boolean);
  return paragraphs[0] || '';
}

function firstMeaningfulLine(text) {
  return String(text || '')
    .split(/\r?\n/)
    .map((value) => value.trim())
    .find(Boolean) || '';
}

function extractListItems(text) {
  const items = [];
  for (const rawLine of String(text || '').split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line) continue;
    const checkbox = line.match(/^[-*]\s+\[( |x)\]\s+(.+)$/i);
    if (checkbox) {
      items.push(cleanInlineMarkdown(checkbox[2]));
      continue;
    }
    const bullet = line.match(/^[-*]\s+(.+)$/);
    if (bullet) {
      items.push(cleanInlineMarkdown(bullet[1]));
      continue;
    }
    const numbered = line.match(/^\d+\.\s+(.+)$/);
    if (numbered) {
      items.push(cleanInlineMarkdown(numbered[1]));
      continue;
    }
  }
  if (items.length) return items;
  const paragraph = firstMeaningfulParagraph(text);
  return paragraph ? [paragraph] : [];
}

function extractUncheckedItems(text) {
  return String(text || '')
    .split(/\r?\n/)
    .map((line) => line.trim().match(/^[-*]\s+\[\s\]\s+(.+)$/))
    .filter(Boolean)
    .map((match) => cleanInlineMarkdown(match[1]));
}

function firstSectionSummary(section) {
  if (!section || !section.body) return '';
  const items = extractListItems(section.body);
  return clipText(items[0] || firstMeaningfulParagraph(section.body), 150);
}

function findSection(sections, headings) {
  return sections.find((section) => headings.some((heading) => section.normalizedHeading === heading.toLowerCase())) || null;
}

function collectSectionItems(sections, pattern, { uncheckedOnly = false, prefixHeading = false } = {}) {
  return sections
    .filter((section) => pattern.test(section.heading))
    .flatMap((section) => {
      const items = uncheckedOnly ? extractUncheckedItems(section.body) : extractListItems(section.body);
      return items.map((item) => prefixHeading ? `${section.heading}: ${item}` : item);
    });
}

function selectFieldFacts(fields, preferredLabels, limit = 6) {
  const seen = new Set();
  const picked = [];
  for (const label of preferredLabels) {
    const match = fields.find((field) => normalizeFieldLabel(field.label) === normalizeFieldLabel(label));
    if (!match) continue;
    const key = normalizeFieldLabel(match.label);
    if (seen.has(key)) continue;
    seen.add(key);
    picked.push(match);
    if (picked.length >= limit) return picked;
  }
  for (const field of fields) {
    const key = normalizeFieldLabel(field.label);
    if (seen.has(key)) continue;
    seen.add(key);
    picked.push(field);
    if (picked.length >= limit) break;
  }
  return picked;
}

function renderFactRows(rows) {
  return rows
    .map((row) => `<div class="pcx-fact-row"><strong>${escapeHtml(row.label)}:</strong> ${escapeHtml(row.value)}</div>`)
    .join('');
}

function prettifyProjectName(name) {
  return String(name || 'Project')
    .replace(/[-_]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim() || 'Project';
}

function extractHeading(text) {
  const match = String(text || '').match(/^#\s+(.+)$/m);
  return match ? match[1].trim() : '';
}

function extractSection(text, heading) {
  const lines = String(text || '').split(/\r?\n/);
  const target = ('## ' + heading).toLowerCase();
  const start = lines.findIndex((line) => line.trim().toLowerCase() === target);
  if (start < 0) return '';
  const collected = [];
  for (let index = start + 1; index < lines.length; index += 1) {
    if (/^##\s+/.test(lines[index])) break;
    collected.push(lines[index]);
  }
  return collected.join('\n').trim();
}

function extractFrontmatter(text) {
  const value = String(text || '');
  const match = value.match(/^---\r?\n([\s\S]*?)\r?\n---/);
  if (!match) return {};
  const data = {};
  for (const line of match[1].split(/\r?\n/)) {
    const entry = line.match(/^([A-Za-z0-9_-]+):\s*(.+)$/);
    if (!entry) continue;
    data[entry[1]] = entry[2].trim().replace(/^['"]|['"]$/g, '');
  }
  return data;
}

function extractProjectSections(text) {
  const lines = String(text || '').split(/\r?\n/);
  const sections = [];
  let current = null;
  for (const line of lines) {
    const headingMatch = line.match(/^##\s+(.+)$/);
    if (headingMatch) {
      if (current) {
        current.body = current.lines.join('\n').trim();
        sections.push(current);
      }
      current = {
        heading: headingMatch[1].trim(),
        normalizedHeading: headingMatch[1].trim().toLowerCase(),
        lines: []
      };
      continue;
    }
    if (current) current.lines.push(line);
  }
  if (current) {
    current.body = current.lines.join('\n').trim();
    sections.push(current);
  }
  return sections;
}

function normalizeFieldLabel(label) {
  return String(label || '')
    .toLowerCase()
    .replace(/[`*_]/g, '')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

function cleanInlineMarkdown(text) {
  return String(text || '')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/\s+/g, ' ')
    .trim();
}

function extractLabeledFields(text) {
  const fields = [];
  let current = null;
  for (const rawLine of String(text || '').split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line) {
      if (current) {
        fields.push(current);
        current = null;
      }
      continue;
    }
    const match = line.match(/^(?:[-*]\s+)?\*\*([^*\n]+?):\*\*\s*(.*)$/)
      || line.match(/^(?:[-*]\s+)?\*\*([^*\n]+?)\*\*:\s*(.*)$/);
    if (match) {
      if (current) fields.push(current);
      current = { label: cleanInlineMarkdown(match[1]), value: cleanInlineMarkdown(match[2]) };
      continue;
    }
    if (current && !/^##\s+/.test(line) && !/^\*\*Session /.test(line)) {
      current.value = cleanInlineMarkdown(current.value + ' ' + line);
      continue;
    }
    if (current) {
      fields.push(current);
      current = null;
    }
  }
  if (current) fields.push(current);
  return fields.filter((field) => field.label && field.value);
}

function buildFieldLookup(fields) {
  const lookup = new Map();
  for (const field of fields) {
    const key = normalizeFieldLabel(field.label);
    if (key && !lookup.has(key)) lookup.set(key, field.value);
  }
  return lookup;
}

function getFieldValue(lookup, labels) {
  for (const label of labels) {
    const normalized = normalizeFieldLabel(label);
    const value = lookup.get(normalized);
    if (value) return value;
    for (const [key, entryValue] of lookup.entries()) {
      if (key.startsWith(normalized) || normalized.startsWith(key)) return entryValue;
    }
  }
  return '';
}

function uniqueStrings(items) {
  const seen = new Set();
  return items.filter((item) => {
    const value = String(item || '').trim();
    if (!value || seen.has(value)) return false;
    seen.add(value);
    return true;
  });
}

function uniqueEntries(entries) {
  const seen = new Set();
  return entries.filter((entry) => {
    if (!entry || typeof entry !== 'object') return false;
    const key = String(entry.path || '') + '::' + String(entry.label || '');
    if (!key.trim() || seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function listWithFallback(items, emptyMessage, limit = 4) {
  const values = uniqueStrings(items).slice(0, limit);
  return values.length ? values : [emptyMessage];
}

function buildContractIssueList(missingQuickFields, missingSections) {
  return uniqueStrings([
    ...missingQuickFields.map((label) => `Missing quick field: ${label}`),
    ...missingSections.map((heading) => `Missing canonical section: ${heading}`)
  ]);
}

function parseActiveComponents(section, defaultFreshness) {
  if (!section?.body) return [];
  return extractListItems(section.body)
    .map((item) => {
      const cleaned = cleanInlineMarkdown(item);
      const parts = cleaned.split(/\s+\|\s+/);
      const name = clipText((parts[0] || '').replace(/:$/, ''), 80);
      const phasePart = parts.find((part) => /^phase:/i.test(part));
      const statePart = parts.find((part) => /^(state|status):/i.test(part));
      const freshnessPart = parts.find((part) => /^freshness:/i.test(part));
      if (!name) return null;
      return {
        name,
        phase: clipText(phasePart ? phasePart.split(/:\s*/).slice(1).join(': ') : 'Not recorded.', 120),
        freshness: clipText(freshnessPart ? freshnessPart.split(/:\s*/).slice(1).join(': ') : defaultFreshness, 24),
        health: clipText(statePart ? statePart.split(/:\s*/).slice(1).join(': ') : 'Needs definition', 32)
      };
    })
    .filter(Boolean)
    .slice(0, 4);
}

function parseTimelineEntries(section) {
  if (!section?.body) return [];
  return extractListItems(section.body)
    .map((item) => {
      const cleaned = clipText(cleanInlineMarkdown(item), 220);
      const dated = cleaned.match(/^(\d{4}-\d{2}-\d{2})\s+[—-]\s+(.+)$/);
      const phase = dated ? dated[1] : 'Milestone';
      const detail = dated ? dated[2] : cleaned;
      const sentenceParts = detail.split(/(?<=[.?!])\s+/).filter(Boolean);
      const title = clipText(sentenceParts[0] || detail, 92);
      const note = clipText(sentenceParts.slice(1).join(' ') || detail, 220);
      return {
        phase,
        title,
        note
      };
    })
    .slice(0, 6);
}

function parseDependencyMap(section) {
  const fallback = {
    upstream: 'No dependency map recorded yet.',
    downstream: 'No dependency map recorded yet.',
    global: 'No dependency map recorded yet.'
  };
  if (!section?.body) return fallback;
  const items = extractListItems(section.body);
  for (const item of items) {
    const match = cleanInlineMarkdown(item).match(/^([^:]+):\s*(.+)$/);
    if (!match) continue;
    const key = match[1].trim().toLowerCase();
    const value = clipText(match[2], 220);
    if (key === 'upstream') fallback.upstream = value;
    if (key === 'downstream') fallback.downstream = value;
    if (key === 'global') fallback.global = value;
  }
  return fallback;
}

function looksClosed(text) {
  return /(closed|complete|completed|done|accepted|approved|verified|shipped|landed)/i.test(String(text || ''));
}

function looksPaused(text) {
  return /(paused|deferred|pending|blocked|waiting|stalled|hold)/i.test(String(text || ''));
}

function getFileBirthtime(filePath) {
  try {
    const stat = _fs.statSync(filePath);
    return stat.birthtime || stat.ctime || stat.mtime;
  } catch (_error) {
    return null;
  }
}

function formatDateOnly(date) {
  if (!date) return 'Unknown';
  return new Date(date).toISOString().slice(0, 10);
}

function makeProjectRelative(filePath) {
  return _path.relative(projectDir, filePath).replace(/\\/g, '/');
}

function buildSourceEntry(filePath, label, role) {
  const updatedAt = getFileMtime(filePath);
  return {
    label,
    role,
    path: makeProjectRelative(filePath),
    updatedAt,
    updatedRelative: updatedAt ? formatRelative(updatedAt) : 'Missing',
    updatedAbsolute: updatedAt ? formatAbsolute(updatedAt) : 'Not found',
    exists: Boolean(updatedAt)
  };
}

function collectMarkdownFiles(dirPath, depth = 0, found = []) {
  if (!_fs.existsSync(dirPath) || depth > 2) return found;
  for (const entry of _fs.readdirSync(dirPath, { withFileTypes: true })) {
    if (entry.name.startsWith('.')) continue;
    const fullPath = _path.join(dirPath, entry.name);
    if (entry.isDirectory()) {
      if (/^(Archive|history)$/i.test(entry.name)) continue;
      collectMarkdownFiles(fullPath, depth + 1, found);
      continue;
    }
    if (/\.md$/i.test(entry.name) && !/^Status\.md$/i.test(entry.name) && !/^PROJECT\.md$/i.test(entry.name)) {
      found.push(fullPath);
    }
  }
  return found;
}

ensureFeedbackLog();

const projectText = readFileSafe(projectFile);
const frontmatter = extractFrontmatter(projectText);
const projectSessions = extractProjectSessions(projectText);
const projectSections = extractProjectSections(projectText);
const contentSections = projectSections.filter((section) => !/^quick (resume|context)$/i.test(section.heading));
const projectLabel = prettifyProjectName(projectLeaf);
const quickResumeSection = extractSection(projectText, 'Quick Resume');
const quickContextSection = extractSection(projectText, 'Quick Context');
const activeQuickSection = quickResumeSection || quickContextSection;
const quickFields = extractLabeledFields(activeQuickSection);
const quickFieldLookup = buildFieldLookup(quickFields);
const requiredQuickLabels = [
  'What this is',
  'Current phase',
  'Current focus',
  'Next action',
  'Review state',
  'Verification',
  'Start date',
  'Last active'
];
const requiredSectionNames = [
  'Locked Truth',
  'Active Components',
  'Planning Gaps',
  'Open Decisions',
  'Recent Milestones',
  'Dependencies',
  'Reference'
];
const quickResume = {
  sourceLabel: quickResumeSection ? 'Quick Resume' : quickContextSection ? 'Quick Context' : 'Project Notes',
  whatThisIs: getFieldValue(quickFieldLookup, ['What this is'])
    || 'Project summary missing from PROJECT.md Quick Resume / Quick Context.',
  currentPhase: getFieldValue(quickFieldLookup, ['Current phase'])
    || frontmatter.phase
    || 'Current phase not recorded.',
  currentFocus: getFieldValue(quickFieldLookup, ['Current focus'])
    || 'Current focus not recorded.',
  nextAction: getFieldValue(quickFieldLookup, ['Next action']) || '',
  reviewState: getFieldValue(quickFieldLookup, ['Review state'])
    || frontmatter.status
    || 'Review state not recorded.',
  verification: getFieldValue(quickFieldLookup, ['Verification'])
    || frontmatter.lastAuditScore
    || 'No verification snapshot recorded.',
  startDate: getFieldValue(quickFieldLookup, ['Start date'])
    || formatDateOnly(getFileBirthtime(projectFile)),
  lastActive: getFieldValue(quickFieldLookup, ['Last active'])
    || frontmatter.lastActiveDate
    || frontmatter.lastAuditDate
    || projectSessions[0]?.date
    || formatDateOnly(getFileMtime(projectFile))
    || 'Unknown'
};
const missingQuickFields = requiredQuickLabels.filter((label) => {
  if (label === 'Current phase' && frontmatter.phase) return false;
  if (label === 'Review state' && frontmatter.status) return false;
  if (label === 'Verification' && frontmatter.lastAuditScore) return false;
  if (label === 'Last active' && (frontmatter.lastActiveDate || frontmatter.lastAuditDate)) return false;
  return !getFieldValue(quickFieldLookup, [label]);
});
const lockedTruthSection = findSection(contentSections, ['Locked Truth']);
const activeComponentsSection = findSection(contentSections, ['Active Components']);
const planningGapsSection = findSection(contentSections, ['Planning Gaps']);
const openDecisionsSection = findSection(contentSections, ['Open Decisions']);
const recentMilestonesSection = findSection(contentSections, ['Recent Milestones']);
const dependenciesSection = findSection(contentSections, ['Dependencies']);
const aiWatchoutsSection = findSection(contentSections, ['AI Watchouts', 'Preserve']);
const missingSections = requiredSectionNames.filter((heading) => !findSection(contentSections, [heading]));

const documentationDir = _path.join(projectDir, 'documentation');
const sourceCatalog = uniqueEntries([
  buildSourceEntry(projectFile, 'PROJECT.md', 'Canonical project command-center source'),
  buildSourceEntry(feedbackLogFile, 'project-feedback-log.json', 'Feedback absorption log'),
  buildSourceEntry(changelogFile, 'CHANGELOG.md', 'Session history'),
  buildSourceEntry(platformVisionFile, 'PLATFORM-VISION.md', 'Project direction'),
  buildSourceEntry(decisionsFile, 'knowledge/DECISIONS.md', 'Locked decisions'),
  buildSourceEntry(lessonsFile, 'knowledge/LESSONS-LEARNED.md', 'Reusable lessons'),
  ...collectMarkdownFiles(documentationDir)
    .sort((left, right) => {
      const rightTime = getFileMtime(right)?.getTime() || 0;
      const leftTime = getFileMtime(left)?.getTime() || 0;
      return rightTime - leftTime;
    })
    .slice(0, 4)
    .map((filePath) => buildSourceEntry(filePath, makeProjectRelative(filePath), 'Supporting documentation'))
]).filter((entry) => entry.exists);
const freshestSource = sourceCatalog
  .filter((entry) => entry.exists)
  .reduce((latest, entry) => {
    const latestTime = toDate(latest?.updatedAt)?.getTime() || 0;
    const entryTime = toDate(entry.updatedAt)?.getTime() || 0;
    return !latest || entryTime > latestTime ? entry : latest;
  }, null);
const projectMtime = toDate(freshestSource?.updatedAt) || new Date();
const projectStart = quickResume.startDate;
const contractIssues = buildContractIssueList(missingQuickFields, missingSections);
const planningGaps = listWithFallback(
  [
    ...contractIssues,
    ...extractListItems(planningGapsSection?.body || '')
  ],
  'No planning gaps recorded.',
  5
);
const approvedTruth = listWithFallback(
  extractListItems(lockedTruthSection?.body || ''),
  'No locked-truth section recorded yet.',
  6
);
const subsystemState = (() => {
  const parsed = parseActiveComponents(activeComponentsSection, computeFreshnessStatus(projectMtime, 'reference'));
  return parsed.length
    ? parsed
    : [
        {
          name: 'No active components recorded yet.',
          phase: 'Add an Active Components section to PROJECT.md.',
          freshness: computeFreshnessStatus(projectMtime, 'reference'),
          health: 'Needs definition'
        }
      ];
})();
const workflowHistoryBase = parseTimelineEntries(recentMilestonesSection);
const workflowHistory = (workflowHistoryBase.length
  ? workflowHistoryBase
  : [
      {
        phase: 'Milestone',
        title: 'No recent milestones recorded yet.',
        note: 'Add a Recent Milestones section to PROJECT.md so this page can show real project history.'
      }
    ]).concat([
  {
    phase: 'NOW',
    title: 'Current alignment focus',
    note: clipText(quickResume.currentFocus !== 'Current focus not recorded.' ? quickResume.currentFocus : quickResume.currentPhase, 220)
  },
  {
    phase: 'NEXT',
    title: quickResume.nextAction ? 'Next tracked move' : 'Next densification target',
    note: clipText(quickResume.nextAction || planningGaps[0], 220)
  }
]);
const completedWork = listWithFallback(
  workflowHistoryBase.map((item) => {
    const detail = item.note && item.note !== item.title ? ` — ${item.note}` : '';
    return `${item.phase}: ${item.title}${detail}`;
  }),
  'No recent milestones recorded yet.',
  4
);
const aiSuggestions = listWithFallback(
  extractListItems(aiWatchoutsSection?.body || ''),
  'No explicit preserve guidance recorded yet.',
  4
);
const dependencies = parseDependencyMap(dependenciesSection);
const openDecisions = listWithFallback(
  extractListItems(openDecisionsSection?.body || ''),
  'No open decisions recorded.',
  5
);
const resumeHighlights = [
  { label: 'What this is', value: clipText(quickResume.whatThisIs, 150) },
  { label: 'Current phase', value: clipText(quickResume.currentPhase, 150) },
  { label: 'Review', value: clipText(quickResume.reviewState, 150) },
  { label: 'Verification', value: clipText(quickResume.verification, 150) }
];
const projectClosed = looksClosed(`${quickResume.currentPhase} ${quickResume.reviewState}`);
const projectPaused = looksPaused(`${quickResume.currentPhase} ${quickResume.reviewState}`);
const nextStepTracked = Boolean(quickResume.nextAction);
const trackedWork = quickResume.nextAction || quickResume.currentFocus || quickResume.currentPhase;
const needsAttention = quickResume.nextAction
  ? {
      title: 'Approve or redirect the next move',
      subtitle: 'Next action from PROJECT.md',
      summary: quickResume.nextAction
    }
  : {
      title: 'Record the next concrete move',
      subtitle: 'PROJECT.md needs a tracked next action',
      summary: 'Add a plain-language Next action field to Quick Resume / Quick Context so this page can stop showing a placeholder.'
    };

const state = {
  projectName: projectLeaf,
  projectLabel,
  title: 'Project Command Center',
  tagline: clipText(quickResume.whatThisIs, 170),
  bannerQuote: 'This page reads from canonical quick fields and named command-center sections, not freeform PROJECT.md scraping.',
  startDate: projectStart,
  lastActivity: formatRelative(projectMtime),
  lastActivityAbsolute: formatAbsolute(projectMtime),
  primaryFocus: clipText(quickResume.currentFocus !== 'Current focus not recorded.' ? quickResume.currentFocus : quickResume.currentPhase, 92),
  primaryFocusDetail: quickResume.currentFocus !== 'Current focus not recorded.' ? quickResume.currentFocus : quickResume.currentPhase,
  engineState: 'Engine Live',
  freshness: {
    source: freshestSource ? freshestSource.label : 'PROJECT.md',
    updatedRelative: formatRelative(projectMtime),
    updatedAbsolute: formatAbsolute(projectMtime),
    status: computeFreshnessStatus(projectMtime, 'snapshot')
  },
  resumeSnapshot: quickResume,
  resumeHighlights,
  sourceCatalog,
  milestones: [
    { label: 'Context', status: /summary missing/i.test(quickResume.whatThisIs) ? 'Pending' : 'Done' },
    { label: 'Execution', status: /Current phase not recorded\./i.test(quickResume.currentPhase) ? 'Pending' : projectClosed ? 'Done' : projectPaused ? 'Paused' : 'Active' },
    { label: 'Review', status: /Review state not recorded\./i.test(quickResume.reviewState) ? 'Pending' : looksClosed(quickResume.reviewState) ? 'Done' : projectPaused ? 'Paused' : 'Active' },
    { label: 'Next Step', status: nextStepTracked ? 'Active' : 'Pending' }
  ],
  audit: {
    result: contractIssues.length
      ? 'Source contract needs attention'
      : projectClosed
        ? 'Accepted state recorded'
        : computeFreshnessStatus(projectMtime, 'snapshot') === 'Stale'
          ? 'Source needs refresh'
          : 'Live work recorded',
    unresolved: planningGaps.length,
    primaryBlock: clipText(contractIssues[0] || trackedWork, 200),
    source: 'PROJECT.md ' + quickResume.sourceLabel + ' + canonical command-center sections',
    freshness: computeFreshnessStatus(projectMtime, 'snapshot')
  },
  subsystems: subsystemState,
  approvedTruth,
  completedWork,
  needsAttention,
  latestHumanDirection: clipText(
    quickResume.nextAction
      ? `${quickResume.currentFocus !== 'Current focus not recorded.' ? quickResume.currentFocus + ' Next: ' : ''}${quickResume.nextAction}`
      : quickResume.currentFocus !== 'Current focus not recorded.'
        ? quickResume.currentFocus
        : quickResume.reviewState,
    280
  ),
  planningGaps,
  workflowHistory,
  aiSuggestions,
  dependencies,
  openDecisions
};

const host = dv.container;
host.innerHTML = '';
host.classList.add('pcx-host');

function openTerminal(mode) {
  const trackedNextSteps = [
    quickResume.currentPhase,
    ...state.planningGaps.slice(0, 3)
  ].map((item, index) => `${index + 1}. ${item}`);
  const liveSources = state.sourceCatalog
    .filter((entry) => entry.exists)
    .slice(0, 5)
    .map((entry) => `${entry.label} (${entry.updatedRelative})`);
  const latestSession = projectSessions[0]
    ? `${projectSessions[0].date} — ${projectSessions[0].title}`
    : 'No recent session heading was found.';
  const prompts = {
    next: [
      'Continue ' + state.projectName + '.',
      'Read PROJECT.md and Status.md first.',
      'Current phase: ' + quickResume.currentPhase,
      'Review state: ' + quickResume.reviewState,
      'Latest recorded session: ' + latestSession + '.',
      'Tracked next steps: ' + trackedNextSteps.join(' '),
      'Use the live source map in Status.md as a recovery anchor and preserve the accepted project command center baseline.',
      'Return the single highest-confidence next move.'
    ].join(' '),
    audit: [
      'Audit the ' + state.projectName + ' project page.',
      'Check it against the accepted project command center baseline, the wide layout rules, and the source-backed resume data now shown in Status.md.',
      'Summarize findings first, then the smallest safe next fixes.'
    ].join(' '),
    terminal: [
      'Continue ' + state.projectName + '.',
      'Start by reading ' + sourceDoc + ' and PROJECT.md.',
      'Current phase: ' + quickResume.currentPhase,
      'Review state: ' + quickResume.reviewState,
      'Latest recorded session: ' + latestSession + '.',
      'Fresh source docs: ' + liveSources.join('; ') + '.',
      'Tracked next steps: ' + trackedNextSteps.join(' '),
      'Then summarize the latest accepted state and the single highest-confidence next move.',
      'Keep the response concise and easy for a human to scan.'
    ].join(' ')
  };
  if (mode === 'next') {
    window._db.launchGoverned('continue', {
      targetRepo: projectDir,
      projectName: state.projectName,
      sourceNote,
      extraInstruction: [
        'Focus on the project command center and preserve the accepted wide shell baseline.',
        'Current phase: ' + quickResume.currentPhase,
        'Tracked next steps: ' + trackedNextSteps.join(' '),
        'Use the live source map in Status.md as the recovery anchor.'
      ].join(' ')
    });
    return;
  }
  const launchTitle = mode === 'terminal' ? state.projectName + ' Terminal' : state.projectName + ' - ' + mode;
  const notice = mode === 'terminal' ? 'Launching project terminal...' : undefined;
  window._db.launch(launchTitle, projectDir, prompts[mode], {
    skipPerms: true,
    notice
  });
}

function injectStyles() {
  const styleId = 'nx-project-command-center-style-v4';
  const css = `
.project-status-page.markdown-preview-view{background:#141517;padding:0;}
.project-status-page .markdown-preview-sizer{max-width:100% !important;padding:1.25rem 1.5rem 3rem !important;}
.pcx-host{width:100%;}
.pcx-shell{width:min(1940px,calc(100vw - 36px));margin:0 auto;color:#ece7df;font-family:Inter,Segoe UI,sans-serif;}
.pcx-card,.pcx-lane,.pcx-feedback,.pcx-banner,.pcx-meta-card,.pcx-milestone{background:linear-gradient(180deg,rgba(24,24,28,0.98),rgba(18,18,22,0.98));border:1px solid rgba(119,108,93,0.28);border-radius:24px;box-shadow:0 20px 40px rgba(0,0,0,0.18);}
.pcx-topbar{display:flex;justify-content:space-between;align-items:center;gap:1rem;margin-bottom:1rem;padding:0 0.5rem;}
.pcx-topbar-label{font:700 0.78rem/1.2 "JetBrains Mono",Consolas,monospace;letter-spacing:.18em;text-transform:uppercase;color:#d6a463;}
.pcx-toolbar{display:flex;gap:0.75rem;flex-wrap:wrap;justify-content:flex-end;}
.pcx-btn{border:1px solid rgba(139,157,190,0.42);border-radius:14px;background:rgba(77,87,122,0.9);color:#f4efe8;padding:0.78rem 1.25rem;font-weight:800;font-size:0.9rem;letter-spacing:.02em;cursor:pointer;transition:transform .16s ease,border-color .16s ease,background .16s ease;max-width:100%;}
.pcx-btn:hover{transform:translateY(-1px);border-color:#a9bddb;background:#5a6793;}
.pcx-btn-secondary{background:rgba(40,42,46,0.95);border-color:rgba(184,153,96,0.34);}
.pcx-btn-secondary:hover{background:#373b42;border-color:#d3b078;}
.pcx-header{display:grid;grid-template-columns:minmax(0,1.3fr) minmax(360px,0.7fr);gap:1rem;margin-bottom:1rem;}
.pcx-banner{padding:2rem 2.2rem;}
.pcx-node{font:700 0.76rem/1.2 "JetBrains Mono",Consolas,monospace;letter-spacing:.18em;text-transform:uppercase;color:#a88862;}
.pcx-title{margin:0.35rem 0 0;font:800 clamp(2.2rem,2.5vw,3.4rem)/1.02 Manrope,Segoe UI,sans-serif;color:#faf7f0;}
.pcx-subtitle{margin:0.75rem 0 0;font-size:1rem;line-height:1.6;color:#cfc7bb;max-width:70ch;}
.pcx-quote{margin-top:1.15rem;padding:1rem 1.1rem;border-left:4px solid #d79a54;background:rgba(217,151,63,0.08);color:#ddd2c0;border-radius:14px;font-size:0.98rem;line-height:1.6;}
.pcx-side{display:flex;flex-direction:column;gap:1rem;}
.pcx-engine{align-self:flex-end;padding:0.7rem 1rem;border:1px solid rgba(115,138,166,0.33);border-radius:999px;background:rgba(31,36,44,0.95);font:700 0.8rem/1 "JetBrains Mono",Consolas,monospace;color:#c9d5e6;letter-spacing:.12em;text-transform:uppercase;}
.pcx-freshness{padding:1rem 1.1rem;background:rgba(33,32,41,0.96);border:1px solid rgba(119,108,93,0.22);border-radius:18px;}
.pcx-freshness-grid{display:grid;grid-template-columns:1fr auto;gap:0.45rem 1rem;font-size:0.92rem;color:#d7cfc4;}
.pcx-freshness-label{font:700 0.72rem/1.2 "JetBrains Mono",Consolas,monospace;letter-spacing:.14em;text-transform:uppercase;color:#9f9183;}
.pcx-meta{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:0.85rem;margin:0.9rem 0 1rem;}
.pcx-meta-card{padding:1rem 0.95rem;min-height:98px;}
.pcx-meta-label{font:700 0.72rem/1.2 "JetBrains Mono",Consolas,monospace;letter-spacing:.12em;text-transform:uppercase;color:#9f9183;}
.pcx-meta-value{margin-top:0.72rem;font:700 1.06rem/1.38 Manrope,Segoe UI,sans-serif;color:#fbf8f3;}
.pcx-meta-copy{margin-top:0.38rem;font-size:0.82rem;line-height:1.45;color:#b9aea1;}
.pcx-milestones{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:0.85rem;margin-bottom:1rem;}
.pcx-milestone{padding:1rem 1.1rem;display:flex;flex-direction:column;gap:0.55rem;position:relative;overflow:hidden;}
.pcx-milestone::before{content:"";position:absolute;left:0;top:0;bottom:0;width:4px;background:#54627f;opacity:.9;}
.pcx-milestone-active::before{background:#d79a54;}
.pcx-milestone-done::before{background:#88b6a4;}
.pcx-milestone-pending::before{background:#5e5967;}
.pcx-main-grid{display:grid;grid-template-columns:minmax(0,1.05fr) minmax(0,1.18fr) minmax(0,0.9fr);gap:0.9rem;align-items:start;}
.pcx-lane{padding:1.05rem 1rem 1rem;display:flex;flex-direction:column;gap:0.9rem;height:100%;min-width:0;}
.pcx-lane-header{display:flex;justify-content:space-between;align-items:center;padding-bottom:0.7rem;border-bottom:1px solid rgba(119,108,93,0.18);}
.pcx-lane-title{font:700 0.78rem/1.2 "JetBrains Mono",Consolas,monospace;letter-spacing:.16em;text-transform:uppercase;color:#b08e64;}
.pcx-lane-meta{font:700 0.66rem/1.2 "JetBrains Mono",Consolas,monospace;letter-spacing:.12em;text-transform:uppercase;color:#8d8276;}
.pcx-card{padding:0.95rem 0.95rem 1rem;min-width:0;}
.pcx-card-title{margin:0 0 0.8rem;font:800 1.28rem/1.15 Manrope,Segoe UI,sans-serif;color:#fbf8f3;}
.pcx-section-kicker{font:700 0.72rem/1.2 "JetBrains Mono",Consolas,monospace;letter-spacing:.16em;text-transform:uppercase;color:#b08e64;}
.pcx-copy{color:#d9d0c5;font-size:0.96rem;line-height:1.65;}
.pcx-copy strong{color:#fff7ec;}
.pcx-fact-rows{display:flex;flex-direction:column;gap:0.55rem;color:#d9d0c5;font-size:0.96rem;line-height:1.6;}
.pcx-fact-row strong{color:#fff7ec;}
.pcx-badge{display:inline-flex;align-items:center;gap:0.35rem;padding:0.28rem 0.65rem;border-radius:999px;border:1px solid rgba(192,161,113,0.34);background:rgba(217,151,63,0.1);color:#f1d2aa;font:700 0.72rem/1.2 "JetBrains Mono",Consolas,monospace;letter-spacing:.08em;text-transform:uppercase;}
.pcx-list{margin:0;padding-left:1.15rem;display:flex;flex-direction:column;gap:0.65rem;color:#ddd5ca;font-size:0.96rem;line-height:1.6;}
.pcx-subsystems{display:flex;flex-direction:column;gap:0.65rem;}
.pcx-subsystem{display:flex;justify-content:space-between;gap:1rem;padding:0.85rem 0.95rem;border-radius:16px;background:rgba(38,37,46,0.96);border:1px solid rgba(119,108,93,0.18);}
.pcx-subsystem-name{font-weight:800;color:#f6f1e8;}
.pcx-subsystem-meta{font-size:0.86rem;color:#bdb3a8;margin-top:0.15rem;}
.pcx-need{border:2px solid rgba(215,154,84,0.38);background:linear-gradient(180deg,rgba(47,38,31,0.98),rgba(28,24,21,0.98));}
.pcx-need-actions{display:flex;gap:0.65rem;flex-wrap:wrap;margin-top:1rem;}
.pcx-need-actions .pcx-btn{padding:0.6rem 1rem;font-size:0.84rem;}
.pcx-timeline{display:flex;flex-direction:column;gap:0.9rem;position:relative;padding-left:1.4rem;padding-right:0.55rem;max-height:min(980px,calc(100vh - 320px));overflow-y:auto;scrollbar-gutter:stable;}
.pcx-timeline::-webkit-scrollbar{width:10px;}
.pcx-timeline::-webkit-scrollbar-thumb{background:rgba(92,103,130,0.65);border-radius:999px;}
.pcx-timeline::before{content:"";position:absolute;left:0.38rem;top:0.15rem;bottom:0.2rem;width:2px;background:linear-gradient(180deg,rgba(215,154,84,0.7),rgba(85,90,104,0.1));}
.pcx-event{position:relative;padding:0.85rem 0.9rem;border-radius:16px;background:rgba(35,35,43,0.96);border:1px solid rgba(119,108,93,0.16);}
.pcx-event::before{content:"";position:absolute;left:-1.37rem;top:1rem;width:12px;height:12px;border-radius:50%;background:#d79a54;border:2px solid #221f1d;box-shadow:0 0 0 4px rgba(215,154,84,0.12);}
.pcx-event-phase{font:700 0.68rem/1.2 "JetBrains Mono",Consolas,monospace;letter-spacing:.12em;text-transform:uppercase;color:#b08e64;}
.pcx-event-title{margin-top:0.25rem;font-weight:800;color:#f5efe7;}
.pcx-event-note{margin-top:0.25rem;font-size:0.9rem;line-height:1.5;color:#d0c6ba;}
.pcx-feedback{margin-top:1rem;padding:1rem;}
.pcx-feedback-inline,.pcx-open-decisions-inline{margin-top:0;}
.pcx-feedback-head{display:flex;align-items:center;gap:1rem;margin-bottom:1rem;}
.pcx-feedback-title{font:800 1.45rem/1.1 Manrope,Segoe UI,sans-serif;color:#faf7f0;}
.pcx-feedback-grid{display:grid;grid-template-columns:minmax(0,0.94fr) minmax(0,1.06fr);gap:1rem;align-items:start;min-width:0;}
.pcx-field-stack{display:grid;grid-template-columns:1fr;gap:0.85rem;}
.pcx-field{display:flex;flex-direction:column;gap:0.45rem;min-width:0;}
.pcx-field-label{font:700 0.68rem/1.2 "JetBrains Mono",Consolas,monospace;letter-spacing:.14em;text-transform:uppercase;color:#9f9183;}
.pcx-textarea{width:100%;border-radius:16px;border:1px solid rgba(119,108,93,0.24);background:#12131a;color:#f1ece4;padding:0.95rem 1rem;font-size:0.96rem;line-height:1.55;resize:vertical;min-height:170px;max-height:340px;overflow:auto;}
.pcx-generated{display:grid;grid-template-columns:1fr;gap:0.8rem;min-width:0;align-items:start;overflow:hidden;}
.pcx-generated pre{margin:0;padding:1rem 1.15rem 1rem 1rem;border-radius:18px;background:#0e1017;border:1px solid rgba(119,108,93,0.22);color:#f3eee4;font-size:0.92rem;line-height:1.55;white-space:pre-wrap;overflow-wrap:anywhere;word-break:break-word;min-height:640px;max-height:760px;overflow:auto;box-sizing:border-box;scrollbar-gutter:stable;}
.pcx-feedback-actions{display:flex;gap:0.75rem;flex-wrap:wrap;align-items:flex-start;}
.pcx-foot{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0.85rem 1rem;font-size:0.82rem;color:#a79a8c;align-items:start;}
.pcx-foot span{display:block;min-width:0;}
.pcx-open-decisions{margin-top:1rem;}
.pcx-log{margin-top:1rem;padding-top:0.95rem;border-top:1px solid rgba(119,108,93,0.18);}
.pcx-log-list{display:flex;flex-direction:column;gap:0.75rem;}
.pcx-log-item{padding:0.85rem 0.95rem;border-radius:16px;background:rgba(18,19,26,0.88);border:1px solid rgba(119,108,93,0.2);}
.pcx-log-head{display:flex;justify-content:space-between;gap:0.75rem;align-items:center;margin-bottom:0.35rem;}
.pcx-log-title{font-weight:800;color:#f6f1e8;}
.pcx-log-meta{font-size:0.82rem;color:#b9ae9f;}
.pcx-log-copy{font-size:0.9rem;line-height:1.5;color:#d5cbc0;}
.pcx-log-empty{font-size:0.94rem;color:#bfb4a8;}
.pcx-source-map{max-height:340px;overflow:auto;padding-right:0.2rem;}
.pcx-feedback-status{display:flex;justify-content:space-between;gap:0.85rem;align-items:flex-start;padding:0.75rem 0.85rem;border-radius:16px;background:rgba(18,19,26,0.88);border:1px solid rgba(119,108,93,0.2);margin-bottom:0.9rem;}
.pcx-feedback-status-copy{font-size:0.9rem;line-height:1.45;color:#d6ccc0;}
.pcx-feedback-status-copy strong{display:block;color:#f7f2e9;margin-bottom:0.18rem;}
.pcx-feedback-status-meta{font-size:0.8rem;color:#a99c8f;text-align:right;white-space:nowrap;}
@media (max-width: 1700px){.pcx-shell{width:min(1760px,calc(100vw - 40px));}.pcx-meta{grid-template-columns:repeat(3,minmax(0,1fr));}.pcx-main-grid{grid-template-columns:minmax(0,1fr) minmax(0,1fr);}.pcx-lane:last-child{grid-column:1 / -1;}.pcx-feedback-grid{grid-template-columns:minmax(0,0.92fr) minmax(0,1.08fr);}}
@media (max-width: 1400px){.pcx-shell{width:min(1560px,calc(100vw - 40px));}.pcx-main-grid{grid-template-columns:1fr;}.pcx-feedback-grid,.pcx-header,.pcx-meta,.pcx-milestones,.pcx-foot{grid-template-columns:1fr;}.pcx-generated pre{min-height:360px;max-height:520px;}}
@media (max-width: 860px){.project-status-page .markdown-preview-sizer{padding:1rem 1rem 3rem !important;}.pcx-shell{width:100%;}.pcx-topbar,.pcx-toolbar,.pcx-feedback-actions,.pcx-need-actions,.pcx-feedback-status{flex-direction:column;align-items:stretch;}.pcx-feedback-status-meta{text-align:left;white-space:normal;}.pcx-textarea{min-height:150px;}}
  `;
  let styleEl = document.getElementById(styleId);
  if (!styleEl) {
    styleEl = document.createElement('style');
    styleEl.id = styleId;
    document.head.appendChild(styleEl);
  }
  styleEl.textContent = css;
}

function renderList(items) {
  return items.map((item) => '<li>' + escapeHtml(item) + '</li>').join('');
}

function renderSubsystems(items) {
  return items.map((item) => `
    <div class="pcx-subsystem">
      <div>
        <div class="pcx-subsystem-name">${escapeHtml(item.name)}</div>
        <div class="pcx-subsystem-meta">Phase: ${escapeHtml(item.phase)}</div>
      </div>
      <div style="text-align:right;">
        <div class="pcx-badge">${escapeHtml(item.freshness)}</div>
        <div class="pcx-subsystem-meta">${escapeHtml(item.health)}</div>
      </div>
    </div>
  `).join('');
}

function renderTimeline(items) {
  return items.map((item) => `
    <div class="pcx-event">
      <div class="pcx-event-phase">${escapeHtml(item.phase)}</div>
      <div class="pcx-event-title">${escapeHtml(item.title)}</div>
      <div class="pcx-event-note">${escapeHtml(item.note)}</div>
    </div>
  `).join('');
}

function renderSourceCatalog(entries) {
  return entries.map((entry) => `
    <div class="pcx-log-item">
      <div class="pcx-log-head">
        <div class="pcx-log-title">${escapeHtml(entry.label)}</div>
        <div class="pcx-log-meta">${escapeHtml(entry.updatedRelative)}</div>
      </div>
      <div class="pcx-log-copy">${escapeHtml(entry.role)} - ${escapeHtml(entry.path)}</div>
    </div>
  `).join('');
}

function buildFeedbackSummaryParts(fields) {
  const parts = [];
  const approvals = firstMeaningfulLine(fields.approvals).replace(/^-+\s*/, '');
  const changeRequests = firstMeaningfulLine(fields.changeRequests).replace(/^-+\s*/, '');
  const questions = firstMeaningfulLine(fields.questions);
  if (approvals) parts.push('Approved: ' + approvals);
  if (changeRequests) parts.push('Change: ' + changeRequests);
  if (questions) parts.push('Context: ' + questions);
  return clipText(parts.join(' | ') || 'Project feedback processed into the project log.', 240);
}

function summarizeFeedbackEntry(entry) {
  if (entry.summary) return entry.summary;
  return buildFeedbackSummaryParts({
    approvals: entry.approvals,
    changeRequests: entry.requestedChanges,
    questions: entry.questionsContext
  });
}

function formatFeedbackSource(source) {
  if (source === 'draft-recovery') return 'Recovered draft';
  if (source === 'manual-save') return 'Saved to Project';
  return 'Processed';
}

function formatFeedbackStatus(status) {
  if (status === 'absorbed') return 'Absorbed';
  if (status === 'rejected') return 'Rejected';
  if (status === 'pending') return 'Pending';
  return status ? status[0].toUpperCase() + status.slice(1) : 'Processed';
}

function getLastProcessedState(log) {
  const meta = log.meta || {};
  if (meta.lastProcessedAt) {
    const processedAt = new Date(meta.lastProcessedAt);
    return {
      label: formatRelative(processedAt),
      detail: formatFeedbackStatus(meta.lastProcessedStatus || 'absorbed') + ' via ' + formatFeedbackSource(meta.lastProcessedSource),
      summary: meta.lastProcessedSummary || 'Latest processed feedback entry is in the project log.',
      absolute: formatAbsolute(processedAt)
    };
  }
  return {
    label: 'No input yet',
    detail: 'Waiting for the first processed workspace entry.',
    summary: 'The feedback workspace has not written anything into the project log yet.',
    absolute: 'Not processed yet'
  };
}

function renderFeedbackLog(entries) {
  if (!entries.length) {
    return '<div class="pcx-log-empty">No processed project feedback yet. Absorbed entries will appear here with their current status.</div>';
  }

  return entries.slice(0, 6).map((entry) => {
    const processedAt = entry.processedAt || entry.savedAt;
    const processedDate = processedAt ? new Date(processedAt) : null;
    const summary = summarizeFeedbackEntry(entry);
    return `
      <div class="pcx-log-item">
        <div class="pcx-log-head">
          <div class="pcx-log-title">${escapeHtml(formatFeedbackStatus(entry.status || 'absorbed'))}</div>
          <div class="pcx-log-meta">${escapeHtml(formatAbsolute(processedDate))}</div>
        </div>
        <div class="pcx-log-copy">${escapeHtml(summary)}</div>
        <div class="pcx-log-meta">${escapeHtml(formatFeedbackSource(entry.processedSource))}</div>
      </div>
    `;
  }).join('');
}

function render() {
  const draft = loadDraft();
  const feedbackLog = loadFeedbackLog();
  const lastProcessedState = getLastProcessedState(feedbackLog);
  const pendingFeedbackCount = (feedbackLog.entries || []).filter((entry) => (entry.status || 'pending') === 'pending').length;
  const pendingHumanItems = state.openDecisions.length + pendingFeedbackCount;
  injectStyles();

  host.innerHTML = `
    <section class="pcx-shell">
      <div class="pcx-topbar">
        <div class="pcx-topbar-label">Project: ${escapeHtml(state.projectLabel)}</div>
        <div class="pcx-toolbar">
          <button class="pcx-btn" data-action="next" title="Open the governed next-step flow for this project.">Execute Next Step</button>
          <button class="pcx-btn pcx-btn-secondary" data-action="audit" title="Launch an audit-focused Claude session for this project.">Launch Audit</button>
          <button class="pcx-btn pcx-btn-secondary" data-action="terminal" title="Open a normal Claude terminal session for this project.">Open Terminal</button>
        </div>
      </div>

      <div class="pcx-header">
        <div class="pcx-banner">
          <div class="pcx-node">System Node: ${escapeHtml(state.projectLabel)}</div>
          <h1 class="pcx-title">${escapeHtml(state.title)}</h1>
          <p class="pcx-subtitle">${escapeHtml(state.tagline)}</p>
          <div class="pcx-quote">${escapeHtml(state.bannerQuote)}</div>
        </div>
        <div class="pcx-side">
          <div class="pcx-engine">${escapeHtml(state.engineState)}</div>
          <div class="pcx-freshness">
            <div class="pcx-freshness-grid">
              <span class="pcx-freshness-label">Source</span><span>${escapeHtml(state.freshness.source)}</span>
              <span class="pcx-freshness-label">Updated</span><span>${escapeHtml(state.freshness.updatedRelative)}</span>
              <span class="pcx-freshness-label">Timestamp</span><span>${escapeHtml(state.freshness.updatedAbsolute)}</span>
              <span class="pcx-freshness-label">Freshness</span><span>${escapeHtml(state.freshness.status)}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="pcx-meta">
        <div class="pcx-meta-card"><div class="pcx-meta-label">Start Date</div><div class="pcx-meta-value">${escapeHtml(state.startDate)}</div></div>
        <div class="pcx-meta-card"><div class="pcx-meta-label">Last Activity</div><div class="pcx-meta-value">${escapeHtml(state.lastActivity)}</div></div>
        <div class="pcx-meta-card"><div class="pcx-meta-label">Pending Items</div><div class="pcx-meta-value">${escapeHtml(String(pendingHumanItems).padStart(2, '0'))}</div></div>
        <div class="pcx-meta-card"><div class="pcx-meta-label">Last Input</div><div class="pcx-meta-value">${escapeHtml(lastProcessedState.label)}</div><div class="pcx-meta-copy">${escapeHtml(lastProcessedState.detail)}</div></div>
        <div class="pcx-meta-card"><div class="pcx-meta-label">Primary Focus</div><div class="pcx-meta-value">${escapeHtml(state.primaryFocus)}</div><div class="pcx-meta-copy">Full current-phase detail stays in the attention lane below.</div></div>
      </div>

      <div class="pcx-milestones">
        ${state.milestones.map((item) => `
          <div class="pcx-milestone pcx-milestone-${item.status.toLowerCase()}">
            <div class="pcx-meta-label">Milestone</div>
            <div class="pcx-meta-value">${escapeHtml(item.label)}: ${escapeHtml(item.status)}</div>
          </div>
        `).join('')}
      </div>

      <div class="pcx-main-grid">
        <div class="pcx-lane">
          <div class="pcx-lane-header">
            <div class="pcx-lane-title">Lane 01 // Approved Truth</div>
            <div class="pcx-lane-meta">Source: System-State</div>
          </div>
          <article class="pcx-card">
            <div class="pcx-section-kicker">Source Snapshot</div>
            <h2 class="pcx-card-title">${escapeHtml(state.audit.result)}</h2>
            <p class="pcx-copy"><strong>Open signals:</strong> ${escapeHtml(state.audit.unresolved)}<br><strong>Primary focus:</strong> ${escapeHtml(state.audit.primaryBlock)}</p>
            <div class="pcx-foot"><span>${escapeHtml(state.audit.source)}</span><span>${escapeHtml(state.audit.freshness)}</span></div>
          </article>
          <article class="pcx-card">
            <div class="pcx-section-kicker">${escapeHtml(state.resumeSnapshot.sourceLabel)} Snapshot</div>
            <h2 class="pcx-card-title">Last Active ${escapeHtml(state.resumeSnapshot.lastActive)}</h2>
            <div class="pcx-fact-rows">${renderFactRows(state.resumeHighlights)}</div>
            <div class="pcx-foot"><span>Source: PROJECT.md ${escapeHtml(state.resumeSnapshot.sourceLabel)}</span><span>${escapeHtml(state.freshness.updatedRelative)}</span></div>
          </article>
          <article class="pcx-card">
            <div class="pcx-section-kicker">Approved Truth</div>
            <h2 class="pcx-card-title">What is already locked</h2>
            <ul class="pcx-list">${renderList(state.approvedTruth)}</ul>
          </article>
          <article class="pcx-card">
            <div class="pcx-section-kicker">Active Subsystems</div>
            <h2 class="pcx-card-title">Current surfaces</h2>
            <div class="pcx-subsystems">${renderSubsystems(state.subsystems)}</div>
          </article>
          <article class="pcx-card">
            <div class="pcx-section-kicker">Completed Work</div>
            <h2 class="pcx-card-title">What already landed</h2>
            <ul class="pcx-list">${renderList(state.completedWork)}</ul>
          </article>
        </div>

        <div class="pcx-lane">
          <div class="pcx-lane-header">
            <div class="pcx-lane-title">Lane 02 // Human Intent</div>
            <div class="pcx-lane-meta">Identity: Commander-Tyler</div>
          </div>
          <article class="pcx-card pcx-need">
            <div class="pcx-section-kicker">Needs Your Attention</div>
            <h2 class="pcx-card-title">${escapeHtml(state.needsAttention.title)}</h2>
            <p class="pcx-copy"><strong>${escapeHtml(state.needsAttention.subtitle)}</strong><br>${escapeHtml(state.needsAttention.summary)}</p>
            <div class="pcx-need-actions">
              <button class="pcx-btn" data-action="approve" title="Approve the current project-page direction and keep using it as the live command center.">Approve</button>
              <button class="pcx-btn pcx-btn-secondary" data-action="modify" title="Record a concrete change you want made in the next pass.">Modify</button>
              <button class="pcx-btn pcx-btn-secondary" data-action="defer" title="Leave this item open for later; saving will record it as a pending context note, not an approval or rejection.">Defer</button>
              <button class="pcx-btn pcx-btn-secondary" data-action="review" title="Open a review-focused Claude session for this page.">Open Review</button>
            </div>
          </article>
          <article class="pcx-card">
            <div class="pcx-section-kicker">Current Mandate</div>
            <h2 class="pcx-card-title">Tyler's instruction</h2>
            <p class="pcx-copy">${escapeHtml(state.latestHumanDirection)}</p>
          </article>
          <article class="pcx-card">
            <div class="pcx-section-kicker">Critical Planning Gaps</div>
            <h2 class="pcx-card-title">Still needs definition</h2>
            <ul class="pcx-list">${renderList(state.planningGaps)}</ul>
          </article>
          <article class="pcx-card pcx-feedback pcx-feedback-inline">
            <div class="pcx-feedback-head">
              <div class="pcx-section-kicker">Feedback Workspace // Command Input</div>
              <div style="flex:1;height:1px;background:rgba(119,108,93,0.24);"></div>
            </div>
            <div class="pcx-feedback-status">
              <div class="pcx-feedback-status-copy">
                <strong>Last processed input</strong>
                ${escapeHtml(lastProcessedState.summary)}
              </div>
              <div class="pcx-feedback-status-meta">${escapeHtml(lastProcessedState.detail)}<br>${escapeHtml(lastProcessedState.absolute)}</div>
            </div>
            <div class="pcx-feedback-grid">
              <div class="pcx-field-stack">
                <label class="pcx-field">
                  <span class="pcx-field-label">Approvals / Sign-offs</span>
                  <textarea class="pcx-textarea" data-draft="approvals" placeholder="Record what is approved, accepted, or confirmed." title="Use this for approvals, accepted directions, and things you are locking in.">${escapeHtml(draft.approvals)}</textarea>
                </label>
                <label class="pcx-field">
                  <span class="pcx-field-label">Requested Changes</span>
                  <textarea class="pcx-textarea" data-draft="changeRequests" placeholder="Describe anything that should change before the next pass." title="Use this only when you want something changed in the layout, workflow, wording, or data presentation.">${escapeHtml(draft.changeRequests)}</textarea>
                </label>
                <label class="pcx-field">
                  <span class="pcx-field-label">Questions / Context</span>
                  <textarea class="pcx-textarea" data-draft="questions" placeholder="Capture missing context, open questions, or anything you want Codex to address next." title="Use this for questions, reminders, background context, or soft notes that are not direct change requests.">${escapeHtml(draft.questions)}</textarea>
                </label>
              </div>
              <div class="pcx-generated">
                <div class="pcx-section-kicker">Generated Markdown</div>
                <pre data-generated></pre>
                <div class="pcx-feedback-actions">
                  <button class="pcx-btn" data-action="save-project" title="Process this feedback into the project log and clear the current draft.">Save to Project</button>
                  <button class="pcx-btn pcx-btn-secondary" data-action="copy-markdown" title="Copy the current feedback block to the clipboard as a troubleshooting or handoff fallback without clearing the draft.">Copy Markdown</button>
                  <button class="pcx-btn pcx-btn-secondary" data-action="reset-draft" title="Clear the local draft fields on this page.">Reset Draft</button>
                </div>
                <div class="pcx-foot"><span>Save to Project now absorbs the current input into the project log and clears the workspace.</span><span>Copy Markdown leaves the draft in place. Defer only becomes real project context after save.</span></div>
              </div>
            </div>
            <div class="pcx-log">
              <div class="pcx-section-kicker">Recent Feedback Log</div>
              <div class="pcx-log-list">${renderFeedbackLog(feedbackLog.entries || [])}</div>
            </div>
          </article>
          <article class="pcx-card pcx-open-decisions-inline">
            <div class="pcx-section-kicker">Open Decisions</div>
            <h2 class="pcx-card-title">Questions that still need a human call</h2>
            <ul class="pcx-list">${renderList(state.openDecisions)}</ul>
          </article>
        </div>

        <div class="pcx-lane">
          <div class="pcx-lane-header">
            <div class="pcx-lane-title">Lane 03 // AI Suggestions</div>
            <div class="pcx-lane-meta">Review + dependency lane</div>
          </div>
          <article class="pcx-card">
            <div class="pcx-section-kicker">Workflow History</div>
            <h2 class="pcx-card-title">How we got here + what comes next</h2>
            <div class="pcx-timeline">${renderTimeline(state.workflowHistory)}</div>
          </article>
          <article class="pcx-card">
            <div class="pcx-section-kicker">AI Suggestions</div>
            <h2 class="pcx-card-title">What the next rollout should preserve</h2>
            <ul class="pcx-list">${renderList(state.aiSuggestions)}</ul>
          </article>
          <article class="pcx-card">
            <div class="pcx-section-kicker">Dependency Graph</div>
            <h2 class="pcx-card-title">Upstream / downstream</h2>
            <ul class="pcx-list">
              <li><strong>Upstream:</strong> ${escapeHtml(state.dependencies.upstream)}</li>
              <li><strong>Downstream:</strong> ${escapeHtml(state.dependencies.downstream)}</li>
              <li><strong>Global:</strong> ${escapeHtml(state.dependencies.global)}</li>
            </ul>
          </article>
          <article class="pcx-card">
            <div class="pcx-section-kicker">Live Source Map</div>
            <h2 class="pcx-card-title">Repo files anchoring this page</h2>
            <div class="pcx-log-list pcx-source-map">${renderSourceCatalog(state.sourceCatalog)}</div>
          </article>
        </div>
      </div>
    </section>
  `;

  const generated = host.querySelector('[data-generated]');
  if (generated) generated.textContent = buildMarkdown(draft);

  host.querySelectorAll('[data-draft]').forEach((node) => {
    node.addEventListener('input', () => {
      const nextDraft = {
        approvals: host.querySelector('[data-draft="approvals"]').value,
        changeRequests: host.querySelector('[data-draft="changeRequests"]').value,
        questions: host.querySelector('[data-draft="questions"]').value
      };
      saveDraft(nextDraft);
      if (generated) generated.textContent = buildMarkdown(nextDraft);
    });
  });

  host.querySelector('[data-action="save-project"]')?.addEventListener('click', () => saveToProject({
    approvals: host.querySelector('[data-draft="approvals"]').value,
    changeRequests: host.querySelector('[data-draft="changeRequests"]').value,
    questions: host.querySelector('[data-draft="questions"]').value
  }));

  host.querySelector('[data-action="copy-markdown"]')?.addEventListener('click', () => copyMarkdown({
    approvals: host.querySelector('[data-draft="approvals"]').value,
    changeRequests: host.querySelector('[data-draft="changeRequests"]').value,
    questions: host.querySelector('[data-draft="questions"]').value
  }));

  host.querySelector('[data-action="reset-draft"]')?.addEventListener('click', () => resetDraft());
  host.querySelector('[data-action="next"]')?.addEventListener('click', () => openTerminal('next'));
  host.querySelector('[data-action="audit"]')?.addEventListener('click', () => openTerminal('audit'));
  host.querySelector('[data-action="terminal"]')?.addEventListener('click', () => openTerminal('terminal'));
  host.querySelector('[data-action="review"]')?.addEventListener('click', () => openTerminal('audit'));
  host.querySelector('[data-action="approve"]')?.addEventListener('click', () => {
    const area = host.querySelector('[data-draft="approvals"]');
    area.value = appendUniqueLine(area.value, '- Accepted the current project command center direction for continued use.');
    area.dispatchEvent(new Event('input'));
  });
  host.querySelector('[data-action="modify"]')?.addEventListener('click', () => {
    const area = host.querySelector('[data-draft="changeRequests"]');
    area.focus();
    new Notice('Add the requested change in Requested Changes, then save it to the project log.', 5000);
  });
  host.querySelector('[data-action="defer"]')?.addEventListener('click', () => {
    const area = host.querySelector('[data-draft="questions"]');
    area.value = appendUniqueLine(area.value, '- Deferred this item for a later pass without requesting a change yet.');
    area.dispatchEvent(new Event('input'));
  });
}

function dedupeLines(text) {
  const seen = new Set();
  return String(text || '')
    .split('\n')
    .map((value) => value.trim())
    .filter(Boolean)
    .filter((value) => {
      if (seen.has(value)) return false;
      seen.add(value);
      return true;
    })
    .join('\n');
}

function normalizeDraft(draft) {
  const next = draft || emptyDraft();
  return {
    approvals: dedupeLines(next.approvals),
    changeRequests: dedupeLines(next.changeRequests),
    questions: dedupeLines(next.questions),
    updatedAt: next.updatedAt || ''
  };
}

function ensureFeedbackLog() {
  if (_fs.existsSync(feedbackLogFile)) return;
  _fs.writeFileSync(feedbackLogFile, JSON.stringify({
    project: projectLeaf,
    meta: {
      lastProcessedAt: '',
      lastProcessedStatus: '',
      lastProcessedSource: '',
      lastProcessedSummary: ''
    },
    entries: []
  }, null, 2), 'utf8');
}

function normalizeFeedbackLog(data) {
  const next = data && typeof data === 'object' ? data : {};
  if (!Array.isArray(next.entries)) next.entries = [];
  if (!next.meta || typeof next.meta !== 'object') next.meta = {};
  next.meta.lastProcessedAt = next.meta.lastProcessedAt || '';
  next.meta.lastProcessedStatus = next.meta.lastProcessedStatus || '';
  next.meta.lastProcessedSource = next.meta.lastProcessedSource || '';
  next.meta.lastProcessedSummary = next.meta.lastProcessedSummary || '';
  return next;
}

function loadFeedbackLog() {
  try {
    ensureFeedbackLog();
    const raw = _fs.readFileSync(feedbackLogFile, 'utf8');
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object') throw new Error('Invalid feedback log');
    return normalizeFeedbackLog(parsed);
  } catch (_error) {
    return normalizeFeedbackLog({
      project: projectLeaf,
      meta: {
        lastProcessedAt: '',
        lastProcessedStatus: '',
        lastProcessedSource: '',
        lastProcessedSummary: ''
      },
      entries: []
    });
  }
}

function saveFeedbackLog(data) {
  _fs.writeFileSync(feedbackLogFile, JSON.stringify(data, null, 2), 'utf8');
}

function buildFeedbackSummary(normalized) {
  return buildFeedbackSummaryParts(normalized);
}

function saveFeedbackEntry(draft, source = 'manual-save') {
  const normalized = normalizeDraft(draft);
  if (!normalized.approvals && !normalized.changeRequests && !normalized.questions) {
    new Notice('There is no feedback to save yet.', 4000);
    return false;
  }

  const log = loadFeedbackLog();
  const latest = Array.isArray(log.entries) && log.entries.length ? log.entries[0] : null;
  const sameAsLatest = latest
    && latest.approvals === normalized.approvals
    && latest.requestedChanges === normalized.changeRequests
    && latest.questionsContext === normalized.questions;

  const processedAt = new Date().toISOString();
  const summary = buildFeedbackSummary(normalized);
  if (!Array.isArray(log.entries)) log.entries = [];
  if (!sameAsLatest) {
    log.entries.unshift({
      id: 'fb-' + Date.now(),
      inputAt: normalized.updatedAt || processedAt,
      processedAt,
      status: 'absorbed',
      processedSource: source,
      approvals: normalized.approvals,
      requestedChanges: normalized.changeRequests,
      questionsContext: normalized.questions,
      summary
    });
  }
  log.meta.lastProcessedAt = processedAt;
  log.meta.lastProcessedStatus = 'absorbed';
  log.meta.lastProcessedSource = source;
  log.meta.lastProcessedSummary = sameAsLatest ? summarizeFeedbackEntry(latest) : summary;
  saveFeedbackLog(log);
  return {
    outcome: !sameAsLatest ? 'saved' : 'duplicate',
    processedAt,
    source
  };
}

function clearDraftUI() {
  host.querySelectorAll('[data-draft]').forEach((node) => {
    node.value = '';
  });
  const generated = host.querySelector('[data-generated]');
  if (generated) generated.textContent = buildMarkdown(emptyDraft());
}

function loadDraft() {
  try {
    const raw = window.localStorage.getItem(storageKey);
    if (!raw) return emptyDraft();
    return normalizeDraft(JSON.parse(raw));
  } catch (_error) {
    return emptyDraft();
  }
}

function saveDraft(draft) {
  const nextDraft = normalizeDraft(draft);
  nextDraft.updatedAt = new Date().toISOString();
  window.localStorage.setItem(storageKey, JSON.stringify(nextDraft));
}

function appendUniqueLine(text, line) {
  return dedupeLines([String(text || ''), line].filter(Boolean).join('\n'));
}

function buildMarkdown(draft) {
  const normalized = normalizeDraft(draft);
  return [
    '# Project Feedback - ' + state.projectLabel,
    '',
    'Generated: ' + new Date().toISOString(),
    '',
    '## Approvals',
    normalized.approvals.trim() || '_none_',
    '',
    '## Requested Changes',
    normalized.changeRequests.trim() || '_none_',
    '',
    '## Questions / Context',
    normalized.questions.trim() || '_none_'
  ].join('\n');
}

function copyMarkdown(draft) {
  const normalized = normalizeDraft(draft);
  const markdown = buildMarkdown(normalized);
  navigator.clipboard.writeText(markdown)
    .then(() => {
      new Notice('Project feedback copied to clipboard.', 4500);
    })
    .catch((error) => new Notice('Copy failed: ' + error.message, 6000));
}

function saveToProject(draft) {
  const result = saveFeedbackEntry(draft, 'manual-save');
  if (!result) return;
  window.localStorage.removeItem(storageKey);
  clearDraftUI();
  render();
  const message = result.outcome === 'saved'
    ? 'Project feedback was absorbed into the project log and the workspace was cleared.'
    : 'That exact input was already absorbed into the project log, so the workspace was just cleared.';
  new Notice(message, 5000);
}

function resetDraft() {
  window.localStorage.removeItem(storageKey);
  clearDraftUI();
  render();
  new Notice('Project feedback draft reset.', 4000);
}

function recoverLingeringDraft() {
  const draft = loadDraft();
  if (!draft.approvals && !draft.changeRequests && !draft.questions) return null;
  const result = saveFeedbackEntry(draft, 'draft-recovery');
  if (!result) return null;
  window.localStorage.removeItem(storageKey);
  return result;
}

const startupRecovery = recoverLingeringDraft();
render();
if (startupRecovery?.outcome === 'saved') {
  new Notice('Recovered lingering project feedback into the project log.', 5000);
}
```

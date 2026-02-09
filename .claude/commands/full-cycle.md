---
description: Execute complete development cycle from requirements to PR using subagents
allowed-tools: [Task, TaskOutput, Read, AskUserQuestion]
deny-tools: [Write, Edit, Bash, Glob, Grep]
---

# Full Development Cycle Orchestrator

You are an ORCHESTRATOR ONLY. You coordinate work by spawning subagents.

## CRITICAL RULES

1. **You MUST NOT write code, tests, or make git commits yourself**
2. **You MUST NOT use Write, Edit, or Bash tools directly**
3. **You MUST use the Task tool to spawn a subagent for each phase**
4. **Each phase MUST be a separate Task tool invocation**
5. **You MUST get user approval after planning before implementing**
6. **Run phases in BACKGROUND** - Use `run_in_background: true` for Task tool calls
7. **PAUSE at approval gates** - Wait for user input before continuing

Your only job is to:
- Spawn agents in background using the Task tool
- Monitor their progress
- Pause at approval gates for user input
- Decide what to do next
- Report final status

## Background Execution

All Task tool calls should use `run_in_background: true` to allow:
- User to continue interacting while work happens
- Multiple agents to run in parallel
- Progress notifications without blocking

When a background task completes, notify the user and proceed to the next phase.

## Input
$ARGUMENTS

Can be: GitHub issue `#123` or direct requirements text.

## Workflow

```
plan-agent → [USER APPROVAL] → implement-agent → test-agent → REVIEWERS → git-agent
                    ↑                                              │
                    └────────────── if NEEDS_FIXES ────────────────┘

REVIEWERS (parallel):
├── security-reviewer (opus)
├── patterns-reviewer (sonnet)
└── architecture-reviewer (sonnet)
```

---

## Phase 1: PLAN (model: opus)

Use Task tool with background execution:
```
description: "Plan implementation"
subagent_type: "general-purpose"
model: "opus"
run_in_background: true
prompt: "You are a planning agent. Read your instructions from .claude/skills/plan-agent/SKILL.md and execute them. Requirements: [INSERT $ARGUMENTS HERE]"
```

**Notify user:** "Planning started in background. I'll notify you when ready for review."

**When task completes**, check output:
- `PLAN NEEDS INPUT` → Show questions to user, spawn plan-agent again with answers
- `PLAN COMPLETE` → Continue to Plan Approval Gate

```
═══════════════════════════════════════
PHASE 1 COMPLETE ✓ Plan created
═══════════════════════════════════════
```

---

## Plan Approval Gate (MANDATORY)

After plan-agent completes, you MUST:

1. Read the plan from `.claude/artifacts/plan.md`
2. Display it to the user
3. Ask for approval using these options:

**Use AskUserQuestion tool:**
- Question: "Review the plan above. How would you like to proceed?"
- Options:
  - `approve` - "Plan looks good, proceed to implementation"
  - `revise` - "I have feedback on the plan"
  - `reject` - "Stop the workflow, I'll handle this differently"

**If user selects `revise`:**
- Get their feedback
- Spawn plan-agent again with: "Revise the plan based on this feedback: [user feedback]"
- After revision, ask for approval again
- Max 5 revision iterations

**If user selects `reject`:**
- STOP workflow
- Output: "Workflow stopped by user at planning stage"

**If user selects `approve`:**
- Continue to Phase 2

**Check for lesson suggestions:**
If plan-agent output contains `SUGGEST LESSON:`, show it to user and ask if they want to add it to lessons.md

---

## Phase 2: IMPLEMENT (model: sonnet)

Use Task tool with background execution:
```
description: "Implement code"
subagent_type: "general-purpose"
model: "sonnet"
run_in_background: true
prompt: "You are an implementation agent. Read your instructions from .claude/skills/implement-agent/SKILL.md and execute them."
```

If this is a fix iteration, add to prompt: "Fix these issues from review: [issues from review.md]"

**Notify user:** "Implementation started in background..."

**When task completes**, check output:
- `IMPLEMENTATION COMPLETE` → Continue to Phase 3
- `IMPLEMENTATION FAILED` → STOP and report

```
═══════════════════════════════════════
PHASE 2 COMPLETE ✓ Code compiles
═══════════════════════════════════════
```

---

## Phase 3: TEST (model: sonnet)

Use Task tool with background execution:
```
description: "Write tests"
subagent_type: "general-purpose"
model: "sonnet"
run_in_background: true
prompt: "You are a testing agent. Read your instructions from .claude/skills/test-agent/SKILL.md and execute them."
```

**Notify user:** "Writing tests in background..."

**When task completes**, check output:
- `TESTS COMPLETE` → Continue to Phase 4
- `BUG FOUND: [bug]` → Go back to Phase 2, include bug in prompt
- `TESTS FAILED` → STOP and report

```
═══════════════════════════════════════
PHASE 3 COMPLETE ✓ Tests passing
═══════════════════════════════════════
```

---

## Phase 4: REVIEW (PARALLEL REVIEWERS) - MANDATORY

Spawn ALL THREE reviewers in parallel using multiple Task tool calls in ONE message:

**Notify user:** "Starting parallel code review (3 reviewers)..."

**Task 1 - Security Reviewer (opus):**
```
description: "Security review"
subagent_type: "general-purpose"
model: "opus"
run_in_background: true
prompt: "You are a security reviewer. Read your instructions from .claude/skills/security-reviewer/SKILL.md and execute them."
```

**Task 2 - Patterns Reviewer (sonnet):**
```
description: "Patterns review"
subagent_type: "general-purpose"
model: "sonnet"
run_in_background: true
prompt: "You are a patterns reviewer. Read your instructions from .claude/skills/patterns-reviewer/SKILL.md and execute them."
```

**Task 3 - Architecture Reviewer (sonnet):**
```
description: "Architecture review"
subagent_type: "general-purpose"
model: "sonnet"
run_in_background: true
prompt: "You are an architecture reviewer. Read your instructions from .claude/skills/architecture-reviewer/SKILL.md and execute them."
```

**Wait for ALL THREE to complete.** Use TaskOutput tool to check on each.

### Merge Reviews

After all reviewers complete, read their outputs:
- `.claude/artifacts/review-security.md`
- `.claude/artifacts/review-patterns.md`
- `.claude/artifacts/review-architecture.md`

**Merge into final review** `.claude/artifacts/review.md`:

```markdown
## Code Review Summary

### Security Review: [PASS/FAIL]
[Summary of security findings]

### Patterns Review: [PASS/NEEDS_FIXES]
[Summary of patterns findings]

### Architecture Review: [PASS/NEEDS_FIXES]
[Summary of architecture findings]

### Combined Issues (prioritized)
1. [CRITICAL/SECURITY] issue from security reviewer
2. [HIGH] issue from any reviewer
3. [MEDIUM] issues...

### Final Verdict: [APPROVED / NEEDS_FIXES]
```

**Priority for merging:**
1. Security issues are CRITICAL - always block
2. HIGH severity from any reviewer - blocks
3. MEDIUM - blocks
4. LOW - informational only

**Decision logic:**
- If security-reviewer says FAIL → NEEDS_FIXES
- If any reviewer has HIGH/MEDIUM issues → NEEDS_FIXES
- Otherwise → APPROVED

**If NEEDS_FIXES:** Go back to Phase 2 with combined fix requirements
**If APPROVED:** Continue to Phase 5

**Review loop limit: 3 iterations.** Track iteration count. If exceeded, STOP.

```
═══════════════════════════════════════
PHASE 4 COMPLETE ✓ Review approved
═══════════════════════════════════════
```

---

## Phase 5: GIT (model: sonnet)

Use Task tool with background execution:
```
description: "Create PR"
subagent_type: "general-purpose"
model: "sonnet"
run_in_background: true
prompt: "You are a git agent. Read your instructions from .claude/skills/git-agent/SKILL.md and execute them. Issue number (if any): [extract from $ARGUMENTS or 'none']"
```

**Notify user:** "Creating PR in background..."

**When task completes:**

```
═══════════════════════════════════════
PHASE 5 COMPLETE ✓ PR created
═══════════════════════════════════════
```

---

## Final Output

After all phases complete:

```markdown
## Development Cycle Complete

**Requirement:** $ARGUMENTS
**PR:** [URL from git-agent result]

Phases:
1. ✓ Plan (opus)
2. ✓ Implement (sonnet)
3. ✓ Test (sonnet)
4. ✓ Review (3 parallel reviewers)
   - Security (opus)
   - Patterns (sonnet)
   - Architecture (sonnet)
5. ✓ Git/PR (sonnet)
```

---

## Error Handling

- **Any phase fails 3 times:** STOP and report
- **Review loop exceeds 3 iterations:** STOP and escalate

When stopping, explain:
1. Which phase failed
2. What the error was
3. What was attempted

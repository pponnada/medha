# Implementation Plan

## Mastery-Based Mathematics Learning System

---

# 1. Technology Stack

```text
Language       ClojureScript via Scittle (no build step)
Storage        IndexedDB (student data, settings)
Content        EDN files on GitHub Pages
PWA            Service Worker + Web App Manifest
Visualisation  Desmos Calculator API (cached offline)
Publishing     GitHub REST API (content authoring)
Hosting        GitHub Pages
```

---

# 2. Project Structure

```text
medha/
├── index.html               ← entry point; loads Scittle + app
├── manifest.json            ← PWA manifest
├── sw.js                    ← service worker (plain JS)
├── src/
│   ├── app.cljs             ← bootstrap, routing, mode switching
│   ├── db.cljs              ← IndexedDB wrapper (all reads/writes)
│   ├── curriculum.cljs      ← EDN fetch, parse, cache
│   ├── router.cljs          ← client-side routing
│   ├── student/
│   │   ├── lesson.cljs      ← Pólya phase rendering
│   │   ├── desmos.cljs      ← Desmos Calculator integration
│   │   ├── responses.cljs   ← all response type components
│   │   ├── hints.cljs       ← layered hint system
│   │   ├── timer.cljs       ← time tracking + idle detection
│   │   ├── mastery.cljs     ← mastery formula + skill scoring
│   │   ├── reviews.cljs     ← SM-2 scheduling + daily queue
│   │   ├── achievements.cljs← badge evaluation + storage
│   │   └── export.cljs      ← JSON export
│   ├── teacher/
│   │   ├── dashboard.cljs   ← all dashboard panels
│   │   ├── pending.cljs     ← pending review queue + grading
│   │   ├── authoring.cljs   ← lesson seed form
│   │   ├── prompts.cljs     ← AI prompt generator
│   │   ├── importer.cljs    ← AI output validation + import
│   │   ├── skills.cljs      ← skill management UI
│   │   ├── github.cljs      ← GitHub API commit flow
│   │   └── storage.cljs     ← storage monitor + prune flow
│   └── components/
│       ├── chips.cljs        ← observation chips
│       ├── sentence.cljs     ← sentence starters
│       ├── dragdrop.cljs     ← drag and drop
│       ├── confidence.cljs   ← confidence rating widget
│       └── pin.cljs          ← PIN entry + setup
├── content/
│   ├── curriculum.edn        ← sample curriculum
│   ├── skills.edn            ← skill definitions
│   ├── heuristics.edn        ← heuristic library
│   └── templates.edn         ← lesson templates
└── docs/
    ├── requirements.md
    └── implementation-plan.md
```

---

# 3. IndexedDB Schema

All stores use auto-incremented integer keys unless noted.

---

## Student

```clojure
{:id          ; auto
 :name        ; string — display name only, no credentials
 :created-at} ; timestamp
```

---

## Attempt

```clojure
{:id                   ; auto
 :student-id
 :problem-id
 :skill-ids            ; vector — skills this problem maps to
 :started-at           ; timestamp
 :submitted-at         ; timestamp
 :active-duration      ; ms — excludes idle time
 :hint-count           ; integer
 :worked-example-used  ; boolean
 :confidence           ; :sure | :unsure | :guessing | nil
 :correct              ; boolean | nil (pending review)
 :grade                ; :correct | :partially-correct | :incorrect | nil
 :content-version}     ; string — version of lesson at time of attempt
```

---

## Response

```clojure
{:id           ; auto
 :attempt-id
 :question-id
 :type         ; :multiple-choice | :observation-chips | :sentence-starter
               ;   | :short-text | :long-text | :drag-drop
 :value        ; the student's response (type-dependent)
 :grade        ; :correct | :partially-correct | :incorrect | nil
 :graded-by   ; :auto | :teacher | nil
 :graded-at   ; timestamp | nil
 :pending-review} ; boolean
```

---

## SkillMastery

```clojure
{:id                  ; auto
 :student-id
 :skill-id
 :score               ; 0–100
 :correctness-rate    ; float 0–1
 :consistency-bonus   ; float 0–1
 :independence-factor ; float 0–1
 :speed-factor        ; float 0–1
 :attempt-count       ; includes lesson + quiz attempts
 :last-updated}       ; timestamp
```

---

## ReviewItem

```clojure
{:id              ; auto
 :student-id
 :skill-id
 :ease-factor     ; float, initial 2.5, range 1.3–2.5
 :interval        ; days until next review
 :next-review-date; date
 :last-reviewed   ; timestamp
 :failure-count}  ; integer
```

---

## LessonProgress

```clojure
{:id              ; auto
 :student-id
 :lesson-id
 :started-at      ; timestamp
 :completed-at    ; timestamp | nil
 :active-duration ; ms
 :content-version ; string
 :phase-progress} ; map of phase → :not-started | :in-progress | :complete
```

---

## QuizAttempt

```clojure
{:id              ; auto
 :student-id
 :quiz-id
 :started-at      ; timestamp
 :completed-at    ; timestamp | nil
 :all-graded      ; boolean — false if any response pending review
 :mastery-updates}; map of skill-id → new score, populated after grading
```

---

## Analytics

```clojure
{:id         ; auto
 :student-id
 :event-type ; keyword — :lesson-started | :hint-used | :mastery-updated | etc.
 :data       ; map — event-specific payload
 :timestamp}
```

---

## Settings

```clojure
{:key   ; string — primary key (not auto)
 :value}; any EDN-serialisable value
```

Keys used:

```text
"pin"               — teacher PIN (hashed)
"github-token"      — personal access token
"github-repo"       — repository URL
"github-branch"     — target branch
"curriculum-url"    — loaded curriculum URL
"desmos-cached"     — boolean
"student-id"        — active student
"prune-last-at"     — timestamp of last prune
```

---

# 4. Routing

Single-page app. Routes:

```text
/                   → student home (curriculum map)
/lesson/:id         → lesson player
/lesson/:id/:phase  → specific Pólya phase
/quiz/:id           → quiz player
/review             → daily review queue
/progress           → student progress view
/teacher            → PIN gate
/teacher/dashboard  → teacher home
/teacher/mastery    → mastery dashboard
/teacher/progress   → progress dashboard
/teacher/struggle   → struggle dashboard
/teacher/reviews    → review dashboard
/teacher/pending    → pending review queue
/teacher/authoring  → authoring home
/teacher/authoring/seed/:id → lesson seed editor
/teacher/skills     → skill management
/teacher/settings   → PIN + GitHub credentials
```

---

# 5. Version 1 — Foundation

**Exit criterion:** a single student can load a curriculum via shared URL, complete a lesson with at least one Desmos activity, and export data as JSON.

---

## 1.1 PWA Shell

- `index.html`: load Scittle, register service worker, mount app root
- `manifest.json`: name, icons, `start_url`, `display: standalone`
- `sw.js`:
  - on install: cache app shell (index.html, Scittle bundle, app scripts)
  - on install: fetch and cache Desmos Calculator JS bundle
  - on activate: delete old caches
  - on fetch: cache-first for app shell and Desmos; network-first with cache fallback for curriculum EDN

Desmos bundle URL to cache:
```text
https://www.desmos.com/api/v1.9/calculator.js
```

Detect whether Desmos bundle is cached on first launch. If not, show one-time prompt:
```text
Some lessons require Desmos.
Connect to the internet once to enable them offline.
```

---

## 1.2 IndexedDB Layer (`db.cljs`)

Implement a thin async wrapper over the browser IndexedDB API.

Expose:

```clojure
(db/get store key)
(db/put store record)
(db/delete store key)
(db/get-all store)
(db/get-by-index store index value)
(db/transaction stores mode f)
```

Create all stores on `onupgradeneeded`.

Handle migrations by versioning the database schema (increment DB version integer when stores change).

---

## 1.3 Curriculum Loading (`curriculum.cljs`)

On first load:
1. Read `"curriculum-url"` from Settings. If absent, show URL entry screen.
2. Fetch `curriculum.edn` from the URL.
3. Parse EDN using Scittle's native `clojure.edn/read-string`.
4. Store parsed curriculum in an in-memory atom.
5. Cache raw EDN bytes via the service worker cache API for offline use.

On subsequent loads:
1. Load from service worker cache immediately.
2. Re-fetch in background if online; update cache on success.

Validate required top-level keys on parse:

```clojure
[:curriculum/id :curriculum/title :curriculum/chapters :curriculum/skills]
```

Show a clear error if validation fails.

---

## 1.4 Student Profile

On first launch after curriculum loads, prompt for student name.

Write a `Student` record to IndexedDB.

Store the student ID in Settings under `"student-id"`.

---

## 1.5 Lesson Rendering (`student/lesson.cljs`)

Render the four Pólya phases in sequence.

Each phase renders:
- Phase header and prompts from the curriculum EDN
- One or more questions
- Navigation: Next / Back

Phase gate: student must submit a response to each question before advancing.

Track `LessonProgress` in IndexedDB: write `started-at` on entry, update `phase-progress` on each phase completion, write `completed-at` on lesson completion.

---

## 1.6 Response Types (`student/responses.cljs`)

Implement all V1 response types:

**Multiple Choice**
- Render options as tappable cards
- Auto-grade on submit against `:correct-answer` from EDN
- Store `Response` with `graded-by: :auto`

**Observation Chips**
- Render chip set from `:chips` field on question EDN
- Allow multi-select
- Store selected chip IDs as response value

**Sentence Starters**
- Render text input pre-populated with `:starter` string from EDN
- Store completed text; mark `pending-review: true`

**Short Text**
- Plain textarea
- Store text; mark `pending-review: true`

**Long Text**
- Larger textarea
- Store text; mark `pending-review: true`

**Drag and Drop**
- Render draggable items and drop targets from EDN
- Auto-grade on submit
- Store final arrangement as response value

---

## 1.7 Desmos Integration (`student/desmos.cljs`)

Load Desmos Calculator from cached bundle.

Each Desmos question in EDN carries:

```clojure
{:desmos/state   ; Desmos calculator state JSON string
 :desmos/mission ; instruction string shown above calculator
 :desmos/readonly-expressions ; list of expression IDs student cannot edit
}
```

On mount: initialise calculator with `:desmos/state`.

Lock readonly expressions.

On question submit: capture current calculator state as JSON and store in response value.

---

## 1.8 Time Tracking (`student/timer.cljs`)

Start a timer on question entry.

Pause timer after 5 minutes of no user interaction (mouse, touch, keyboard).

Resume on next interaction.

On question submit: write `active-duration` (elapsed non-idle ms) to Attempt.

On lesson phase transition: write phase duration to LessonProgress.

---

## 1.9 Hint System (`student/hints.cljs`)

Render hints lazily: show "Get a hint" button.

Each click reveals the next hint in the stack.

Track `hint-count` and `worked-example-used` on the in-progress Attempt.

Decrement `independence-factor` per hint revealed (exact step defined in V3 mastery engine).

---

## 1.10 JSON Export (`student/export.cljs`)

Gather all IndexedDB records for the active student (except Settings).

Serialise to JSON.

Trigger browser file download as `medha-export-YYYY-MM-DD.json`.

Expose from student progress view and teacher dashboard.

---

# 6. Version 2 — Authoring

**Exit criterion:** a teacher can generate a lesson seed, produce a prompt, import AI output, approve content, and publish EDN to GitHub without using Git directly.

---

## 2.1 PIN Gate (`components/pin.cljs`)

On first Teacher Mode access: show PIN setup screen (enter + confirm).

Hash PIN before storing in Settings (use SubtleCrypto SHA-256).

On subsequent access: show PIN entry. Compare hash. Allow entry on match.

Entering Student Mode from Teacher Mode requires no PIN.

---

## 2.2 GitHub Setup Wizard (`teacher/github.cljs`)

Run on first access to Authoring if `"github-token"` is absent from Settings.

Collect:
- Repository URL
- Personal access token
- Target branch (default: `main`)

Store all three in Settings.

Verify token by making a test `GET /repos/{owner}/{repo}` call before saving.

Expose re-run from Teacher Settings.

---

## 2.3 Lesson Seed Form (`teacher/authoring.cljs`)

Fields: Title, Topic, Age, Objective, Example Problems (textarea), Notes.

Save seed to IndexedDB as a draft with a local ID.

---

## 2.4 AI Prompt Generator (`teacher/prompts.cljs`)

For each generation action, construct a prompt string by interpolating the lesson seed fields into a template.

Generation actions and their prompt templates:

```text
Generate Questions        → ask for Easy/Medium/Hard/Challenge questions
                            with JSON schema matching Response EDN format
Generate Hints            → ask for 3-hint stack + worked example per question
Generate Reflection Prompts → ask for prompts per Pólya phase
Generate Quiz             → ask for mastery/transfer/challenge questions
Generate Skills           → ask for skill hierarchy + dependency map
Generate Mastery Rules    → ask for thresholds + mode settings
Generate Desmos Ideas     → ask for visualisation + mission suggestions
Generate Review Problems  → ask for 1-day/1-week/1-month variants
Generate Template         → ask for a new reusable template schema
```

Each action shows a modal with the generated prompt and a "Copy Prompt" button (use Clipboard API).

---

## 2.5 AI Import and Validation (`teacher/importer.cljs`)

Show a textarea for pasting AI output.

On submit:
1. Attempt to parse as JSON.
2. Validate against the expected schema for the generation action type.
3. Show a diff-style preview: valid items in green, invalid items in red.
4. Allow teacher to deselect individual items before approving.
5. On approval: convert valid items to EDN and store as draft content.

Show a clear error message for each invalid item (missing field, wrong type, etc.).

---

## 2.6 Template System (`teacher/authoring.cljs`)

Load templates from `content/templates.edn`.

When creating a lesson, teacher selects a template.

Template pre-populates:
- Phase structure
- Default response types per phase
- Default heuristics

Teacher edits the populated form; does not touch EDN directly.

---

## 2.7 Skill Management UI (`teacher/skills.cljs`)

List all skills defined in the curriculum EDN.

Allow teacher to:
- Add a new skill (name only)
- Rename a skill
- Delete a skill (only if no problems map to it)
- View which problems map to each skill

Changes are staged locally until published to GitHub.

---

## 2.8 EDN Generation and GitHub Publish (`teacher/github.cljs`)

Convert approved draft content (stored in IndexedDB) to EDN using `pr-str`.

Show EDN preview before publishing.

On "Publish":
1. Read `"github-token"`, `"github-repo"`, `"github-branch"` from Settings.
2. Fetch current file SHA via `GET /repos/{owner}/{repo}/contents/{path}`.
3. Base64-encode the EDN string.
4. Commit via `PUT /repos/{owner}/{repo}/contents/{path}` with `content`, `sha`, `message`.
5. Show confirmation with commit URL on success.
6. Show error with GitHub API message on failure.

---

# 7. Version 3 — Mastery

**Exit criterion:** mastery scores are calculated after each attempt using the defined formula, and the teacher dashboard shows mastery per skill with the pending review queue.

---

## 3.1 Mastery Engine (`student/mastery.cljs`)

Implement the mastery formula:

```text
mastery_score =
  0.40 × correctness_rate
+ 0.25 × consistency_bonus
+ 0.20 × independence_factor
+ 0.15 × speed_factor
```

**correctness_rate**
```text
correct_attempts / total_attempts
```
Includes auto-graded and teacher-graded attempts. Pending-review attempts are excluded until graded.

**consistency_bonus**
```text
sessions_with_correct_answer / total_sessions_attempted
```
A "session" is a calendar day. Group attempts by date, check if any attempt on that day was correct.

**independence_factor**
```text
base = 1.0
subtract 0.2 per hint revealed
set to 0.0 if worked example was revealed
floor at 0.0
average across all attempts
```

**speed_factor**
```text
compute median active_duration across all attempts for this problem
flag as suspicious if active_duration < 0.25 × median
speed_factor = 1.0 for normal attempts, 0.5 for suspicious
average across all attempts
```

Scale final score to 0–100.

Do not calculate until attempt count ≥ 3 (lesson + quiz attempts pooled per skill).

Write updated `SkillMastery` record to IndexedDB after every graded attempt.

---

## 3.2 Progression Rules

After a lesson completes, evaluate each skill mapped to that lesson.

**Strict Mode** (per lesson setting in EDN):
- If any skill score < mastery threshold (default 70): lock next lesson
- Show locked state in curriculum map
- Student may retry the current lesson

**Soft Mode**:
- Always allow progression
- For each skill below threshold: create or update a `ReviewItem`

Mastery threshold is configurable per lesson in EDN (default 70).

---

## 3.3 Teacher Dashboard (`teacher/dashboard.cljs`)

**Mastery Panel**
- List all skills with current score and colour indicator
- Colour: green ≥ 70, amber 40–69, red < 40

**Progress Panel**
- Time spent per chapter (sum of `active-duration` across lessons)

**Struggle Panel**
- Most failed skill (lowest correctness rate)
- Most used hint (hint with highest usage count across all attempts)
- Slowest topic (chapter with highest median active duration per problem)

**Review Panel**
- Reviews due today
- Overdue reviews
- Reviews completed this week

**Pending Review Queue**
- List all responses where `pending-review: true`
- For each: show question text, student response, time spent, hints used
- Teacher selects grade: Correct / Partially Correct / Incorrect
- On submit: update Response, update Attempt grade, trigger mastery recalculation

---

# 8. Version 4 — Review System

**Exit criterion:** skills due for review appear in the student's daily queue, SM-2 intervals update correctly on pass and fail, and the teacher can see the review schedule.

---

## 4.1 SM-2 Implementation (`student/reviews.cljs`)

On skill mastery update:
- If `ReviewItem` does not exist for this skill: create one with `ease-factor: 2.5`, `interval: 1`, `next-review-date: tomorrow`
- If `ReviewItem` exists: update `next-review-date` based on current interval

On review attempt completion:
- If mastery score ≥ 60 (pass):
  ```text
  interval_next = max(1, round(interval × ease_factor))
  ease_factor   = min(2.5, ease_factor + 0.1)
  ```
- If mastery score < 60 (fail):
  ```text
  interval_next = 1
  ease_factor   = max(1.3, ease_factor - 0.2)
  failure_count += 1
  ```
- Cap interval at 180 days
- Write updated `ReviewItem`

---

## 4.2 Daily Review Queue

On student home load, query all `ReviewItem` records where `next-review-date ≤ today`.

Sort by priority:
1. Lowest skill mastery score
2. Most recent failure (`failure-count > 0`, sorted by `last-reviewed` ascending)
3. Oldest `next-review-date`

Cap at 10 items per day.

Interleave: do not show two problems from the same skill consecutively.

Present each review as a problem from the lesson that originally introduced the skill. Pull from the problem bank for that skill.

---

## 4.3 Review Dashboard (Teacher)

Show per skill:
- Next review date
- Current interval
- Ease factor
- Failure count

Show aggregate:
- Reviews due today
- Reviews overdue
- Reviews completed this week (from Analytics events)

---

# 9. Version 5 — Analytics

**Exit criterion:** the teacher dashboard surfaces at least three automatically generated insights without requiring raw data inspection.

---

## 9.1 Analytics Event Recording

Write an `Analytics` record for every significant event:

```text
:lesson-started
:lesson-completed
:hint-used
:worked-example-used
:mastery-updated
:review-completed
:review-failed
:achievement-unlocked
:quiz-completed
```

Each record carries a `data` map with event-specific fields (skill ID, score, problem ID, etc.).

---

## 9.2 Insight Generation

On teacher dashboard load, compute the following from raw Analytics + SkillMastery + Attempt data:

**Rushing Detection**
```text
flag problems where active_duration < 0.25 × median_duration for that problem
if flagged_count / total_attempts > 0.3 → "Student rushes through easy problems"
```

**Struggle Detection**
```text
skill with lowest consistency_bonus AND lowest correctness_rate
→ "Student struggles with [skill name]"
```

**Time Concentration**
```text
chapter with highest total active_duration
→ "Student spends longest on [chapter name]"
```

**Mastery Trend**
```text
compare average SkillMastery score this month vs last month
if delta < -5 → "Mastery declined over last month"
```

Present insights as plain-language summary cards at the top of the teacher dashboard. No raw tables required.

---

## 9.3 Achievements (`student/achievements.cljs`)

Evaluate achievement conditions after each relevant event:

```text
first-mastery-80       → any SkillMastery score first exceeds 80
no-hints-solve         → Attempt submitted with hint-count = 0 and correct = true
revision-improvement   → retry of a question scores higher than previous attempt
review-streak-7        → ReviewItem completed on 7 consecutive calendar days
```

On unlock: write Achievement record to IndexedDB, show badge notification in student view.

---

# 10. Version 6 — Optional Future AI

**Exit criterion:** the system can classify a free-text response without teacher review and adjust subsequent problem selection based on recent performance.

---

## 10.1 Automatic Response Classification

Replace keyword pattern-matching with an on-device or API-backed classifier.

Classify Short Text responses as:
```text
Fact | Derived Fact | Conjecture | Misunderstanding
```

Auto-populate grade suggestion for teacher confirmation rather than full automation.

---

## 10.2 Adaptive Sequencing

After each problem, evaluate recent skill performance.

If the student is struggling (correctness rate < 0.4 over last 3 attempts for a skill):
- Insert a remediation problem at a lower difficulty before continuing the fixed sequence

If the student is excelling (correctness rate = 1.0, no hints, fast):
- Skip Easy problems for skills already above 80 mastery

---

# 11. Cross-Cutting Implementation Notes

---

## EDN Content Conventions

All EDN content IDs are namespaced keywords:

```clojure
:curriculum/fractions-grade-5
:lesson/equivalent-fractions
:problem/eq-frac-001
:skill/fraction-comparison
:heuristic/draw-a-picture
:template/area-model
```

Version field on all content items:

```clojure
{:version "1.0.0"
 ...}
```

---

## Offline-First Rules

All reads go to IndexedDB or in-memory atom first.

Network calls are background-only (never block the UI).

Service worker uses cache-first for all static assets and curriculum EDN.

GitHub API calls (publishing) explicitly require internet; show clear error if offline.

---

## No-Build Scittle Conventions

Each `.cljs` file is loaded as a `<script type="application/x-scittle">` tag in `index.html` in dependency order.

Use `defonce` for atoms that should survive hot reload during development.

State management: a single top-level `app-state` atom holds UI state; IndexedDB holds persistent state.

---

## Storage Management

Check `navigator.storage.estimate()` on teacher dashboard load.

If `usage / quota > 0.8`: show warning banner.

Prune flow:
1. Show warning
2. Trigger JSON export (same as student export)
3. On download complete: enable Prune button
4. On Prune: delete `Analytics` and `Attempt` records where `timestamp < 90 days ago`
5. Never delete `SkillMastery`, `ReviewItem`, `LessonProgress`, or `Settings`

---

## Security Notes

GitHub personal access token is stored in IndexedDB under `"github-token"`.

It is read only when making GitHub API calls.

It is never included in JSON exports.

PIN is stored as a SHA-256 hash. Never store the raw PIN.

Neither the token nor the PIN are transmitted anywhere other than their intended endpoints.

---

## Accessibility

All interactive elements must be keyboard-focusable.

Observation Chips and response cards must be operable via Enter/Space.

Desmos calculator embeds are wrapped with a labelled region for screen readers.

Colour is never the sole indicator of state (mastery levels use colour + icon + label).

Touch targets minimum 44×44px throughout.

Test with VoiceOver (iOS) and TalkBack (Android) before each version ships.

---

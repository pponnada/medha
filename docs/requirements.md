# Software Requirements Specification

## Mastery-Based Mathematics Learning System

### (Pólya + Desmos + AI-Assisted Authoring + Offline-First + GitHub Pages + IndexedDB)

---

# 1. Vision

Build a browser-based mathematics learning platform that teaches mathematical thinking using:

* George Pólya's *How to Solve It*
* Interactive Desmos explorations
* Mastery-based progression
* Spaced repetition
* Teacher-authored curriculum
* AI-assisted content generation

The system must:

* run entirely in the browser
* be deployable via GitHub Pages
* work offline after installation
* store student data locally only
* require no authentication
* support ages 10–18
* support arbitrary curricula
* support non-technical teachers

The platform should not primarily teach procedures.

The platform should teach:

* problem solving
* reasoning
* pattern recognition
* mathematical discovery
* reflection
* transfer of knowledge

---

# 2. Core Philosophy

The system is built around:

```text
Problem
    → Skills

Skills
    → Mastery

Mastery
    → Progression

Performance
    → Reviews

Reviews
    → Long-term retention
```

The central object is **skill mastery**, not chapters.

Chapters exist for organization.

Skills drive:

* progression
* remediation
* reviews
* analytics
* long-term retention

---

# 3. High-Level Architecture

## Content Storage

Teacher-created content is stored in GitHub.

Format:

```text
EDN
```

Repository contains:

```text
Curriculum
Chapters
Lessons
Problems
Templates
Skills
Heuristics
Quizzes
Visualizations
```

GitHub Pages serves all content.

---

## Content Versioning

Every content item carries a version field.

```clojure
{:version "1.0.0"}
```

Student progress records the version of the content it was completed against.

If a lesson version changes after a student has started it, the in-progress work is preserved and labelled with the version it was completed on.

The student is not forced to redo completed work when content is updated.

The teacher can see which version of a lesson each attempt was completed on.

---

## Curriculum Distribution

The teacher shares a URL with the student.

The URL points to the curriculum hosted on GitHub Pages.

The student opens the URL in their browser.

The app loads the curriculum from that URL and caches it for offline use.

The curriculum URL is stored locally in IndexedDB after the first load.

On subsequent launches, the app loads from cache and re-fetches in the background if online.

---

## Student Storage

Student-generated data is stored locally only.

Technology:

```text
IndexedDB
```

Contains:

```text
Responses
Attempts
Mastery
Reviews
Analytics
Progress
```

No student data leaves the device.

No backend.

No cloud storage.

No authentication.

---

## Data Export

Student data must be exportable.

Minimum:

```text
JSON
```

Future:

```text
CSV
```

Exports include:

```text
Responses
Attempts
Mastery
Review schedules
Analytics
Progress
```

---

## Runtime

The application is written in ClojureScript using Scittle.

Scittle interprets ClojureScript directly in the browser.

No build step is required.

EDN is the native data format of Clojure and is read directly by the Scittle runtime without any additional parsing library.

---

## Deployment

```text
GitHub Pages
+
PWA
+
IndexedDB
+
Desmos API
+
Scittle (ClojureScript interpreter)
```

No server required.

No build pipeline required.

---

# 4. Users

## Student

Consumes lessons.

Completes:

* explorations
* exercises
* quizzes
* reviews

Progresses through curriculum.

---

## Teacher

Creates:

* curriculum
* lessons
* quizzes
* visualizations
* skill mappings
* mastery rules

Reviews:

* responses
* mastery reports
* progress
* review schedules
* analytics

Teacher and student use the same device and the same browser.

One student profile per browser instance.

One teacher per browser instance.

No accounts. No cloud authentication.

---

## Mode Switching

The app runs in Student Mode by default.

Teacher Mode is accessed via a PIN entry screen.

The teacher sets the PIN on first launch.

PIN is stored locally in IndexedDB.

PIN is not a security boundary — it prevents accidental access, not adversarial access.

```text
Student Mode  →  Enter PIN  →  Teacher Mode
```

Teacher Mode provides access to:

```text
Dashboard

Authoring

Settings
```

Returning to Student Mode requires no PIN.

---

# 5. Curriculum Model

The system is curriculum-agnostic.

Structure:

```text
Curriculum
    Chapter
        Lesson
            Problem
                Question
```

Example:

```text
Mathematics

    Fractions

        Equivalent Fractions

            Problem 1

            Problem 2
```

---

# 6. Educational Framework

Every lesson follows Pólya.

---

## Phase 1

Understand the Problem

Prompts:

```text
What do you know?

What are you trying to find?

Can you restate the problem?
```

---

## Phase 2

Devise a Plan

Prompts:

```text
Can you draw a picture?

Can you make a table?

Can you solve a simpler problem?

Do you notice a pattern?
```

---

## Phase 3

Carry Out the Plan

Student attempts solution.

---

## Phase 4

Look Back

Prompts:

```text
Can you solve it another way?

Will this always work?

What changed?

What stayed the same?
```

---

## Phase–Response Type Mapping

Each Pólya phase permits a specific set of response types.

```text
Understand   → Observation Chips, Sentence Starters, Short Text

Plan         → Observation Chips, Sentence Starters, Multiple Choice

Execute      → Multiple Choice, Short Text, Long Text, Drag and Drop,
               Sentence Starters

Look Back    → Sentence Starters, Short Text, Long Text
```

The teacher may restrict to a subset of permitted types when authoring a question.

Audio is not phase-restricted (future feature).

---

# 7. Exploration Before Evaluation

During:

```text
Understand
Plan
```

The system should avoid immediately classifying responses as right or wrong.

Instead classify as:

```text
Fact

Derived Fact

Conjecture

Misunderstanding
```

---

## Classification Mechanism

Classification is authoring-time, not runtime AI.

During authoring, the teacher maps expected responses to classification labels.

At runtime, the system pattern-matches the student's response against the teacher-defined set.

Pattern matching is keyword or phrase based.

Unmatched responses are classified as Conjecture by default.

There is no backend AI inference at runtime.

---

Preferred reactions:

```text
Interesting.

Tell me more.

How could we test that?
```

Verification occurs later.

---

# 8. Desmos Integration

Desmos is the primary lesson engine.

Not an optional add-on.

Every lesson may include:

```text
Visualization
Simulation
Experiment
Manipulation
Prediction
```

Examples:

```text
Number lines

Area models

Coordinate geometry

Transformations

Pattern tables

Counting grids
```

Students discover ideas through interaction.

---

## Desmos Offline Strategy

The Desmos Calculator JS bundle must be cached by the service worker on first load.

Subsequent offline sessions load Desmos from cache.

No Desmos functionality is lost offline once the bundle is cached.

Lessons that depend on Desmos are not available until the bundle has been cached at least once.

The app must detect whether the Desmos bundle is cached and display a one-time prompt on first launch:

```text
Some lessons require Desmos.
Connect to the internet once to enable them offline.
```

---

## Desmos Constraints

Activities should be mission-oriented.

Bad:

```text
Play with the slider.
```

Good:

```text
Move the slider until area becomes 48.
```

Preferred workflow:

```text
Prediction

↓

Experiment

↓

Explain
```

---

# 9. Reusable Lesson Templates

Lessons are built from templates.

Examples:

```text
Pattern Lesson

Area Model Lesson

Fraction Lesson

Number Line Lesson

Counting Lesson

Coordinate Lesson
```

Teachers should not create custom lesson logic.

Templates should be reusable.

---

## Template Schema

```clojure
{
 :id               ; unique keyword identifier
 :name             ; display name
 :phase-structure  ; which Pólya phases are included
                   ; e.g. [:understand :plan :execute :look-back]
 :response-types   ; allowed response types for this template
                   ; e.g. [:observation-chips :multiple-choice :short-text]
 :desmos-required  ; boolean — true if template requires Desmos
 :default-heuristics ; list of heuristic ids pre-attached to this template
}
```

Example:

```clojure
{
 :id               :area-model
 :name             "Area Model Lesson"
 :phase-structure  [:understand :plan :execute :look-back]
 :response-types   [:observation-chips :multiple-choice :short-text]
 :desmos-required  true
 :default-heuristics [:draw-a-picture :make-a-table]
}
```

---

# 10. Heuristic Library

The system maintains reusable Pólya heuristics.

Examples:

```text
Draw a Picture

Make a Table

Look for a Pattern

Solve a Simpler Problem

Work Backwards

Guess and Check

Use Symmetry

Break into Cases

Count Systematically

Find an Invariant
```

Each heuristic contains:

```text
Prompts

Hints

Reflection Questions
```

---

# 11. Skill Model

Skills are first-class entities.

Examples:

```text
Pattern Recognition

Draw a Picture

Work Backwards

Fraction Comparison

Geometric Reasoning

Algebraic Thinking
```

Every problem maps to one or more skills.

Student mastery range:

```text
0–100
```

Example:

```text
Pattern Recognition 82

Draw a Picture 67

Work Backwards 41
```

---

## Skill Scope

Skills are curriculum-scoped.

Each curriculum defines its own skill set in the curriculum EDN repository.

There is no global skill registry.

The authoring system includes a skill management UI where the teacher can:

```text
Define new skills

Edit skill names

Delete unused skills

Map skills to problems
```

---

# 12. Problem Metadata

Every problem stores:

```clojure
{
 :id
 :title
 :skills
 :heuristics
 :difficulty
 :template
 :version
}
```

Example:

```clojure
{
 :skills [:pattern]
 :heuristics [:look-for-pattern]
 :difficulty 2
 :template :table
}
```

---

# 13. Progression Rules

Progression is configurable.

Two modes:

---

## Strict Mastery

Student cannot continue.

```text
Threshold not met.

Lesson locked.
```

---

## Soft Mastery

Student may continue.

Weak skills are scheduled for review.

```text
Continue.

Review queue updated.
```

Teacher selects mode per lesson.

---

# 14. Student Responses

Supported response types:

```text
Multiple Choice

Observation Chips

Sentence Starters

Short Text

Long Text

Drag and Drop

Audio (future)
```

---

## Observation Chips

Observation Chips are a set of tappable labels presented to the student during the Understand and Plan phases.

Examples:

```text
I see a pattern

The numbers are getting bigger

I'm not sure

This looks like a previous problem

I need to draw this
```

The teacher configures the chip set per lesson during authoring.

Students tap chips to record observations without typing.

Multiple chips may be selected.

Chip selections are stored as part of the student response.

---

## Sentence Starters

Sentence Starters are text inputs pre-populated with a partial phrase.

The student completes the sentence rather than writing from scratch.

Examples:

```text
I notice that...

I think the answer is... because...

This is similar to...

I got stuck when...
```

The teacher defines the starter phrases per question during authoring.

Completed sentences are stored as short text responses and follow the same pending review flow.

---

## Answer Validation

Multiple Choice and Drag and Drop responses are auto-graded against the correct answer defined during authoring.

Short Text and Long Text responses are not auto-graded.

Short Text and Long Text responses are marked:

```text
Pending Teacher Review
```

until the teacher inspects and grades them.

The teacher marks each response as:

```text
Correct

Partially Correct

Incorrect
```

Mastery is not updated for a free-text response until the teacher has reviewed it.

---

Avoid excessive typing.

Target:

```text
80% interaction

20% writing
```

---

# 15. Time Tracking

Track:

## Lesson

```text
Started

Completed

Duration
```

---

## Question

```text
Entered

Submitted

Duration
```

Store every attempt.

Never overwrite history.

---

## Idle Detection

If no user interaction is detected for 5 minutes, the question timer pauses.

The timer resumes on next interaction.

Idle time is not counted toward question duration or the speed factor in mastery calculation.

---

# 16. Quiz System

Quizzes are specialized lessons used to evaluate mastery after a lesson or chapter.

Contain:

```text
Problems

Questions

Mastery updates
```

All attempts preserved.

---

## Quiz Grading

Structured responses (Multiple Choice, Drag and Drop) are auto-graded.

Free-text responses are marked Pending Teacher Review and follow the same review flow as lesson responses.

Mastery is updated after all responses in the quiz are graded.

---

## Quiz Retry Policy

A student may retry a quiz.

Each attempt is stored independently.

Mastery is recalculated after each attempt.

There is no cooldown between attempts.

The teacher may observe all attempts in the dashboard.

---

## Quiz vs Lesson

Quizzes differ from lessons in the following ways:

```text
No Pólya phase structure

No hints

No exploration prompts

No Desmos (unless explicitly included by teacher)

Responses contribute directly to mastery scoring
```

---

# 17. Mastery Calculation

Mastery is based on:

```text
Correctness

Consistency

Independence

Hint usage

Time spent
```

Not correctness alone.

A student requiring many hints should receive lower mastery than a student solving independently.

---

## Mastery Formula

```text
mastery_score =
  0.40 × correctness_rate
+ 0.25 × consistency_bonus
+ 0.20 × independence_factor
+ 0.15 × speed_factor
```

Definitions:

```text
correctness_rate     — fraction of attempts answered correctly

consistency_bonus    — proportion of sessions where the skill was
                       answered correctly (rewards retained knowledge
                       across multiple sessions, not just one sitting)

independence_factor  — 1.0 if no hints used; decreases per hint used;
                       0.0 if worked example was revealed

speed_factor         — penalises unusually fast submissions that
                       suggest guessing; normalised against median
                       time for that problem
```

Mastery score range: 0–100.

Minimum attempts before mastery is calculated: 3.

Attempts from lessons and quizzes are pooled per skill.

Mastery is not calculated until the minimum attempt threshold is met regardless of whether attempts came from lessons, quizzes, or a mix of both.

---

# 18. Hint System

Hints are layered.

Example:

```text
Hint 1

Hint 2

Hint 3

Worked Example
```

Hint usage is tracked.

---

# 19. Spaced Repetition

Review scheduling uses the SM-2 algorithm.

Review scheduling is skill-based.

Each skill stores:

```text
Ease Factor

Interval

Next Review Date
```

---

## SM-2 Parameters

Initial ease factor: 2.5

Ease factor range: 1.3 – 2.5

On successful review:

```text
interval_next = interval × ease_factor
ease_factor   = ease_factor + 0.1
```

On failed review:

```text
interval_next = 1 day
ease_factor   = max(1.3, ease_factor - 0.2)
```

Failure is defined as a mastery score below 60 on the review attempt.

---

Typical progression:

```text
1 day

3 days

7 days

14 days

30 days
```

Failure resets interval to 1 day.

Maximum interval: 180 days.

---

## Daily Review Cap

Prevent overload.

Example:

```text
Maximum 10 reviews/day
```

Priority:

1. weakest skills
2. recent failures
3. oldest reviews

---

# 20. Teacher Dashboard

Teacher should see insights, not raw data.

---

## Mastery Dashboard

Example:

```text
Pattern Recognition 82

Draw Picture 61

Work Backwards 40
```

---

## Progress Dashboard

Example:

```text
Chapter 1 : 45 minutes

Chapter 2 : 2h 10m

Chapter 3 : 4h 35m
```

---

## Struggle Dashboard

Examples:

```text
Most failed skill

Most used hint

Slowest topic
```

---

## Review Dashboard

Examples:

```text
Reviews Due Today

Reviews Overdue

Reviews Completed
```

---

## Pending Review Queue

The dashboard surfaces all free-text responses awaiting teacher review.

For each pending response, the teacher sees:

```text
Question

Student response

Time spent

Hints used
```

The teacher marks the response as:

```text
Correct

Partially Correct

Incorrect
```

Mastery is updated once the review is submitted.

---

## Response Review

Teacher can inspect all responses (auto-graded and manually graded):

```text
Question

Response

Grade

Time spent

Hints used
```

---

# 21. AI-Assisted Authoring System

This is a major subsystem.

The teacher should not create all content manually.

The system should function as an AI-assisted curriculum factory.

---

# 22. Authoring Philosophy

Do not design around:

```text
Teacher creates lessons.
```

Design around:

```text
Teacher curates lessons.
```

AI generates drafts.

Teacher reviews and approves.

Teacher remains final authority.

Teachers are responsible for reviewing all AI-generated content and asserting appropriate rights before distributing it to students.

---

# 23. Authoring Workflow

## Step 1

Teacher creates a lesson seed.

Example:

```text
Title

Topic

Target Age

Learning Objective

Example Problems

Notes
```

Example:

```text
Equivalent Fractions

Age 11

Students should understand that
different fractions can represent
the same quantity.

Example:
Is 1/2 equal to 2/4?

Notes:
Use area models.
```

---

## Step 2

Teacher selects a generation action.

Examples:

```text
Generate Questions

Generate Hints

Generate Reflection Prompts

Generate Quiz

Generate Skills

Generate Mastery Rules

Generate Desmos Ideas

Generate Review Problems

Generate Template
```

---

## Step 3

System generates a prompt.

Example:

```text
Generate 20 questions for students
aged 11.

Topic:
Equivalent Fractions

Generate:

10 easy
5 medium
5 hard

Return JSON using schema...
```

---

## Step 4

Teacher clicks:

```text
Copy Prompt
```

Prompt copied to clipboard.

---

## Step 5

Teacher pastes prompt into any LLM.

Examples:

```text
ChatGPT

Claude

Gemini

Local LLM
```

System remains provider-agnostic.

---

## Step 6

Teacher copies LLM response.

---

## Step 7

Teacher pastes response into import area.

---

## Step 8

System validates and imports.

---

## Step 9

Teacher publishes content to GitHub.

System commits the approved EDN file directly to the curriculum repository using the GitHub API.

No manual Git knowledge required.

---

# 24. AI Generation Targets

The authoring system should support generation of:

---

## Question Banks

Teacher provides:

```text
1–3 example questions
```

AI generates:

```text
Easy

Medium

Hard

Challenge
```

questions.

---

## Hint Stacks

AI generates:

```text
Hint 1

Hint 2

Hint 3

Worked Example
```

---

## Reflection Prompts

AI generates prompts for:

```text
Understand

Plan

Execute

Look Back
```

---

## Heuristic Mapping

AI identifies:

```text
Applicable heuristics

Skills

Reasoning strategies
```

---

## Quiz Generation

AI generates:

```text
Mastery Questions

Transfer Questions

Challenge Questions
```

---

## Remediation Content

AI generates easier review problems for struggling students.

---

## Skill Extraction

AI proposes:

```text
Skill hierarchy

Skill relationships

Dependencies
```

---

## Mastery Rules

AI proposes:

```text
Thresholds

Strict mode settings

Soft mode settings
```

Teacher approves.

---

## Difficulty Calibration

AI classifies content as:

```text
Easy

Medium

Hard

Challenge
```

---

## Desmos Activity Design

AI suggests:

```text
Visualizations

Sliders

Manipulations

Predictions

Experiments
```

---

## Template Creation

If no suitable template exists:

AI proposes a new reusable lesson template.

---

## Review Content

AI generates:

```text
1 day review

1 week review

1 month review
```

content.

---

# 25. Authoring UI

Teacher never edits EDN directly.

Teacher uses forms.

---

## Lesson Seed

Fields:

```text
Title

Topic

Age

Objective

Example Problems

Notes
```

---

## AI Prompt Generator Section

Buttons:

```text
Generate Questions

Generate Hints

Generate Reflection Prompts

Generate Quiz

Generate Skills

Generate Mastery Rules

Generate Desmos Ideas

Generate Review Problems

Generate Template
```

---

Each action generates:

```text
Prompt
```

with:

```text
Copy Prompt
```

button.

---

## AI Import Section

Teacher pastes AI output.

System:

```text
Validate

Preview

Edit

Approve

Import
```

---

## EDN Export

Approved content becomes:

```text
EDN
```

for GitHub storage.

---

## GitHub Publishing

The system commits EDN files to the curriculum repository using the GitHub API.

The teacher does not use Git directly.

---

### Setup Wizard

On first use of the authoring system, a one-time setup wizard runs.

The wizard collects:

```text
GitHub repository URL

GitHub personal access token (with repo write scope)

Target branch (default: main)
```

The token is stored locally in IndexedDB.

The token is never transmitted anywhere except the GitHub API.

The wizard can be re-run from Teacher Settings to update credentials.

---

### Publish Flow

After a teacher approves imported content:

```text
1. System generates EDN from approved content

2. Teacher reviews EDN preview

3. Teacher taps Publish

4. System commits EDN file to the configured repository via GitHub API

5. GitHub Pages rebuilds automatically

6. Confirmation shown with commit URL
```

---

# 26. Offline Requirements

System must be a PWA.

Requirements:

```text
Installable

Offline capable

Cache lessons

Cache assets

Cache content

Cache Desmos Calculator JS bundle
```

Student should continue learning without internet after installation.

The Desmos bundle is cached by the service worker on first online load.

---

# 27. IndexedDB Schema

Required entities:

```text
Student

Attempt

Response

SkillMastery

ReviewItem

LessonProgress

QuizAttempt

Analytics

Settings
```

Notes:

```text
Settings — stores PIN (Teacher Mode) and GitHub token (authoring).
           Never exported in student data export.
```

---

# 28. Storage Management

IndexedDB storage grows over time as all attempts and responses are preserved.

The app monitors storage usage and warns the user when usage exceeds 80% of the browser quota.

The warning is shown in the Teacher Dashboard.

The prune flow is:

```text
1. Warning shown: storage is nearly full

2. Teacher is prompted to download all student data as JSON

3. Download completes

4. Prune button becomes active

5. Teacher taps Prune

6. Raw attempt events older than 90 days are deleted

7. Mastery scores, review schedules, and progress summaries are never pruned
```

The Prune button is not accessible until a data download has been completed in the current session.

---

# 29. Analytics

Store raw data.

Present insights.

Examples:

```text
Student rushes through easy problems.

Student struggles with pattern lessons.

Student spends longest on fractions.

Mastery declined over last month.
```

---

# 30. Student Engagement Risks

The system must actively mitigate the following.

---

## Too Much Typing

Use:

```text
Observation Chips

Multiple Choice

Drag-and-Drop

Sentence Starters
```

---

## Reflection Fatigue

Use:

```text
Reflection Budget

Adaptive Prompts

Hint-Based Heuristics
```

Reflection Budget: a cap on the number of reflection prompts shown per lesson. The teacher sets the budget during authoring (e.g. maximum 3 reflection prompts per lesson). Once the budget is reached, no further reflection prompts are shown for that lesson.

Adaptive Prompts: the system draws from a pool of prompts defined by the teacher rather than repeating the same one. Prompts already answered in the current session are not shown again.

---

## Mastery Lock Frustration

Support:

```text
Strict Mode

Soft Mode
```

---

## Invisible Progress

Provide:

```text
Mastery Charts

Progress History

Achievements
```

Achievements are milestone badges stored locally and displayed in the student view.

Examples:

```text
First skill mastered above 80

Completed a 7-day review streak

Solved a problem with no hints used

Revised an answer and improved the score
```

---

## Desmos Distraction

Use:

```text
Mission-Based Activities

Limited Controls

Prediction → Experiment → Explain
```

---

## Teacher Burnout

Use:

```text
Templates

AI Generation

Reusable Heuristics

Metadata-Driven Lessons
```

---

## Review Overload

Use:

```text
Daily Caps

Prioritization

Interleaving
```

Interleaving: during daily review, skills are not reviewed in blocks (all fraction problems, then all geometry). Problems are shuffled across skill types to strengthen retrieval.

---

## Difficulty Spikes

Use:

```text
Difficulty Ratings

Fixed Ordering

Scaffolding
```

Problems within a lesson are ordered by difficulty rating defined at authoring time.

The sequence is fixed: Easy → Medium → Hard → Challenge.

Adaptive sequencing is not used in this version.

---

## Data Without Insight

Provide:

```text
Automatic Summaries

Trend Analysis

Teacher Recommendations
```

---

## Fear of Failure

Separate:

```text
Exploration

Evaluation
```

Support:

```text
Confidence Ratings

Reward Revision
```

Confidence Ratings: before submitting an answer, the student rates their confidence.

```text
Sure

Unsure

Guessing
```

The rating is stored alongside the response and acts as a soft signal in mastery calculation.

Reward Revision: if a student revises a previous answer and improves their score, the system explicitly acknowledges the improvement. Revision is treated as a positive learning action, not an admission of failure.

---

## Gaming the System

Track:

```text
Hint Usage

Retries

Submission Speed

Independence
```

Mastery should incorporate all factors.

---

# 31. Development Roadmap

## Version 1

Foundation

```text
PWA

GitHub Pages

IndexedDB

Lesson Rendering

Desmos Integration

Response Storage

Time Tracking

JSON Export
```

Done when: a single student can load a curriculum via shared URL, complete a lesson with at least one Desmos activity, and export their data as JSON.

---

## Version 2

Authoring

```text
Lesson Seed UI

Prompt Generator

AI Import

EDN Export

Template System

GitHub Publishing
```

Done when: a teacher can generate a lesson seed, produce a prompt, import AI output, approve content, and publish EDN to GitHub without using Git directly.

---

## Version 3

Mastery

```text
Skill Model

Mastery Engine

Progression Rules

Teacher Dashboard
```

Done when: student mastery scores are calculated after each attempt using the defined formula, and the teacher dashboard shows mastery per skill with pending review queue.

---

## Version 4

Review System

```text
Spaced Repetition

Review Scheduling

Review Dashboard
```

Done when: skills due for review appear in the student's daily queue, SM-2 intervals update correctly on pass and fail, and the teacher can see the review schedule.

---

## Version 5

Analytics

```text
Trend Detection

Teacher Insights

Performance Summaries
```

Done when: the teacher dashboard surfaces at least three automatically generated insights (e.g. slowest skill, most used hint, mastery trend) without requiring raw data inspection.

---

## Version 6

Optional Future AI

```text
Automatic Response Analysis

Hint Generation

Lesson Generation

Adaptive Sequencing
```

Done when: the system can classify a free-text response without teacher review and adjust subsequent problem selection based on recent performance.

---

# 32. Accessibility

The system targets WCAG 2.1 AA compliance.

Requirements:

```text
Keyboard navigable

Screen reader compatible

Sufficient colour contrast (minimum 4.5:1 for text)

Touch targets minimum 44×44px

No reliance on colour alone to convey meaning
```

Age range 10–18 must be considered in font sizing and interaction target sizing.

---

# Final Design Principle

The system is fundamentally:

```text
Teacher Curates
    ↓
AI Drafts
    ↓
Teacher Approves
    ↓
Lessons Produced
    ↓
Student Explores
    ↓
Skills Measured
    ↓
Mastery Updated
    ↓
Reviews Scheduled
    ↓
Long-Term Retention
```

Every major subsystem—authoring, lesson delivery, mastery, progression, analytics, and spaced repetition—should support this loop. The design should optimize for making high-quality lesson creation cheap, reusable, and scalable while preserving teacher control and maintaining complete student privacy through local-only storage.


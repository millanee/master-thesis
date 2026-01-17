# Context-Aware Interventions for Social Media Use (Android / Kotlin)

## Overview
This repository contains the implementation and documentation for my **Master’s thesis project** on **context-aware digital interventions** for social media use.

The thesis investigates how users respond to different intervention types **depending on real-life context**. Instead of asking whether interventions work in general, the focus is on **where and when** specific intervention types are more effective and better accepted.

The system targets **Instagram and TikTok only** and triggers interventions **only in predefined contexts**, in order to avoid interfering with intentional or task-related smartphone use.

**Planned platform:** Android  
**Planned language:** Kotlin  
**Current status:** Implementation not started

---

## Research Question
**In which predefined real-life contexts are different digital intervention types most effective in regulating social media use?**

The project assumes that the same intervention may be perceived and acted upon differently depending on situational context, such as being at home, at work, or before bedtime.

---

## Intervention Concept

### Target Applications
- Instagram
- TikTok

Users explicitly select which apps should be intervened. Interventions are triggered only when:
1. A selected target app is opened, and  
2. A predefined context is detected.

---

### Contexts
The system focuses on **non-intrusive context detection** and supports the following contexts:

- **Home** (location-based)
- **Office / Work** (location-based)
- **Bedtime** (time-based, user-defined)
- **Traveling**

Context is determined **once at session start**.  
If the context changes during a session, that session is excluded from evaluation to ensure clean comparisons.

Optionally, users may be asked to confirm whether the detected context was correct.

---

## Intervention Types
All interventions are **session-based** and use the same default parameters:

- **Session time limit:** 15 minutes  
- **Extension interval:** 15 minutes  

Limits apply **per session and per context**, not as daily cumulative limits.

---

### 1) Self-Tracking
After the session time limit is reached, an in-app dialog displays usage information and offers:

- **Close app**
- **Dismiss** (extends the session by 15 minutes)

Collected metrics:
- Number of intervention appearances
- Dismissals
- Responsiveness (time until session end)

---

### 2) Design Friction
When a user attempts to open a selected app in a detected context, app entry is delayed by **6 seconds** and accompanied by a short breathing exercise.

After the delay, the user can:
- Continue into the app
- Dismiss the app opening

Collected metrics:
- App opening attempts
- App opening dismissals
- Frequency of app openings

No time-spent metric is used, as the intervention occurs **before** entering the app.

---

### 3) Goal Advancement
Once per day, users are prompted to set a **daily goal**. If dismissed, a default alternative activity prompt is used.

When the session time limit is reached, a reminder of the daily goal (or default prompt) is shown, offering:
- **Close app**
- **Dismiss** (extends the session by 15 minutes)

Collected metrics:
- Dialog appearances
- Dismissals
- Responsiveness

---

## Evaluation
The thesis evaluates **intervention performance and acceptance**, not general improvements in digital wellbeing.

Key measures include:
- Responsiveness to interventions
- User reactance
- Usability (System Usability Scale)

The design intentionally avoids frequent interruptions to reduce intervention fatigue and abandonment.

---

## Study Design
The planned evaluation is a **21-day in-the-wild field study** using a within-subject design.

- Each participant experiences all intervention types
- One intervention type per week
- All contexts active throughout the study
- Intervention order is counterbalanced

No baseline week is included, as the study compares **interventions across contexts**, not intervention vs. no intervention.

---

## Planned Repository Structure
- Android application (Kotlin)
- Context detection module
- Intervention modules
- Session-based time-limit engine
- Logging and data export
- Study and protocol documentation

---

## Status
Implementation has not started yet.  
This repository will be used to iteratively develop the Android prototype required for the Master’s thesis study.

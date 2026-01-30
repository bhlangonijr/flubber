# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Flubber is a Kotlin-based workflow engine for constructing and executing workflows using a JSON DSL. It supports pluggable actions (JavaScript, Python, REST, JSON transforms), coroutine-based async execution, and multi-threaded parallel processing.

## Build Commands

```bash
./gradlew build              # Build the project
./gradlew test               # Run unit tests (excludes integration tests)
./gradlew integrationTest    # Run integration tests only
./gradlew test -Dtest.type=all  # Run all tests

# Run a single test class
./gradlew test --tests "com.github.bhlangonijr.flubber.FlowEngineTest"

# Run a single test method
./gradlew test --tests "com.github.bhlangonijr.flubber.FlowEngineTest.test running full script"
```

JVM target: Java 17, Kotlin 1.9.10.

## Architecture

**Execution flow:** `Script.from(json)` parses DSL → `script.with(args)` creates a `Context` → `FlowEngine.run(context)` executes asynchronously via Kotlin coroutines.

**Key packages** under `com.github.bhlangonijr.flubber`:

- **`FlowEngine`** — Main orchestrator. Coroutine-based, manages thread stacks, sequences, callbacks, and parallel execution.
- **`script/`** — `Script` parses the JSON DSL and registers actions. Field name constants defined here.
- **`action/`** — Pluggable `Action` interface (`execute(context, args) → Any?`). Built-in actions: `expression` (conditionals), `rest` (HTTP), `run` (call sequence), `forEach` (parallel/serial iteration), `menu`, `exit`, `javascript`, `python`, `json` (Jolt transforms).
- **`context/`** — `Context` holds execution state with Mutex for thread safety. Tracks execution state (NEW/RUNNING/WAITING/FINISHED), call stack frames, and parent-child thread relationships.
- **`util/`** — Variable binding (`{{path.to.variable}}` mustache-like syntax resolved via JSON Pointer), GraalVM JS/Jython script engine helpers.

**Patterns:** Observer (ContextExecutionListener for state/action callbacks), Factory (Script.from, Actions.from), Strategy (pluggable Action implementations), stack machine for sequence execution.

**DSL structure:** JSON with `id`, `import` (external actions via URL), `flow` (array of sequences with actions/decisions), `hooks` (event handlers), `exceptionally` (error handling).

## Testing

JUnit 5 with `kotlinx.coroutines.runBlocking` for async tests. Test DSL scripts are in `src/test/resources/`.

## GUIDELINES
## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.


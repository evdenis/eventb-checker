# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew build          # Full build + tests
./gradlew test           # Run all tests
./gradlew test --rerun   # Force re-run (skips UP-TO-DATE cache)
./gradlew test --tests "com.eventb.checker.validation.TypeCheckerTest"  # Single test class
./gradlew test --tests "*TypeCheckerTest.carrier*"                      # Single test method (glob)
./gradlew run --args="/path/to/model.zip"                               # Run CLI validator
./gradlew shadowJar      # Fat JAR → build/libs/eventb-checker-*-all.jar
./gradlew installDist    # Distribution → build/install/eventb-checker/
```

Java 21, Kotlin 1.9.22, Gradle 8.5, Shadow 8.3.6. No linter configured.

## Architecture

An Event-B model validator that reads Rodin-exported `.zip` archives and checks them for XML well-formedness, formula syntax, type correctness, and cross-reference integrity.

**Pipeline** (executed by `ProjectValidator`):

```
.zip archive
  → ArchiveImporter (extracts .bum/.buc files)
  → RodinXmlParser → MachineXmlParser / ContextXmlParser (XML → model objects)
  → FormulaValidator (syntax check via Rodin AST parsePredicate/parseExpression/parseAssignment)
  → TypeChecker (type check via Rodin AST typeCheck(ITypeEnvironment) — only runs if no syntax errors)
  → CrossReferenceValidator (SEES/REFINES/EXTENDS targets exist)
  → TextReportFormatter (output)
```

**Key design decisions:**
- All model classes are immutable data classes in `model/EventBProject.kt` (Machine, Context, Event, Invariant, etc.)
- Validation errors have severity levels: `ERROR` (makes `isValid=false`), `WARNING`, `INFO`
- Type errors are `WARNING` severity — models that parse correctly but have type issues still report `isValid=true`
- TypeChecker processes formulas sequentially (axioms then invariants) because each inferred type feeds into subsequent formulas
- TypeChecker scope chain: context carrier sets → axioms → machine (via SEES/REFINES) → invariants → per-event (guards → actions → witnesses)

**Key dependency:** `de.hhu.stups:rodin-eventb-ast:3.8.0` provides `FormulaFactory`, `ITypeEnvironmentBuilder`, and the parse/typeCheck API. No Rodin IDE dependency — just the standalone AST library.

## CI & Distribution

Three approaches let external projects validate Event-B models in CI:

- **Approach A — Reusable workflow** (`.github/workflows/ci.yml`): `workflow_call` trigger builds checker from source via `installDist`, runs against caller's model zips. No release needed.
- **Approach B — Fat JAR release** (`.github/workflows/release.yml`): Tag push (`v*`) builds `shadowJar` and publishes it as a GitHub Release asset (`eventb-checker.jar`).
- **Approach C — Composite action** (`action.yml`): Downloads the release JAR from Approach B and runs it. Simplest for callers (`uses: eventb-org/eventb-checker@v1`).

Release flow: push a `v*` tag → `release.yml` builds fat JAR → creates GitHub Release → `action.yml` consumers can download it.

## Test Structure

- **Unit tests** mirror `src/main` package structure under `src/test`
- **Integration tests** in `integration/`: `EndToEndTest` (synthetic zips) and `RealModelTest` (5 real Rodin-exported models from `src/test/resources/samples/`)
- Tests use JUnit 5 + AssertJ. Model objects are constructed directly in tests (no mocking framework).

## Event-B Domain Concepts

- **Context** (`.buc`): carrier sets, constants, axioms. Can EXTEND other contexts.
- **Machine** (`.bum`): variables, invariants, variant, events. Can SEE contexts and REFINE other machines.
- **Event**: parameters, guards, actions, witnesses. Guards constrain parameter types; actions are assignments.
- Formulas use mathematical notation: `∈` (membership), `∧` (and), `≔` (assignment), `ℤ`/`ℕ` (integer sets), `:∈` (becomes-in).

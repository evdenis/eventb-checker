# eventb-checker

A command-line validator for [Event-B](https://www.event-b.org/) models exported from the [Rodin Platform](https://sourceforge.net/projects/rodin-b-sharp/). It reads `.zip` archives containing `.bum` (machine) and `.buc` (context) files and checks them for correctness without requiring a Rodin installation.

## Checks Performed

| Check | Severity | Description |
|-------|----------|-------------|
| XML well-formedness | ERROR | Validates `.bum`/`.buc` files are well-formed XML |
| Formula syntax | ERROR | Parses predicates, expressions, and assignments using the Rodin AST library |
| Type checking | WARNING | Type-checks formulas against inferred type environments |
| Cross-reference integrity | ERROR | Verifies SEES, REFINES, and EXTENDS targets exist in the project |
| Well-definedness | INFO | Reports non-trivial well-definedness conditions (e.g., division by zero) |
| Dead identifiers | WARNING | Detects declared variables/constants never referenced in any formula |
| Unmodified variables | INFO | Flags variables that are never assigned by any event action |
| INITIALISATION completeness | WARNING | Checks that INITIALISATION assigns all declared machine variables |

A model is reported as **VALID** when there are no ERROR-severity findings. Warnings and info findings are reported but do not affect validity.

## Requirements

- Java 21+

## Build

```bash
./gradlew build
```

## Usage

```bash
./gradlew run --args="/path/to/model.zip"
```

Or build a fat JAR and run it directly:

```bash
./gradlew shadowJar
java -jar build/libs/eventb-checker-1.0.0-all.jar /path/to/model.zip
```

### Options

| Option | Description |
|--------|-------------|
| `--verbose`, `-v` | Show formula text in error output |

### Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Model is valid |
| 1 | Model is invalid (has ERROR-severity findings) |
| 2 | Input error (file not found, not a zip, etc.) |

## Example Output

```
=== Event-B Model Validation Report ===

Files:    1 machine(s), 1 context(s)
Formulas: 5 checked
Errors:   0
Warnings: 0
Info:     1

RESULT: VALID (with warnings)

--- Counter.bum ---
  INFO : [inv2] Well-definedness condition: y ≠ 0
```

## CI Integration

Three approaches are available for validating Event-B models in CI pipelines.

### Approach A: Reusable Workflow

Calls this repo's CI workflow, which builds the checker from source and runs it against your models. No release needed.

```yaml
# .github/workflows/validate.yml
name: Validate models
on: [push, pull_request]

jobs:
  validate:
    uses: eventb-org/eventb-checker/.github/workflows/ci.yml@main
    with:
      model-path: "models/*.zip"
```

| Input | Required | Default | Description |
|-------|----------|---------|-------------|
| `model-path` | yes | — | Glob pattern for `.zip` files |
| `java-version` | no | `"21"` | Java version |
| `checker-ref` | no | `"main"` | Git ref of eventb-checker to build |
| `verbose` | no | `false` | Enable verbose output |

### Approach B: Download Fat JAR from GitHub Release

Tagged releases publish a self-contained `eventb-checker.jar`. Download it directly in your workflow.

```yaml
steps:
  - uses: actions/checkout@v4
  - uses: actions/setup-java@v4
    with:
      distribution: temurin
      java-version: 21
  - run: |
      curl -sL "https://github.com/eventb-org/eventb-checker/releases/latest/download/eventb-checker.jar" -o checker.jar
      for zip in models/*.zip; do
        java -jar checker.jar "$zip"
      done
```

### Approach C: Composite Action

The simplest option — a single `uses:` step that downloads the release JAR and validates your models.

```yaml
steps:
  - uses: actions/checkout@v4
  - uses: eventb-org/eventb-checker@v1
    with:
      model-path: "models/*.zip"
```

| Input | Required | Default | Description |
|-------|----------|---------|-------------|
| `model-path` | yes | — | Glob pattern for `.zip` files |
| `java-version` | no | `"21"` | Java version |
| `checker-version` | no | `"latest"` | Release tag or `"latest"` |
| `verbose` | no | `"false"` | Enable verbose output |

## Testing

```bash
./gradlew test           # Run all tests
./gradlew test --rerun   # Force re-run (skip UP-TO-DATE cache)

# Run a specific test class
./gradlew test --tests "com.eventb.checker.validation.TypeCheckerTest"

# Run a specific test method (glob)
./gradlew test --tests "*WellDefinednessCheckerTest.division*"
```

## Project Structure

```
src/
├── main/kotlin/com/eventb/checker/
│   ├── Main.kt                          # CLI entry point (Clikt)
│   ├── ArchiveImporter.kt               # Zip extraction
│   ├── model/
│   │   └── EventBProject.kt             # Immutable data classes
│   ├── xml/
│   │   ├── RodinXmlParser.kt            # XML parsing dispatch
│   │   ├── MachineXmlParser.kt          # .bum → Machine
│   │   └── ContextXmlParser.kt          # .buc → Context
│   ├── validation/
│   │   ├── ProjectValidator.kt          # Orchestrates the pipeline
│   │   ├── FormulaValidator.kt          # Syntax checking
│   │   ├── TypeChecker.kt              # Type checking
│   │   ├── CrossReferenceValidator.kt   # SEES/REFINES/EXTENDS
│   │   ├── WellDefinednessChecker.kt    # WD condition analysis
│   │   ├── IdentifierAnalyzer.kt        # Dead/unmodified identifiers
│   │   ├── EventCompletenessChecker.kt  # INITIALISATION completeness
│   │   └── ValidationResult.kt          # Error/summary types
│   └── report/
│       └── TextReportFormatter.kt       # Plain-text output
└── test/kotlin/com/eventb/checker/
    ├── integration/
    │   ├── EndToEndTest.kt              # Synthetic zip tests
    │   └── RealModelTest.kt             # Real Rodin-exported models
    └── validation/
        ├── TypeCheckerTest.kt
        ├── FormulaValidatorTest.kt
        ├── CrossReferenceValidatorTest.kt
        ├── WellDefinednessCheckerTest.kt
        ├── IdentifierAnalyzerTest.kt
        └── EventCompletenessCheckerTest.kt
```

## Dependencies

- [rodin-eventb-ast](https://github.com/hhu-stups/probparsers) `3.8.0` — Standalone Rodin AST library for formula parsing and type checking
- [Clikt](https://ajalt.github.io/clikt/) `4.2.2` — Command-line argument parsing
- [JUnit 5](https://junit.org/junit5/) + [AssertJ](https://assertj.github.io/doc/) — Testing

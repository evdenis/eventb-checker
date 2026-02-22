# eventb-checker

A command-line validator for [Event-B](https://www.event-b.org/) models. It reads `.zip` archives or directories containing `.bum` (machine), `.buc` (context), and/or `.eventb` (Camille textual notation) files and checks them for correctness without requiring a Rodin installation.

## Checks Performed

| Check | Severity | Description |
|-------|----------|-------------|
| XML well-formedness | ERROR | Validates `.bum`/`.buc` files are well-formed XML |
| Camille syntax | ERROR | Parses `.eventb` files using the Camille textual notation grammar |
| Formula syntax | ERROR | Parses predicates, expressions, and assignments using the Rodin AST library |
| Type checking | WARNING | Type-checks formulas against inferred type environments |
| Cross-reference integrity | ERROR | Verifies SEES, REFINES, and EXTENDS targets exist in the project |
| Well-definedness | INFO | Reports non-trivial well-definedness conditions (e.g., division by zero) |
| Dead identifiers | WARNING | Detects declared variables/constants never referenced in any formula |
| Unmodified variables | INFO | Flags variables that are never assigned by any event action |
| INITIALISATION completeness | WARNING | Checks that INITIALISATION assigns all declared machine variables |
| Proof status | WARNING | Reports undischarged/broken proof obligations from `.bpr`/`.bpo`/`.bps` files (with `--proofs`) |

A model is reported as **VALID** when there are no ERROR-severity findings. Warnings and info findings are reported but do not affect validity.

## Requirements

- Java 21+

## Build

```bash
./gradlew build
```

## Usage

The checker accepts either a `.zip` archive or a directory of `.bum`/`.buc`/`.eventb` files:

```bash
./gradlew run --args="/path/to/model.zip"
./gradlew run --args="/path/to/model-directory"
```

Or build a fat JAR and run it directly:

```bash
./gradlew shadowJar
java -jar build/libs/eventb-checker-1.0.0-all.jar /path/to/model.zip
java -jar build/libs/eventb-checker-1.0.0-all.jar /path/to/model-directory
```

### Options

| Option | Description |
|--------|-------------|
| `--verbose`, `-v` | Show formula text in error output |
| `--format`, `-f` | Output format: `text` (default) or `json` |
| `--proofs`, `-p` | Check proof status from `.bpr`/`.bpo`/`.bps` files |

### JSON Output Schema

When using `--format json`, the output has the following structure:

```json
{
  "valid": true,
  "summary": {
    "machineCount": 2,
    "contextCount": 1,
    "formulaCount": 14,
    "errorCount": 0,
    "warningCount": 0,
    "infoCount": 0,
    "proofSummary": {
      "total": 32,
      "discharged": 26,
      "reviewed": 0,
      "pending": 4,
      "unattempted": 2,
      "broken": 0
    }
  },
  "errors": [
    {
      "file": "project/Counter.bum",
      "severity": "ERROR",
      "message": "Parse error in invariant",
      "element": "inv1",
      "formula": "x ==== y"
    }
  ]
}
```

`element` and `formula` are `null` when not applicable. `severity` is one of `ERROR`, `WARNING`, or `INFO`. `proofSummary` is only present when `--proofs` is used.

### Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Model is valid |
| 1 | Model is invalid (has ERROR-severity findings) |
| 2 | Input error (file not found, not a zip/directory, etc.) |

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

## GitHub CI Integration

The simplest option — a single `uses:` step that downloads the release JAR and validates your models.

```yaml
steps:
  - uses: actions/checkout@v6
  - uses: evdenis/eventb-checker@v1
    with:
      model-path: "models/*.zip"
```

| Input | Required | Default | Description |
|-------|----------|---------|-------------|
| `model-path` | yes | — | Glob pattern for `.zip` files |
| `java-version` | no | `"21"` | Java version |
| `checker-version` | no | `"latest"` | Release tag or `"latest"` |
| `verbose` | no | `"false"` | Enable verbose output |

## GitLab CI Integration

External GitLab projects can validate Event-B models by including the reusable template:

```yaml
include:
  - remote: 'https://raw.githubusercontent.com/evdenis/eventb-checker/master/.gitlab/ci/eventb-checker.yml'

variables:
  EVENTB_MODEL_GLOB: "models/*.zip"
```

This creates an `eventb-validate` job that downloads the release JAR, runs validation, and reports results via JUnit XML in the MR widget.

### Configuration Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `EVENTB_MODEL_GLOB` | yes | — | Glob pattern for `.zip` files |
| `EVENTB_JAVA_VERSION` | no | `"21"` | Java version |
| `EVENTB_CHECKER_VERSION` | no | `"latest"` | Release tag or `"latest"` |
| `EVENTB_FORMAT` | no | `"text"` | Output format: `text` or `json` |
| `EVENTB_VERBOSE` | no | `"false"` | Enable verbose output |
| `EVENTB_CHECKER_REPO` | no | `"evdenis/eventb-checker"` | GitHub repo for JAR download |

### Build from Source

To build the checker from source instead of downloading a release JAR:

```yaml
include:
  - remote: 'https://raw.githubusercontent.com/evdenis/eventb-checker/master/.gitlab/ci/eventb-checker.yml'

eventb-validate:
  extends: .eventb-validate-src
  variables:
    EVENTB_MODEL_GLOB: "models/*.zip"
```

### Custom Job Override

Override the concrete job to add your own configuration:

```yaml
include:
  - remote: 'https://raw.githubusercontent.com/evdenis/eventb-checker/master/.gitlab/ci/eventb-checker.yml'

eventb-validate:
  extends: .eventb-validate-jar
  variables:
    EVENTB_MODEL_GLOB: "models/*.zip"
    EVENTB_VERBOSE: "true"
  only:
    - merge_requests
    - master
```

## Dependencies

- [rodin-eventb-ast](https://github.com/hhu-stups/probparsers) — Standalone Rodin AST library for formula parsing and type checking
- [eventbstruct](https://github.com/hhu-stups/probparsers) — Parser for Camille textual notation (`.eventb` files)
- [Clikt](https://ajalt.github.io/clikt/) — Command-line argument parsing
- [JSON-java](https://github.com/stleary/JSON-java) — JSON output formatting
- [JUnit 5](https://junit.org/junit5/) + [AssertJ](https://assertj.github.io/doc/) — Testing

## Acknowledgements

The test suite includes real-world Event-B models from the following repositories:

- [base-model](https://github.com/17451k/base-model) — A base Event-B model
- [eventb-models](https://github.com/17451k/eventb-models) — A collection of Event-B models

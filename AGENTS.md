# Verification commands

- Unit tests: `./mvnw test`
- Targeted mutation feedback: `./mvnw -Pmutation-testing test-compile pitest:mutationCoverage`

# Mutation-testing workflow

- Run the normal tests first, then the mutation profile after changing service behavior, streaming tokenization, or their tests.
- Separate `NO_COVERAGE` execution modes from `SURVIVED` weak assertions. Prioritize externally visible Ollama compatibility, final-chunk selection, timing boundaries, and lossless stream assembly.
- A useful new test must pass on the original and fail on the mutant. Avoid coupling tests to log wording or private implementation solely to move the score.
- Keep the initial score advisory and reject new meaningful survivors in changed code; use broader trend runs outside the pull-request critical path.

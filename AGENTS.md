# Repository Guidelines

## Project Structure & Module Organization
The repository is centered on [`api/`](./api), a Spring Boot 2.3 service built with Maven. Production code lives in `api/src/main/java/com/ke/bella/batch`, organized by `api`, `service`, `db/repo`, `configuration`, `utils`, and `enums`. Tests mirror that structure under `api/src/test/java`. SQL bootstrap and migration scripts are in `api/sql`. Redis Lua scripts are stored in `api/src/main/resources/lua`. Deployment helpers live in `api/deploy/docker` and `api/deploy/local-dev`. jOOQ-generated sources are under `api/src/codegen/java`; treat them as generated artifacts and avoid manual edits unless you are intentionally regenerating them.

## Build, Test, and Development Commands
Run commands from the repository root unless noted.

- `mvn -f api/pom.xml test`: run the JUnit/Spring Boot test suite.
- `mvn -f api/pom.xml package`: build the runnable JAR under `api/target`.
- `mvn -f api/pom.xml jooq-codegen:generate`: regenerate jOOQ classes after schema changes.
- `cd api/deploy/local-dev && ./start.sh`: start MySQL and Redis for IDE-based local development.
- `cd api/deploy/docker && ./start.sh --bella-openapi-host <host> --bella-openapi-key <key>`: boot the full local stack in Docker.

## Coding Style & Naming Conventions
Use Java 11, 4-space indentation, and standard Spring layering. Class names use `PascalCase`; methods and fields use `camelCase`; constants use `UPPER_SNAKE_CASE`. Keep controller names ending in `Controller`, repository classes ending in `Repo`, and tests ending in `Test`. Format Java code with `api/configuration/eclipse-formatter.xml` and import order from `api/configuration/eclipse.importorder`.

## Testing Guidelines
Tests use JUnit with Spring Boot test support and Mockito. Place tests beside the matching package path, for example `api/src/test/java/com/ke/bella/batch/service/QueueServiceTest.java`. Add coverage for queue logic, Redis/Lua integration boundaries, and controller behavior when changing those areas. Run `mvn -f api/pom.xml test` before opening a PR.

## Commit & Pull Request Guidelines
Follow Conventional Commits as documented in [`CONTRIBUTING.md`](./CONTRIBUTING.md): `feat:`, `fix:`, `docs:`, etc. Recent history also uses concise subject lines such as `feat: add ttft/ttlt metrics`. Open PRs from focused branches like `feature/<name>` or `fix/<name>`. PRs should explain the change, note any config or schema impact, link the issue when applicable, and include API examples or screenshots when behavior or docs change.

## Configuration Tips
Local runs require external dependencies and environment variables for Bella OpenAPI, MySQL, Redis, and queue secrets. Use `api/deploy/local-dev/start.sh` to print a working baseline configuration, and keep secrets out of committed files.

# Havoc 1.0.2

Paper 1.21.1 plugin, Java 21, Maven.

## Commands
- `/havoc` or `/hvc`: open the Havoc Hardcore System menu.
- `/havoc reload` or `/hvc reload`: reload `config.yml`.

Requires `havoc.admin` (default: op). Reload is also available from console.

Reloading does not reset an active event's remaining time or its already-started boost amount. New config values apply to future events and current chance base values.

## Build
Run `mvn clean package` with Java 21. GitHub Actions builds the jar and uploads it as an artifact.

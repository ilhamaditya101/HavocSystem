# Havoc 1.1.2

Paper 1.21.1 plugin, Java 21, Maven.

## Commands
- `/havoc` or `/hvc`: open the Havoc Hardcore System menu.
- `/havoc reload` or `/hvc reload`: reload `config.yml`.
- `/shop`: open the native Minecraft Merchant/Villager trading interface while the Barter Shop event is active.
- `/shop edit`: open the admin editor; put payment items in the left slots and result items in the paired right slots, then close to save.
- `/shop stop`: immediately stop the active Barter Shop event, close all open shop trading windows, remove its bossbar/timer, and broadcast the configured stopped announcement.

`/shop edit` and `/shop stop` require `havoc.admin` (default: op). The shop uses Bukkit/Paper Merchant recipes and does not require an external shop plugin. Each offer currently uses one payment item stack and one result item stack.

## Build
Run `mvn clean package` with Java 21. GitHub Actions builds the jar and uploads it as an artifact.

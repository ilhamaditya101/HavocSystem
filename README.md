# Havoc 1.1.0

Paper 1.21.1 plugin, Java 21, Maven.

## Commands
- `/havoc` or `/hvc`: open the admin menu.
- `/hvc reload`: reload config.yml.
- `/shop`: open the barter shop while its event is active (all players).
- `/shop edit`: open the admin trade editor (requires `havoc.admin`).

## Shop editor
For each trade pair, place the required payment item in the left slot and the reward item in the right slot. Close the editor to save. Up to 16 trades. The shop event is started from the Event menu and its duration/messages are configured in config.yml.

## Build
Use Java 21 and run `mvn clean package`. GitHub Actions builds the jar automatically.

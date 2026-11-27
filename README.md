# Havoc 1.1.4

Paper 1.21.1 plugin, Java 21, Maven.

## Commands
- `/havoc` or `/hvc`: open the Havoc menu.
- `/havoc reload` or `/hvc reload`: reload config.
- `/havoc event mending stop` or `/hvc event mending stop`: stop Mending boost.
- `/havoc event totem stop` or `/hvc event totem stop`: stop Totem boost.
- `/havoc tpall <x> <y> <z>`: teleport all online players to coordinates (world of command sender; console uses `teleport.world`).
- `/havoc tpall <player>`: teleport all online players to the online target player's location.
- `/shop`: open the villager-style shop while the event is active.
- `/shop edit`: edit barter offers (admin).
- `/shop stop`: stop the shop event (admin).

## Havoc Teleport
All online players receive configured Darkness and Levitation effects. Then each configured ActionBar message is typed character by character, sequentially. The teleport only happens after the complete message sequence; configured completion Title and Subtitle appear after teleport.

Configure the minimum teleport delay, effects, typing speed, inter-message pause, messages, announcements, and completion title/subtitle in `config.yml`. Teleport waits for both the full message sequence and the configured minimum delay. Typing speed and pauses use ticks (20 ticks = 1 second). Potion amplifier `0` means level I.

## Event controls
Mending and Totem boosts can be stopped by command or from their Stop buttons in the Event menu. Stopping cancels the timer and removes its BossBar.

## Build
Run `mvn clean package` with Java 21, or use the included GitHub Actions workflow.

# Havoc 1.1.3

Paper 1.21.1 plugin, Java 21, Maven.

## Fixes
- Removed an obsolete chest-shop click handler that called the undefined `executeTrade(Player, int)` method.
- `/shop` uses the native Minecraft Merchant/Villager interface and lets Minecraft process merchant trades.
- `/shop edit` remains the chest-style trade editor.
- `/shop stop` stops the event, cancels its countdown, removes the bossbar, and closes active shop viewers.

## Commands
- `/havoc` or `/hvc`: open the Havoc menu.
- `/havoc reload` or `/hvc reload`: reload config.
- `/shop`: open the villager-style shop while the event is active.
- `/shop edit`: edit barter offers (admin).
- `/shop stop`: stop the shop event (admin).

## Build
Run `mvn clean package` with Java 21, or use the included GitHub Actions workflow.

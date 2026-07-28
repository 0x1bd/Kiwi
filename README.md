# Kiwi
Kiwi is a high-performance, client-side autonomous bot for Minecraft 26.1.2+ (Fabric).

> [!IMPORTANT]
> Kiwi is in a very early stage of development. Expect missing features and rough edges.

---

### Pathfinding
Kiwi uses **[Theta\*](https://en.wikipedia.org/wiki/Theta*)**, which allows traversal at any angle rather
than only 0° or 45° as A\* would. On average this is about 13% faster than basic A\* **in 3D space**.

![thetastar-astar-comparison.png](docs/thetastar-astar-comparison.png)
![thetastar-random-map-benchmark.png](docs/thetastar-random-benchmark.png)

## Usage
Download the mod from the [releases page](https://github.com/0x1bd/Kiwi/releases) and place the
`kiwi-x.x.x.jar` into your `mods` directory.

### Commands
All commands are prefixed with `/kiwi`.

| Command                  | Description                                                          |
|--------------------------|----------------------------------------------------------------------|
| `get <item> [count]`     | Obtain an item by any means: gathering, mining, crafting or smelting |
| `get status`             | Report what the bot is currently doing                               |
| `goal xyz \| near \| xz` | Path to a position                                                   |
| `stop`                   | Stop everything                                                      |
| `config`                 | Configure Kiwi                                                       |
| `debug ...`              | Debug stuff                                                          |

## Testing

Fast unit tests:

```
./gradlew test
```

Tests against a real, headless Minecraft client:

```
./gradlew runClientGameTest
```

## Special Thanks
- [Baritone](https://github.com/cabaletta/baritone/) (major inspiration)

## License
Kiwi is licensed under the **[GNU GPLv3](LICENSE.txt)**.

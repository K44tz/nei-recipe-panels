# NEI Recipe Panels

A [NotEnoughItems](https://github.com/GTNewHorizons/NotEnoughItems) add-on for Minecraft
1.7.10 / GT New Horizons.

Adds a button to each recipe in NEI's recipe screen. Clicking it spends a **Recipe
Blueprint** (a craftable consumable) and gives you a **Recipe Panel** carrying that recipe.
Hang the panel on a wall like a painting: it renders the recipe exactly as NEI draws it —
handler background, item grid, arrow, result, counts, animated textures — with the
permutation you had selected frozen in place. Right-click the placed panel, or press the
NEI recipe key over the item, to re-open that recipe in NEI. Craft a panel back into a
blank blueprint to reuse it.

## How the rendering works

NEI's recipe drawing is GUI code. Rather than reimplement it or run it directly in the
world (where item models poke out and layers z-fight), each panel's recipe is drawn once
into an offscreen framebuffer using NEI's own handler + item rendering, and the tile entity
blits that as a single flat textured quad. It re-renders a few times a second so animated
GregTech textures keep ticking; the item slots draw the stacks captured at imprint time so
permutations stay put.

## Configuration (`config/neirecipepanels.cfg`)

- `recipe.registerBlueprintRecipe` — register the built-in blueprint recipe.
- `server.panelMode` — `EVERYONE` / `CREATIVE_ONLY` / `OP_ONLY` / `DISABLED`.
- `server.consumeInCreative` — also spend a blueprint for creative players.
- `server.maxIngredients` / `maxAlternatives` / `maxSnapshotBytes` — server-side caps on
  the recipe data a client may submit.

## Development

```
./gradlew setupDecompWorkspace
./gradlew runClient
```

Requires the JDK named in `.java-version`. NEI is pulled from the GTNH Maven as a dev
dependency (see `dependencies.gradle`).

## License

MIT — see [`LICENSE`](LICENSE).

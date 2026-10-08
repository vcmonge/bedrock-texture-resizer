# Bedrock Texture Resizer

A lightweight JavaFX desktop tool for batch-resizing textures in **Minecraft Bedrock Edition** resource packs. Point it at a resource pack folder, preview every texture on a fast canvas-based grid, and downscale individual or bulk-selected textures with a single click.

![Bedrock Texture Resizer 1.2.0 interface](https://ik.imagekit.io/dmNtb25nZQ/Bedrock%20Texture%20Resizer/BedrockTextureResizer1.2.0.webp)

## Download

A pre-built executable JAR for Windows is available on the
[Releases](https://github.com/vcmonge/mcbe-texture-resizer/releases/latest)
page. It only requires **Java 25** — JavaFX is already bundled inside.

## How It Works

1. **Select** the resource pack's root folder using the **Browse…** button.
2. The app scans the standard `textures/` subdirectories and displays every texture in a tabbed mosaic (Blocks, Items, Environment, Entity).
3. **Resize** textures one by one via the per-card **Resize** button, or enable **Selection Mode** to batch-process multiple textures at once.

> **Warning:** Processed images overwrite the originals in place — back up your resource pack before running the tool.

## Features

| Feature | Details |
|---------|---------|
| **Supported Formats** | PNG and TGA (including alpha channel) |
| **Scanned Paths** | `textures/blocks`, `textures/items`, `textures/environment`, `textures/entity` |
| **Related Textures** | Automatically detects and resizes companion `_mer`, `_mers`, `_normal`, and `_n` maps alongside the base texture. These maps hold data rather than color, so every channel (including alpha, e.g. subsurface in `_mers`) is averaged independently, regardless of the selected alpha mode |
| **Default Algorithm** | *Area Resampling* (pixel-area relation) — produces cleaner results on semi-transparent edges than naive downscaling |
| **Alpha Mode** | Choose how the alpha (transparency) channel is handled during resize: **Binary** (default) forces each pixel to be fully opaque or fully transparent — ideal for cut-out textures like leaves and flowers. **Continuous** preserves the real alpha values — ideal for textures with partial opacity such as ice, water, stained glass, or any semi-transparent surface |
| **TGA Handling** | RGB and alpha channels are processed independently (fixed behavior, suited for most resource-pack TGAs) |
| **Scale Factors** | Divide dimensions by 2, 4, 8, or 16 (selectable in the toolbar) |
| **Batch Selection** | Toggle Selection Mode to pick multiple textures in the active tab and process them all at once |
| **Sorting** | Sort the grid by resolution (ascending or descending) |
| **Performance** | Each tab renders its grid on a single `Canvas` node — the UI stays responsive even with thousands of textures |

## Requirements

- **Java 25** for the Maven fat JAR build.
- **Apache Maven** available as `mvn`.
- Optional: **JDK 8 with JavaFX** for the legacy NetBeans/Ant project setup.

## Running

### Build fat JAR

The recommended distribution build uses Maven Shade and packages JavaFX into a
single Windows fat JAR:

```powershell
mvn clean package
java -jar target/iron-1.2.0-windows.jar
```

The packaged entry point is `view.IronLauncher`, which delegates to the JavaFX
application class `view.Iron`.

See [BUILD.md](BUILD.md) for Maven installation, packaging, and verification
steps.

### Run from NetBeans

1. Open the project in **Apache NetBeans**.
2. **Clean and Build**, then **Run**.

## Project Structure

| Path | Contents |
|------|----------|
| `src/view/` | UI — `Iron.java`, `FXMLInicio.fxml`, `CanvasTextureGrid.java`, stylesheets |
| `src/controller/` | `FXMLInicioController.java` |
| `src/service/` | Texture loading & processing (`TextureService`, `ImageProcessor`, `AreaResampler`) |
| `src/model/` | `TextureInfo` |
| `src/util/` | Constants, TGA utilities, algorithm definitions |

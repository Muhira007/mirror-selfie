
const fs = require('fs');
let code = fs.readFileSync('app/src/main/java/id/mirrorselfie/MainActivity.kt', 'utf8');

// In showViewer(), add camera tool buttons in dock
const oldShutter = 'val shutter=row()';
const newShutter = `// Secondary camera remote tools: Zoom, Torch, Timer, Grid
        val toolsRow = row()
        val zoomOut = action(R.string.zoom_out, "zoomOut", primary = false) { Session.controlCamera("zoomOut") }
        val zoomIn = action(R.string.zoom_in, "zoomIn", primary = false) { Session.controlCamera("zoomIn") }
        val torch = action(R.string.torch, "torch", primary = false) { Session.controlCamera("torch") }
        val timer = action(R.string.timer, "timer", primary = false) { Session.controlCamera("timer") }
        toolsRow.addView(zoomOut, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        toolsRow.addView(zoomIn, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        toolsRow.addView(torch, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        toolsRow.addView(timer, LinearLayout.LayoutParams(0, -2, 1f))
        dock.addView(toolsRow, margins(top = 4))

        val shutter=row()`;

code = code.replace(oldShutter, newShutter);

fs.writeFileSync('app/src/main/java/id/mirrorselfie/MainActivity.kt', code, 'utf8');
console.log('MainActivity patched');


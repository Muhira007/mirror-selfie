package id.mirrorselfie

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText

object RemoteCameraSettings {
    fun show(activity: Activity) {
        val state = Session.state
        if (!state.live || !state.camera.available) return
        val c = state.camera
        val items = mutableListOf<Pair<String, () -> Unit>>()
        fun choose(title: String, labels: List<String>, values: List<String>, action: String, selected: String) {
            AlertDialog.Builder(activity).setTitle(title)
                .setSingleChoiceItems(labels.toTypedArray(), values.indexOf(selected)) { dialog, which ->
                    dialog.dismiss(); Session.controlCamera(action, values[which])
                }.setNegativeButton("Batal", null).show()
        }
        if (c.canConfigure) {
            items += "Mode · ${if (c.mode == "photo") "Foto" else "Video"}" to {
                choose("Mode", listOf("Foto", "Video"), listOf("photo", "video"), "mode", c.mode)
            }
            if (c.videoModes.isNotEmpty()) items += "Video · ${c.videoMode}" to {
                choose("Resolusi & FPS", c.videoModes, c.videoModes, "quality", c.videoMode)
            }
            if (c.isoOptions.isNotEmpty()) items += "ISO · ${if (c.iso == 0) "Auto" else c.iso}" to {
                choose("ISO · Auto mengembalikan ISO & shutter", c.isoOptions.map { if (it == 0) "Auto" else "$it" }, c.isoOptions.map { "$it" }, "iso", "${c.iso}")
            }
            if (c.shutterOptions.isNotEmpty()) items += "Shutter · ${shutterLabel(c.shutterNs)}" to {
                choose("Shutter · Auto mengembalikan ISO & shutter", c.shutterOptions.map { shutterLabel(it) }, c.shutterOptions.map { "$it" }, "shutter", "${c.shutterNs}")
            }
            if (c.manualWb) items += "WB · ${if (c.kelvin == 0) "Auto" else "${c.kelvin} K"}" to {
                val input = EditText(activity).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER
                    hint = "2000–8000 K"; if (c.kelvin > 0) setText("${c.kelvin}")
                    setSelectAllOnFocus(true)
                }
                val dialog = AlertDialog.Builder(activity).setTitle("WB (Kelvin)").setView(input)
                    .setPositiveButton("Terapkan", null).setNeutralButton("Auto") { _, _ -> Session.controlCamera("wb", "0") }
                    .setNegativeButton("Batal", null).create()
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val value = input.text.toString().toIntOrNull()
                        if (value == null || value !in 2000..8000) input.error = "Masukkan 2000–8000"
                        else { Session.controlCamera("wb", "$value"); dialog.dismiss() }
                    }
                }
                dialog.show()
            }
        }
        if (c.focusSupported) items += "Lepas kunci fokus" to { Session.controlCamera("unlockFocus") }
        if (c.exposureLockSupported && c.iso == 0 && c.shutterNs == 0L) items +=
            (if (c.exposureLocked) "Buka kunci pencahayaan" else "Kunci pencahayaan") to { Session.controlCamera("exposureLock", (!c.exposureLocked).toString()) }
        items += "Cara mengatur fokus" to {
            AlertDialog.Builder(activity).setMessage("Ketuk gambar untuk fokus. Tahan untuk mengunci fokus. Ketuk dua kali untuk tampilkan atau sembunyikan kontrol.")
                .setPositiveButton("Oke", null).show()
        }
        AlertDialog.Builder(activity).setTitle(if (c.recording) "Kamera · sedang merekam" else "Kamera")
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
            .setNegativeButton("Tutup", null).show()
    }
}

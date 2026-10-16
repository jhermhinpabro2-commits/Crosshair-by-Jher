package com.jher.crosshair

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Persistent storage ng mga saved crosshair (SharedPreferences + PNG files para sa custom image). */
object Store {
    private const val PREF = "cx"
    private const val KEY = "saved"
    private const val MAX_SAVED = 30

    private fun imgFile(ctx: Context, id: Long) = File(ctx.filesDir, "cx_$id.png")

    fun load(ctx: Context): MutableList<SavedCross> {
        val out = mutableListOf<SavedCross>()
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val sc = SavedCross(o.getLong("id"), o.getInt("preset"), o.getInt("color"), o.getInt("size"), o.optBoolean("img", false))
                if (sc.hasImage) sc.bitmap = BitmapFactory.decodeFile(imgFile(ctx, sc.id).absolutePath)
                out.add(sc)
            }
        } catch (_: Exception) { }
        return out
    }

    private fun write(ctx: Context, list: List<SavedCross>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("preset", it.preset).put("color", it.color).put("size", it.size).put("img", it.hasImage))
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun add(ctx: Context, sc: SavedCross) {
        val bmp = sc.bitmap
        if (sc.hasImage && bmp != null) {
            try { FileOutputStream(imgFile(ctx, sc.id)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (_: Exception) { }
        }
        val list = load(ctx)
        list.add(sc)
        while (list.size > MAX_SAVED) { val old = list.removeAt(0); imgFile(ctx, old.id).delete() }
        write(ctx, list)
    }

    fun remove(ctx: Context, id: Long) {
        val list = load(ctx)
        list.removeAll { it.id == id }
        imgFile(ctx, id).delete()
        write(ctx, list)
    }
}

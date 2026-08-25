package com.domedav.mavjegy.ui.screens

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import org.json.JSONObject
import java.io.File

/**
 * A WebView session (localStorage + sessionStorage + cookie) perzisztálása fájlba,
 * hogy a bejelentkezés még app-újraindítás után is megmaradjon – a WebView példány
 * újrahasználása (tab-váltáskor) mellett.
 */
object WebViewSession {
    private fun file(context: Context) = File(context.filesDir, "web_session.json")

    fun save(context: Context, web: WebView) {
        val js = "(function(){" +
            "var o={l:{},s:{},c:document.cookie};" +
            "try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);o.l[k]=localStorage.getItem(k);}}catch(e){}" +
            "try{for(var i=0;i<sessionStorage.length;i++){var k=sessionStorage.key(i);o.s[k]=sessionStorage.getItem(k);}}catch(e){}" +
            "return o;" +
            "})()"
        web.evaluateJavascript(js) { result ->
            if (!result.isNullOrEmpty() && result != "null") {
                runCatching { file(context).writeText(result) }
            }
        }
        CookieManager.getInstance().flush()
    }

    fun restore(context: Context, web: WebView) {
        val f = file(context)
        if (!f.exists()) return
        val json = runCatching { f.readText() }.getOrNull() ?: return
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return
        val l = obj.optJSONObject("l")
        val s = obj.optJSONObject("s")
        val c = obj.optString("c", "")
        val sb = StringBuilder("(function(){")
        l?.let { keys ->
            val it = keys.keys()
            while (it.hasNext()) {
                val k = it.next()
                val v = keys.optString(k, "")
                sb.append("try{localStorage.setItem(${JSONObject.quote(k)},${JSONObject.quote(v)});}catch(e){}")
            }
        }
        s?.let { keys ->
            val it = keys.keys()
            while (it.hasNext()) {
                val k = it.next()
                val v = keys.optString(k, "")
                sb.append("try{sessionStorage.setItem(${JSONObject.quote(k)},${JSONObject.quote(v)});}catch(e){}")
            }
        }
        sb.append("})()")
        web.evaluateJavascript(sb.toString()) {
            if (c.isNotEmpty()) {
                val cm = CookieManager.getInstance()
                c.split(";").map { it.trim() }.filter { it.isNotEmpty() }
                    .forEach { cm.setCookie(BUY_URL, it) }
                cm.flush()
            }
            // SPA újraindítása, hogy olvassa a visszaállított session-t
            web.reload()
        }
    }
}

import re

with open('autumn-demo/src/wasmJsMain/kotlin/dev/autumn/demo/client/Client.kt', 'r') as f:
    text = f.read()

text = text.replace("import org.w3c.dom.document", "import kotlinx.browser.document") # Reverting if wrong? Actually Wasm has kotlinx.browser provided by `kotlin-dom-api-compat`! Wait no.

# Let's completely mock the DOM classes if we have to, or use standard Kotlin/Wasm syntax.
# First, let's fix js("autumnNavigate") =
text = text.replace("js(\"autumnNavigate\") = { target: String ->", "@JsExport\nfun autumnNavigate(target: String) {")
text = text.replace("    js(\"autumnFilter\") = { query: String ->", "@JsExport\nfun autumnFilter(query: String) {")

# Encode URI Component:
text = text.replace('val encoded = js("window.encodeURIComponent(query)") as String', 'val encoded = encodeURIComponent(query)')

# we need an external fun for encodeUriComponent:
export_func_str = """
external fun encodeURIComponent(uri: String): String
"""
text = text.replace("package dev.autumn.demo.client", "package dev.autumn.demo.client\n" + export_func_str)

with open('autumn-demo/src/wasmJsMain/kotlin/dev/autumn/demo/client/Client.kt', 'w') as f:
    f.write(text)

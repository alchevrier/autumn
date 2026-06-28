import re

with open('autumn-demo/src/wasmJsMain/kotlin/dev/autumn/demo/client/Client.kt', 'r') as f:
    text = f.read()

# Replace kotlinx.browser with org.w3c.dom
text = text.replace("import kotlinx.browser.document", "import org.w3c.dom.document")
text = text.replace("import kotlinx.browser.window", "import org.w3c.dom.window")

# window.fetch to global fetch
text = text.replace("window.fetch", "window.fetch") # fetch is in org.w3c.fetch? Actually it's on window too.

# JS dynamically invoked stuff
text = re.sub(r'window\.asDynamic\(\)\.([^ ]+) =', r'js("\1") =', text) # wait, js(...) cannot be used to set variables in Wasm.
# Let's see what asDynamic is doing.

with open('autumn-demo/src/wasmJsMain/kotlin/dev/autumn/demo/client/Client.kt', 'w') as f:
    f.write(text)


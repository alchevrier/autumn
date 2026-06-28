import re

with open('autumn-demo/src/wasmJsMain/kotlin/dev/autumn/demo/client/Client.kt', 'r') as f:
    text = f.read()

# Strip browser imports
text = text.replace("import kotlinx.browser.document\n", "")
text = text.replace("import kotlinx.browser.window\n", "")
text = text.replace("import org.w3c.dom.HTMLInputElement\n", "")
text = text.replace("as? org.w3c.dom.HTMLInputElement", "as? HTMLInputElement")
text = text.replace("as org.w3c.dom.HTMLInputElement", "as HTMLInputElement")

# Replace JsFetchNetworkClient await logic with the Wasm-compatible Promises
js_fetch_old = """class JsFetchNetworkClient : RawNetworkClient {
    override suspend fun executeRaw(endpoint: String, method: String, requestBody: ByteArray?): Result<ByteArray> {
        return try {
            val response = window.fetch(endpoint).await()
            if (!response.ok) throw Exception("HTTP error: ${response.status}")
            val text = response.text().await()
            Result.success(text.encodeToByteArray())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}"""
js_fetch_new = """class JsFetchNetworkClient : RawNetworkClient {
    override suspend fun executeRaw(endpoint: String, method: String, requestBody: ByteArray?): Result<ByteArray> {
        return kotlin.coroutines.suspendCoroutine { continuation ->
            val promise = window.fetch(endpoint)
            promise.then { response ->
                if (response.ok) {
                    response.text().then { text ->
                        continuation.resumeWith(Result.success(text.toString().encodeToByteArray()))
                        text
                    }
                } else {
                    continuation.resumeWith(Result.failure(Exception("HTTP error: ${response.status}")))
                    response
                }
            }
        }
    }
}"""
text = text.replace(js_fetch_old, js_fetch_new)

# Make main things global so they can be exported
# 1. Strip the fun main() wrapper and inject earlier
text = re.sub(r'fun main\(\) \{\s*', '', text) 

# find the stringRegistryLimits
text = text.replace("val stringRegistryLimits = 0\n\n    // 2. Initialize the Motherboard", """
val stringRegistryLimits = 0

val motherboard = AutumnMotherboard(
    networkClient = JsFetchNetworkClient(),
    stringRegistryBudget = stringRegistryLimits,
    concurrencyBudget = 4,
    epochMatrixBudget = 2,
    configBucketsBudget = 100
)

val binder = DemoCircuitBinder(motherboard)
var filterTimeout: Int = 0

fun main() {
""")

# Wipe the local variables in main we just globalized
text = re.sub(r'val motherboard = AutumnMotherboard\([\s\S]*?configBucketsBudget = bucketConfigLimits\n    \)', '', text)
text = text.replace('val binder = DemoCircuitBinder(motherboard)\n    var filterTimeout: Int = 0', '')

# Replace 
text = text.replace("""    // Bridge JS Native events to Autumn circuit triggers
    js("autumnNavigate") = { target: String ->""", """    
    // Fall back to original file end for main:
    // ...
}

@JsExport
fun autumnNavigate(target: String) {""")

text = text.replace("""    js("autumnFilter") = { query: String ->""", """@JsExport
fun autumnFilter(query: String) {""")

text = text.replace('window.asDynamic().autumnNavigate =', '// no dynamic')
text = text.replace('window.asDynamic().autumnFilter =', '// no dynamic')
text = text.replace('val encoded = js("window.encodeURIComponent(query)") as String', 'val encoded = encodeURIComponent(query)')

text = text.replace('window.setTimeout(', 'window.setTimeout( { -> ')
text = text.replace('}, 300)', '} , 300)')
text = text.replace('onclick="window.autumnNavigate(', 'onclick="autumnNavigate(')
text = text.replace('oninput="window.autumnFilter(', 'oninput="autumnFilter(')

# Add external function encodeURIComponent
text = "external fun encodeURIComponent(uri: String): String\n" + text.replace("import kotlinx.coroutines.launch", "import kotlinx.coroutines.launch\nimport kotlin.coroutines.suspendCoroutine")


# We need to end main properly... Wait, the JS export block replaced `js("autumnNavigate") = {`. This means the `main()` closing brace is right before `@JsExport`. Let's just run it.

with open('autumn-demo/src/wasmJsMain/kotlin/dev/autumn/demo/client/Client.kt', 'w') as f:
    f.write(text)


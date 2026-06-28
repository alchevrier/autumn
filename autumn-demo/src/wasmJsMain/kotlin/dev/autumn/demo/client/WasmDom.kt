package dev.autumn.demo.client

external fun encodeURIComponent(uri: String): String

external interface Response : kotlin.js.JsAny {
    val ok: Boolean
    val status: Int
    fun text(): kotlin.js.Promise<kotlin.js.JsString>
}

external interface Window : kotlin.js.JsAny {
    fun fetch(url: String): kotlin.js.Promise<Response>
    fun setTimeout(handler: () -> Unit, timeout: Int): Int
    fun clearTimeout(handle: Int)
}

external val window: Window

external interface Element : kotlin.js.JsAny {
    var innerHTML: String
    fun focus()
}

external interface HTMLInputElement : Element {
    var value: String
    fun setSelectionRange(selectionStart: Int, selectionEnd: Int)
}

external interface Document : kotlin.js.JsAny {
    fun getElementById(id: String): Element?
}

external val document: Document

external interface Console : kotlin.js.JsAny {
    fun log(msg: String)
    fun error(msg: String)
}

external val console: Console

package dev.autumn.config

class JsonConfigParser : ConfigParser {

    companion object {
        private val RESOURCES_KEY = byteArrayOf('r'.code.toByte(), 'e'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'u'.code.toByte(), 'r'.code.toByte(), 'c'.code.toByte(), 'e'.code.toByte(), 's'.code.toByte())
        private val TYPE_KEY = byteArrayOf('t'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 'e'.code.toByte())
        private val PATH_KEY = byteArrayOf('p'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'h'.code.toByte())
        private val ACTION_KEY = byteArrayOf('a'.code.toByte(), 'c'.code.toByte(), 't'.code.toByte(), 'i'.code.toByte(), 'o'.code.toByte(), 'n'.code.toByte())
    }

    override fun parse(bytes: ByteArray, config: ConfigManager, registry: StringRegistry) {
        registry.setBuffer(bytes)
        
        var i = 0
        val len = bytes.size
        var depth = 0
        var insideResources = false
        var resourcesDepth = -1
        
        var currentResourceId = -2
        var currentTypeId = -2
        var currentPathId = -2
        var currentActionId = -2
        
        while (i < len) {
            val b = bytes[i]
            when (b) {
                '{'.code.toByte() -> {
                    depth++
                    if (insideResources && depth == resourcesDepth + 1) {
                        currentTypeId = -2
                        currentPathId = -2
                        currentActionId = -2
                    }
                    i++
                }
                '}'.code.toByte() -> {
                    if (insideResources && depth == resourcesDepth + 1) {
                        if (currentTypeId != -2 && currentPathId != -2 && currentActionId != -2) {
                            config.defineResource(currentResourceId, currentTypeId, currentPathId, currentActionId)
                        }
                    } else if (insideResources && depth == resourcesDepth) {
                        insideResources = false
                    }
                    depth--
                    i++
                }
                '['.code.toByte() -> { depth++; i++ }
                ']'.code.toByte() -> { depth--; i++ }
                '"'.code.toByte() -> {
                    i++
                    val keyStart = i
                    while (i < len && bytes[i] != '"'.code.toByte()) {
                        if (bytes[i] == '\\'.code.toByte()) i += 2 else i++
                    }
                    val keyEnd = i
                    val keyLen = keyEnd - keyStart
                    i++ // skip closing quote
                    
                    val colonPos = skipToValue(bytes, i)
                    i = colonPos
                    
                    if (bytes[colonPos] == '{'.code.toByte() || bytes[colonPos] == '['.code.toByte()) {
                        // It's an object or array key
                        if (matchesExact(bytes, keyStart, keyLen, RESOURCES_KEY)) {
                            insideResources = true
                            resourcesDepth = depth + 1
                        } else if (insideResources && depth == resourcesDepth) {
                            // This is the resource identifier (e.g. "hero-banner")
                            currentResourceId = registry.register(keyStart, keyLen)
                        }
                    } else {
                        // It's a primitive value
                        if (insideResources && depth == resourcesDepth + 1) {
                            if (matchesExact(bytes, keyStart, keyLen, TYPE_KEY) && i < len && bytes[i] == '"'.code.toByte()) {
                                i++
                                val valStart = i
                                while (i < len && bytes[i] != '"'.code.toByte()) {
                                    if (bytes[i] == '\\'.code.toByte()) i += 2 else i++
                                }
                                currentTypeId = registry.register(valStart, i - valStart)
                                i++
                            } else if (matchesExact(bytes, keyStart, keyLen, PATH_KEY) && i < len && bytes[i] == '"'.code.toByte()) {
                                i++
                                val valStart = i
                                while (i < len && bytes[i] != '"'.code.toByte()) {
                                    if (bytes[i] == '\\'.code.toByte()) i += 2 else i++
                                }
                                currentPathId = registry.register(valStart, i - valStart)
                                i++
                            } else if (matchesExact(bytes, keyStart, keyLen, ACTION_KEY) && i < len && bytes[i] == '"'.code.toByte()) {
                                i++
                                val valStart = i
                                while (i < len && bytes[i] != '"'.code.toByte()) {
                                    if (bytes[i] == '\\'.code.toByte()) i += 2 else i++
                                }
                                currentActionId = registry.register(valStart, i - valStart)
                                i++
                            } else {
                                // Skip unknown value
                                i = skipValue(bytes, i)
                            }
                        } else {
                            // Skip value entirely since we don't care
                            i = skipValue(bytes, i)
                        }
                    }
                }
                else -> { i++ }
            }
        }
    }

    private fun matchesExact(bytes: ByteArray, start: Int, length: Int, target: ByteArray): Boolean {
        if (length != target.size) return false
        for (j in target.indices) {
            if (bytes[start + j] != target[j]) return false
        }
        return true
    }

    private fun skipToValue(bytes: ByteArray, start: Int): Int {
        var i = start
        while (i < bytes.size && (bytes[i] == ':'.code.toByte() || bytes[i] <= 32.toByte())) i++
        return i
    }

    private fun skipValue(bytes: ByteArray, start: Int): Int {
        var i = start
        val len = bytes.size
        if (i < len && bytes[i] == '"'.code.toByte()) {
            i++
            while (i < len && bytes[i] != '"'.code.toByte()) {
                if (bytes[i] == '\\'.code.toByte()) i += 2 else i++
            }
            i++
        } else {
            // Primitives
            while (i < len && bytes[i] != ','.code.toByte() && bytes[i] != '}'.code.toByte() && bytes[i] != ']'.code.toByte() && bytes[i] > 32.toByte()) {
                i++
            }
        }
        return i
    }
}

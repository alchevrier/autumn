package dev.autumn.config

interface ConfigParser {
    fun parse(bytes: ByteArray, config: ConfigManager, registry: StringRegistry)
}

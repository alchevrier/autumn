package dev.autumn.compiler

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import java.io.File

object TopologyExportSerializer {
    
    data class Component(
        val type: String, // "Channel" or "Handler"
        val name: String,
        val channelType: String = "",
        val capacity: Int = 0,
        val cycles: Int = 0,
        val portPressure: String = "",
        val target: String = "" 
    )

    val components = mutableListOf<Component>()

    fun dumpToJson(outputDir: String, messageCollector: MessageCollector? = null) {
        try {
            val dir = File(outputDir)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "topology.json")
            
            val sb = java.lang.StringBuilder()
            sb.append("[\n")
            components.forEachIndexed { index, comp ->
                sb.append("  {\n")
                sb.append("    \"type\": \"${comp.type}\",\n")
                sb.append("    \"name\": \"${comp.name}\",\n")
                sb.append("    \"channelType\": \"${comp.channelType}\",\n")
                sb.append("    \"capacity\": ${comp.capacity},\n")
                sb.append("    \"cycles\": ${comp.cycles},\n")
                sb.append("    \"portPressure\": \"${comp.portPressure}\",\n")
                sb.append("    \"target\": \"${comp.target}\"\n")
                sb.append("  }")
                if (index < components.size - 1) sb.append(",")
                sb.append("\n")
            }
            sb.append("]\n")
            
            file.writeText(sb.toString())
            messageCollector?.report(CompilerMessageSeverity.WARNING, "=== AUTUMN TOPOLOGY JSON EXPORTED TO ${file.absolutePath} with ${components.size} components ===")
        } catch(e: Exception) {
            messageCollector?.report(CompilerMessageSeverity.WARNING, "Failed to dump topology telemetry (safely ignored): ${e.message}")
        }
    }
}

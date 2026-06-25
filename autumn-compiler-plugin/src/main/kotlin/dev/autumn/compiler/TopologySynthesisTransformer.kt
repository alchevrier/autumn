package dev.autumn.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.ir.IrStatement

class TopologySynthesisTransformer(
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector
) : IrElementTransformerVoidWithContext() {

    private val NETWORK_CHANNEL_FQ = FqName("dev.autumn.annotations.NetworkChannel")
    private val COLD_CHANNEL_FQ = FqName("dev.autumn.annotations.ColdChannel")
    private val REGISTER_CHANNEL_FQ = FqName("dev.autumn.annotations.RegisterChannel")

    data class ChannelTopologyInfo(
        val name: String,
        val weight: Int,
        val capacity: Int,
        val type: String
    )

    private val discoveredChannels = mutableListOf<ChannelTopologyInfo>()

    override fun visitPropertyNew(declaration: IrProperty): IrStatement {
        val isNet = declaration.hasAnnotation(NETWORK_CHANNEL_FQ)
        val isCold = declaration.hasAnnotation(COLD_CHANNEL_FQ)
        val isReg = declaration.hasAnnotation(REGISTER_CHANNEL_FQ)

        if (isNet || isCold || isReg) {
            val annot = when {
                isNet -> declaration.getAnnotation(NETWORK_CHANNEL_FQ)
                isCold -> declaration.getAnnotation(COLD_CHANNEL_FQ)
                else -> declaration.getAnnotation(REGISTER_CHANNEL_FQ)
            }
            
            // Extract capacity (Arg 0) and weight (Arg 1)
            val capArgument = annot?.getValueArgument(0) as? IrConst
            val capacity = (capArgument?.value as? Int) ?: 1024

            val weightArgument = annot?.getValueArgument(1) as? IrConst
            val weight = (weightArgument?.value as? Int) ?: when {
                isNet -> 100
                isCold -> 1
                else -> 10
            }

            val type = when {
                isNet -> "HOT_NETWORK"
                isCold -> "COLD_SHARED"
                else -> "REGISTER"
            }

            discoveredChannels.add(ChannelTopologyInfo(declaration.name.asString(), weight, capacity, type))
            
            messageCollector.report(
                CompilerMessageSeverity.INFO,
                "[Autumn Topology] Discovered $type channel '${declaration.name.asString()}' (weight: $weight, capacity: $capacity)"
            )
        }
        return super.visitPropertyNew(declaration)
    }

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        if (declaration.name.asString() == "main") {
            if (discoveredChannels.isNotEmpty()) {
                messageCollector.report(
                    CompilerMessageSeverity.INFO,
                    "[Autumn Topology] Synthesizing Arbiter schedule in main() for ${discoveredChannels.size} channels..."
                )
                
                // --- K2 IR Injection Plan ---
                // 1. Resolve: dev.autumn.scheduler.Arbiter constructor
                // 2. Resolve: arbiter.addChannel(...)
                // 3. Resolve: arbiter.synthesize()
                // 4. Resolve channels properties to pass them dynamically
                // 5. Unroll the event loop or simply inject `arbiter.pollSweep()` inside an infinite run-to-completion loop.
            }
        }
        return super.visitFunctionNew(declaration)
    }
}

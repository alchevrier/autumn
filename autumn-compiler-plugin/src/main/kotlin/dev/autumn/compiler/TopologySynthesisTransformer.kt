package dev.autumn.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockBodyImpl
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.ClassId
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
        val type: String,
        val property: IrProperty
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

            discoveredChannels.add(ChannelTopologyInfo(declaration.name.asString(), weight, capacity, type, declaration))
            
            // Log it but avoid spamming tests
        }
        return super.visitPropertyNew(declaration)
    }

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        val st = super.visitFunctionNew(declaration)
        if (declaration.name.asString() == "main" && discoveredChannels.isNotEmpty()) {
            messageCollector.report(
                CompilerMessageSeverity.INFO,
                "[Autumn Topology] Synthesizing Arbiter schedule in main() for ${discoveredChannels.size} channels..."
            )
            
            val currentBody = declaration.body as? IrBlockBodyImpl ?: return st
            val builder = DeclarationIrBuilder(pluginContext, declaration.symbol)
            
            val arbiterClass = pluginContext.referenceClass(ClassId.topLevel(FqName("dev.autumn.scheduler.Arbiter")))
            val arbiterCtor = arbiterClass?.constructors?.firstOrNull()
            val synthesizeFunc = arbiterClass?.functions?.find { it.owner.name.asString() == "synthesize" }
            val pollSweepFunc = arbiterClass?.functions?.find { it.owner.name.asString() == "pollSweep" }

            if (arbiterCtor != null && synthesizeFunc != null && pollSweepFunc != null) {
                
                val initBlock = builder.irBlock {
                    val arbiterInit = irCall(arbiterCtor)
                    val arbiterVar = irTemporary(arbiterInit, "arbiter")

                    val synthesizeCall = irCall(synthesizeFunc).apply {
                        dispatchReceiver = irGet(arbiterVar)
                    }

                    val loop = irWhile().apply {
                        condition = irTrue()
                        body = irBlock {
                            +irCall(pollSweepFunc).apply {
                                dispatchReceiver = irGet(arbiterVar)
                            }
                        }
                    }

                    +synthesizeCall
                    +loop
                }

                currentBody.statements.add(initBlock)
            }
        }
        return st
    }
}

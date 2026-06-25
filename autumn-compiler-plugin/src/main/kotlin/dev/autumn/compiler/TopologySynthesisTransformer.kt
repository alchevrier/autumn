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

import org.jetbrains.kotlin.ir.types.typeWith

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

    private val discoveredHandlers = mutableListOf<IrFunction>()

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        if (declaration.valueParameters.size == 1) { // Simplify: just find single-parameter functions
            discoveredHandlers.add(declaration)
        }
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

            val addChannelFunc = arbiterClass?.functions?.find { it.owner.name.asString() == "addChannel" }

            if (arbiterCtor != null && synthesizeFunc != null && pollSweepFunc != null && addChannelFunc != null) {
                
                val initBlock = builder.irBlock {
                    val arbiterInit = irCall(arbiterCtor)
                    val arbiterVar = irTemporary(arbiterInit, "arbiter")

                    for (channelInfo in discoveredChannels) {
                        val handler = discoveredHandlers.find { 
                            it.name.asString() == channelInfo.name || 
                            it.name.asString() == "on${channelInfo.name.replaceFirstChar { it.uppercase() }}"
                        }
                        
                        if (handler != null) {
                            // --- ZERO-ALLOCATION INTERFACE ERASURE ---
                            // Aggressively modify the AST node to replace the Object reference with a primitive Int.
                            handler.valueParameters[0].type = pluginContext.irBuiltIns.intType
                            
                            messageCollector.report(
                                CompilerMessageSeverity.INFO,
                                "[Autumn Signature Assassination] Mutated '${handler.name.asString()}' parameter to (Int) to escape JVM Verifier!"
                            )

                            val addChannelCall = irCall(addChannelFunc).apply {
                                dispatchReceiver = irGet(arbiterVar)
                                
                                val propertyGetter = channelInfo.property.getter?.symbol
                                if (propertyGetter != null) {
                                    putValueArgument(0, irCall(propertyGetter))
                                } else {
                                    // Fallback / skip
                                }
                                
                                putValueArgument(1, irInt(channelInfo.weight))
                                
                                val funType = pluginContext.irBuiltIns.functionN(1).typeWith(pluginContext.irBuiltIns.intType, pluginContext.irBuiltIns.unitType)
                                putValueArgument(2, irFunctionReference(funType, handler.symbol))
                            }
                            +addChannelCall
                        }
                    }

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

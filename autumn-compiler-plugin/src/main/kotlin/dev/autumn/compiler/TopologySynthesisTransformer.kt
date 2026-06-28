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
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.types.classOrNull

class TopologySynthesisTransformer(
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector
) : IrElementTransformerVoidWithContext() {

    private val BOUNDARY_CHANNEL_FQ = FqName("dev.autumn.annotations.BoundaryChannel")
    private val COLD_CHANNEL_FQ = FqName("dev.autumn.annotations.ColdChannel")
    private val SESSION_CHANNEL_FQ = FqName("dev.autumn.annotations.SessionChannel")
    private val REGISTER_CHANNEL_FQ = FqName("dev.autumn.annotations.RegisterChannel")

    data class ChannelTopologyInfo(
        val name: String,
        val weight: Int,
        val capacity: Int,
        val type: String,
        val sharded: Int,
        val property: IrProperty
    )

    private val discoveredChannels = mutableListOf<ChannelTopologyInfo>()

    override fun visitPropertyNew(declaration: IrProperty): IrStatement {
        val isNet = declaration.hasAnnotation(BOUNDARY_CHANNEL_FQ)
        val isCold = declaration.hasAnnotation(COLD_CHANNEL_FQ)
        val isSession = declaration.hasAnnotation(SESSION_CHANNEL_FQ)
        val isReg = declaration.hasAnnotation(REGISTER_CHANNEL_FQ) || declaration.hasAnnotation(SESSION_CHANNEL_FQ)

        if (isNet || isCold || isSession || isReg) {
            val annot = when {
                isNet -> declaration.getAnnotation(BOUNDARY_CHANNEL_FQ)
                isCold -> declaration.getAnnotation(COLD_CHANNEL_FQ)
                isSession -> declaration.getAnnotation(SESSION_CHANNEL_FQ)
                else -> declaration.getAnnotation(REGISTER_CHANNEL_FQ)
            }
            
            val capArgument = annot?.getValueArgument(0) as? IrConst
            val capacity = (capArgument?.value as? Int) ?: 1024

            val weightArgument = annot?.getValueArgument(1) as? IrConst
            val weight = (weightArgument?.value as? Int) ?: when {
                isNet -> 100
                isCold -> 1
                isSession -> 10
                else -> 10
            }

            val shardedArgument = annot?.getValueArgument(2) as? IrConst
            val sharded = (shardedArgument?.value as? Int) ?: 1

            val type = when {
                isNet -> "HOT_NETWORK" // Legacy type name, indicates Hot->Hot Boundary
                isCold -> "COLD_SHARED" // Hot->Cold Observatory
                isSession -> "SESSION" // Cold->Hot 
                else -> "REGISTER"
            }

            discoveredChannels.add(ChannelTopologyInfo(declaration.name.asString(), weight, capacity, type, sharded, declaration))
        }
        return super.visitPropertyNew(declaration)
    }

    private val discoveredHandlers = mutableListOf<IrFunction>()

    private val INJECT_TOPOLOGY_FQ = FqName("dev.autumn.annotations.InjectTopology")

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        if (declaration.valueParameters.size == 1) {
            discoveredHandlers.add(declaration)
        }
        val st = super.visitFunctionNew(declaration)
        if (declaration.hasAnnotation(INJECT_TOPOLOGY_FQ) && discoveredChannels.isNotEmpty()) {
            
            val currentBody = declaration.body as? IrBlockBodyImpl ?: return st
            val builder = DeclarationIrBuilder(pluginContext, declaration.symbol)
            
            // Dynamically lookup the right functions against the actual target topology type, typically AutumnChannel OR Channel 
            // We just use AutumnChannel by default since all benchmark pipelines invoke it now
            val channelClass = pluginContext.referenceClass(ClassId.topLevel(FqName("dev.autumn.channel.AutumnChannel")))?.owner ?: pluginContext.referenceClass(ClassId.topLevel(FqName("dev.autumn.channel.Channel")))?.owner
            val pollFunc = channelClass?.functions?.find { it.name.asString() == "poll" }
            val commitPollFunc = channelClass?.functions?.find { it.name.asString() == "commitPoll" }
            val pollPartitionFunc = channelClass?.functions?.find { it.name.asString() == "pollPartition" }
            val commitPollPartitionFunc = channelClass?.functions?.find { it.name.asString() == "commitPollPartition" }
            
            val runtimeClass = pluginContext.referenceClass(ClassId.topLevel(FqName("dev.autumn.channel.AutumnRuntime")))?.owner
            val spawnFunc = runtimeClass?.functions?.find { it.name.asString() == "spawn" }

            messageCollector.report(CompilerMessageSeverity.WARNING, "DEBUG CLASS: ${channelClass?.name?.asString()} POLL FUNC: ${pollFunc?.parent?.let { (it as? org.jetbrains.kotlin.ir.declarations.IrClass)?.name?.asString() }}")
            if (pollFunc != null && commitPollFunc != null && pollPartitionFunc != null && commitPollPartitionFunc != null) {
                val initBlock = builder.irBlock {
                    for (channelInfo in discoveredChannels) {
                        val handler = discoveredHandlers.find { 
                            it.name.asString() == channelInfo.name || 
                            it.name.asString() == "on${channelInfo.name.replaceFirstChar { c -> c.uppercase() }}"
                        }
                        
                        val propertyGetter = channelInfo.property.getter?.symbol
                        
                        if (handler != null && propertyGetter != null) {
                            
                            if (channelInfo.sharded > 1 && spawnFunc != null && runtimeClass != null) {
                                messageCollector.report(CompilerMessageSeverity.INFO, "[Autumn Topology] Injecting sharded multi-threaded pipeline: ${channelInfo.sharded} threads mapped for '${handler.name.asString()}'")
                                for (i in 0 until channelInfo.sharded) {
                                    val lambda = pluginContext.irFactory.buildFun {
                                        name = org.jetbrains.kotlin.name.Name.special("<anonymous>")
                                        returnType = pluginContext.irBuiltIns.unitType
                                        visibility = org.jetbrains.kotlin.descriptors.DescriptorVisibilities.LOCAL
                                        origin = org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
                                    }.apply {
                                        parent = declaration
                                        val lambdaBuilder = DeclarationIrBuilder(pluginContext, symbol)
                                        body = lambdaBuilder.irBlockBody {
                                            val chanValLocal = irTemporary(irCall(propertyGetter))
                                            val pollCall = irCall(pollPartitionFunc.symbol).apply {
                                                dispatchReceiver = irGet(chanValLocal)
                                                putValueArgument(0, irInt(i))
                                            }
                                            val polledIdx = irTemporary(pollCall, "idx_${channelInfo.name}_$i")

                                            val anyHandledVar = irTemporary(irFalse(), "anyHandled", isMutable = true)
                                            
                                            val ifNotMinusOne = irIfThen(type = pluginContext.irBuiltIns.unitType,
                                                condition = irNotEquals(irGet(polledIdx), irInt(-1)),
                                                thenPart = irBlock {
                                                    +irSet(anyHandledVar, irTrue())
                                                    val eventType = handler.valueParameters[0].type
                                                    val targetClass = eventType.classOrNull?.owner as? org.jetbrains.kotlin.ir.declarations.IrClass
                                                    val constructor = targetClass?.constructors?.firstOrNull { it.isPrimary }
                                                    val arg = if (constructor != null) {
                                                        irCall(constructor.symbol).apply { putValueArgument(0, irGet(polledIdx)) }
                                                    } else {
                                                        irGet(polledIdx)
                                                    }
                                                    +irCall(handler.symbol).apply { putValueArgument(0, arg) }
                                                    +irCall(commitPollPartitionFunc.symbol).apply {
                                                        dispatchReceiver = irGet(chanValLocal)
                                                        putValueArgument(0, irInt(i))
                                                    }
                                                }
                                            )
                                            +ifNotMinusOne
                                        }
                                    }
                                    val functionType = pluginContext.irBuiltIns.functionN(0).typeWith(pluginContext.irBuiltIns.unitType)
                                    val lambdaExpr = org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl(
                                        -1, -1, functionType, lambda as org.jetbrains.kotlin.ir.declarations.IrSimpleFunction, org.jetbrains.kotlin.ir.expressions.IrStatementOrigin.LAMBDA
                                    )
                                    val spawnCall = builder.irCall(spawnFunc.symbol).apply {
                                        dispatchReceiver = builder.irGetObject(runtimeClass.symbol)
                                        putValueArgument(0, lambdaExpr)
                                    }
                                    +spawnCall
                                }
                            } else {
                                messageCollector.report(CompilerMessageSeverity.INFO, "[Autumn Topology] Injecting single-threaded static native unroll for '${handler.name.asString()}'")
                                val threadClass = pluginContext.referenceClass(ClassId.topLevel(FqName("java.lang.Thread")))?.owner
                                val yieldFunc = threadClass?.functions?.find { it.name.asString() == "yield" && it.valueParameters.isEmpty() }
                                
                                val chanVal = irTemporary(irCall(propertyGetter))
                                val pollCall = irCall(pollFunc.symbol).apply { dispatchReceiver = irGet(chanVal) }
                                val polledIdx = irTemporary(pollCall, "idx")

                                val ifNotMinusOne = irIfThen(type = pluginContext.irBuiltIns.unitType,
                                    condition = irNotEquals(irGet(polledIdx), irInt(-1)),
                                    thenPart = irBlock {
                                        val eventType = handler.valueParameters[0].type
                                        val targetClass = eventType.classOrNull?.owner as? org.jetbrains.kotlin.ir.declarations.IrClass
                                        val constructor = targetClass?.constructors?.firstOrNull { it.isPrimary }
                                        val arg = if (constructor != null) {
                                            irCall(constructor.symbol).apply { putValueArgument(0, irGet(polledIdx)) }
                                        } else {
                                            irGet(polledIdx)
                                        }
                                        +irCall(handler.symbol).apply { putValueArgument(0, arg) }
                                        +irCall(commitPollFunc.symbol).apply { dispatchReceiver = irGet(chanVal) }
                                    }
                                )
                                +ifNotMinusOne
                            }
                        }
                    }
                }
                currentBody.statements.add(initBlock)

                // Inject Thread.sleep to keep the JVM alive after spawning 
                // Removed because we no longer background spawn
            }
        }
        return st
    }
}
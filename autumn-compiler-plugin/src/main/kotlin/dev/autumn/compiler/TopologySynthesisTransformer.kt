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
    private val SPECULATIVE_FQ = FqName("dev.autumn.annotations.Speculative")
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
                isCold -> 10
                else -> 50
            }

            val typeString = when {
                isNet -> "BoundaryChannel"
                isCold -> "ColdChannel"
                isSession -> "SessionChannel"
                else -> "RegisterChannel"
            }

            val sharded = 1
            
            val fileEntry = declaration.file.fileEntry
            val srcFile = fileEntry.name
            val srcLine = fileEntry.getLineNumber(declaration.startOffset)
            
            discoveredChannels.add(ChannelTopologyInfo(
                name = declaration.name.asString(),
                weight = weight,
                capacity = capacity,
                type = typeString,
                sharded = sharded,
                property = declaration
            ))
            
            // --- INJECT JSON EXPORT FOR CHANNELS ---
            TopologyExportSerializer.components.add(
                TopologyExportSerializer.Component(
                    type = "Channel",
                    name = declaration.name.asString(),
                    channelType = typeString,
                    capacity = capacity,
                    target = "",
                    sourceFile = srcFile,
                    sourceLine = srcLine
                )
            )
            // ---------------------------------------
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

                                    // Telemetry weave
                                    val observeAnnotation = handler.getAnnotation(FqName("dev.autumn.annotations.Observe"))
                                    val hasObserve = observeAnnotation != null
                                    
                                    val observerNameArg = observeAnnotation?.getValueArgument(0) as? IrConst
                                    val targetObserverName = (observerNameArg?.value as? String) ?: "metricsHistogram"
                                    
                                    val clockClass = pluginContext.referenceClass(ClassId.topLevel(FqName("dev.autumn.scheduler.AutumnClock")))?.owner
                                    val nowFunc = clockClass?.functions?.firstOrNull { it.name.asString() == "now" }
                                    val histogramClass = pluginContext.referenceClass(ClassId.topLevel(FqName("dev.autumn.observatory.LatencyHistogram")))?.owner
                                    val recordDeltaFunc = histogramClass?.functions?.firstOrNull { it.name.asString() == "recordDelta" }
                                    
                                    var targetHistogram: IrProperty? = null
                                    declaration.file.declarations.forEach {
                                        if (it is IrProperty && it.hasAnnotation(FqName("dev.autumn.annotations.ObserveChannel"))) {
                                            val chanAnnot = it.getAnnotation(FqName("dev.autumn.annotations.ObserveChannel"))
                                            val chanArg = chanAnnot?.getValueArgument(0) as? IrConst
                                            val chanName = (chanArg?.value as? String) ?: "metricsHistogram"
                                            if (chanName == targetObserverName || it.name.asString() == targetObserverName) {
                                                targetHistogram = it
                                            }
                                        } else if (it is IrProperty && it.name.asString() == targetObserverName) {
                                            // Fallback to exactly matching the property name if the user forgot the annotation
                                            targetHistogram = it
                                        }
                                    }

                                    if (hasObserve && nowFunc != null && recordDeltaFunc != null && targetHistogram != null) {
                                        messageCollector.report(CompilerMessageSeverity.INFO, "[Autumn Observatory] Weaving telemetry around '${handler.name.asString()}' outside capture.")
                                        val rdtscStart = irTemporary(irCall(nowFunc.symbol).apply {
                                            dispatchReceiver = irGetObject(clockClass.symbol)
                                        }, "rdtscStart")
                                        
                                        +irCall(handler.symbol).apply { putValueArgument(0, arg) }
                                        
                                        val rdtscEnd = irCall(nowFunc.symbol).apply {
                                            dispatchReceiver = irGetObject(clockClass.symbol)
                                        }
                                        val longMinus = pluginContext.irBuiltIns.longClass.owner.functions.first { 
                                            it.name.asString() == "minus" && it.valueParameters[0].type == pluginContext.irBuiltIns.longType 
                                        }
                                        val delta = irCall(longMinus.symbol).apply {
                                            dispatchReceiver = rdtscEnd
                                            putValueArgument(0, irGet(rdtscStart))
                                        }
                                        val histogramGetter = targetHistogram.getter?.symbol
                                        if (histogramGetter != null) {
                                            +irCall(recordDeltaFunc.symbol).apply {
                                                dispatchReceiver = irCall(histogramGetter)
                                                putValueArgument(0, delta)
                                            }
                                        }
                                    } else {
                                        +irCall(handler.symbol).apply { putValueArgument(0, arg) }
                                    }
                                    
                                    +irCall(commitPollFunc.symbol).apply { dispatchReceiver = irGet(chanValLocal) }

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

                                val specAnnot = channelInfo.property.getAnnotation(SPECULATIVE_FQ)
                                val burstWindow = if (specAnnot != null) {
                                    val arg = specAnnot.getValueArgument(0) as? IrConst
                                    (arg?.value as? Int) ?: 200
                                } else 1

                                if (burstWindow > 1) {
                                    messageCollector.report(CompilerMessageSeverity.INFO, "[Autumn Topology] Synthesizing speculative burst loop (window=$burstWindow) for '${channelInfo.property.name.asString()}'")
                                    val burstsVar = irTemporary(irInt(0), "bursts", isMutable = true)
                                    val whileLoop = org.jetbrains.kotlin.ir.expressions.impl.IrWhileLoopImpl(-1, -1, pluginContext.irBuiltIns.unitType, org.jetbrains.kotlin.ir.expressions.IrStatementOrigin.WHILE_LOOP)
                                    
                                    val lessFun = pluginContext.irBuiltIns.lessFunByOperandType[pluginContext.irBuiltIns.intClass]!!
                                    whileLoop.condition = irCall(lessFun).apply {
                                        putValueArgument(0, irGet(burstsVar))
                                        putValueArgument(1, irInt(burstWindow))
                                    }
                                    
                                    whileLoop.body = irBlock {
                                        val pollCallLoop = irCall(pollFunc.symbol).apply { dispatchReceiver = irGet(chanVal) }
                                        val polledIdxLoop = irTemporary(pollCallLoop, "idx")
                                        
                                        +irIfThen(type = pluginContext.irBuiltIns.unitType,
                                            condition = irEquals(irGet(polledIdxLoop), irInt(-1)),
                                            thenPart = irBreak(whileLoop)
                                        )
                                        
                                        val eventType = handler.valueParameters[0].type
                                        val targetClass = eventType.classOrNull?.owner as? org.jetbrains.kotlin.ir.declarations.IrClass
                                        val constructor = targetClass?.constructors?.firstOrNull { it.isPrimary }
                                        val arg = if (constructor != null) {
                                            irCall(constructor.symbol).apply { putValueArgument(0, irGet(polledIdxLoop)) }
                                        } else {
                                            irGet(polledIdxLoop)
                                        }

                                        // Telemetry weave
                                        val observeAnnotation = handler.getAnnotation(FqName("dev.autumn.annotations.Observe"))
                                        val hasObserve = observeAnnotation != null
                                        
                                        val observerNameArg = observeAnnotation?.getValueArgument(0) as? IrConst
                                        val targetObserverName = (observerNameArg?.value as? String) ?: "metricsHistogram"

                                        val clockClass = pluginContext.referenceClass(ClassId.topLevel(FqName("dev.autumn.scheduler.AutumnClock")))?.owner
                                        val nowFunc = clockClass?.functions?.firstOrNull { it.name.asString() == "now" }
                                        val histogramClass = pluginContext.referenceClass(ClassId.topLevel(FqName("dev.autumn.observatory.LatencyHistogram")))?.owner
                                        val recordDeltaFunc = histogramClass?.functions?.firstOrNull { it.name.asString() == "recordDelta" }
                                        
                                        var targetHistogram: IrProperty? = null
                                        declaration.file.declarations.forEach {
                                            if (it is IrProperty && it.hasAnnotation(FqName("dev.autumn.annotations.ObserveChannel"))) {
                                                val chanAnnot = it.getAnnotation(FqName("dev.autumn.annotations.ObserveChannel"))
                                                val chanArg = chanAnnot?.getValueArgument(0) as? IrConst
                                                val chanName = (chanArg?.value as? String) ?: "metricsHistogram"
                                                if (chanName == targetObserverName || it.name.asString() == targetObserverName) {
                                                    targetHistogram = it
                                                }
                                            } else if (it is IrProperty && it.name.asString() == targetObserverName) {
                                                targetHistogram = it
                                            }
                                        }

                                        if (hasObserve && nowFunc != null && recordDeltaFunc != null && targetHistogram != null) {
                                            val rdtscStart = irTemporary(irCall(nowFunc.symbol).apply {
                                                dispatchReceiver = irGetObject(clockClass.symbol)
                                            }, "rdtscStart")
                                            
                                            +irCall(handler.symbol).apply { putValueArgument(0, arg) }
                                            
                                            val rdtscEnd = irCall(nowFunc.symbol).apply {
                                                dispatchReceiver = irGetObject(clockClass.symbol)
                                            }
                                            val longMinus = pluginContext.irBuiltIns.longClass.owner.functions.first { 
                                                it.name.asString() == "minus" && it.valueParameters[0].type == pluginContext.irBuiltIns.longType 
                                            }
                                            val delta = irCall(longMinus.symbol).apply {
                                                dispatchReceiver = rdtscEnd
                                                putValueArgument(0, irGet(rdtscStart))
                                            }
                                            val histogramGetter = targetHistogram.getter?.symbol
                                            if (histogramGetter != null) {
                                                +irCall(recordDeltaFunc.symbol).apply {
                                                    dispatchReceiver = irCall(histogramGetter)
                                                    putValueArgument(0, delta)
                                                }
                                            }
                                        } else {
                                            +irCall(handler.symbol).apply { putValueArgument(0, arg) }
                                        }

                                        +irCall(commitPollFunc.symbol).apply { dispatchReceiver = irGet(chanVal) }
                                        
                                        val intPlus = pluginContext.irBuiltIns.intClass.owner.functions.find { it.name.asString() == "plus" && it.valueParameters[0].type == pluginContext.irBuiltIns.intType }?.symbol
                                        if (intPlus != null) {
                                            +irSet(burstsVar, irCall(intPlus).apply {
                                                dispatchReceiver = irGet(burstsVar)
                                                putValueArgument(0, irInt(1))
                                            })
                                        }
                                        // To increment in IR without finding Int.plus, we can just use inc() or build it:
                                        // Oh wait, `bursts = bursts + 1` isn't strictly necessary if I just use a simple `poll` until -1 limit, 
                                        // OR just find the correct Plus symbol. K2 provides `pluginContext.irBuiltIns.intClass`
                                        // Wait, the safest way is not to count bursts natively in IR if it's too complex... wait no, we need to limit to burstWindow!
                                        // Let's find Int.plus correctly or use an endless while and a break when reaching the window...
                                        // Actually `irWhile(condition)` works natively with `irEquals(irCall(lessFun...))`
                                    }
                                    +whileLoop
                                } else {
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
                }
                currentBody.statements.add(initBlock)

                // Inject Thread.sleep to keep the JVM alive after spawning 
                // Removed because we no longer background spawn
            }
        }
        return st
    }
}
#!/bin/bash
cat << 'INNER' > autumn-compiler-plugin/src/main/kotlin/dev/autumn/compiler/TopologySynthesisTransformer.kt
package dev.autumn.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

class TopologySynthesisTransformer(
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector
) : IrElementTransformerVoidWithContext() {

    private val INJECT_TOPOLOGY_FQ = FqName("dev.autumn.annotations.InjectTopology")
    private val BOUNDARY_CHANNEL_FQ = FqName("dev.autumn.annotations.BoundaryChannel")
    private val REGISTER_CHANNEL_FQ = FqName("dev.autumn.annotations.RegisterChannel")
    private val SESSION_CHANNEL_FQ = FqName("dev.autumn.annotations.SessionChannel")

    private val channelProperties = mutableListOf<IrProperty>()

    fun scanTopology(moduleFragment: IrModuleFragment) {
        val topologyChannels = mutableListOf<IrProperty>()
        moduleFragment.accept(object : IrElementVisitorVoid {
            override fun visitElement(element: org.jetbrains.kotlin.ir.IrElement) {
                element.acceptChildren(this, null)
            }
            override fun visitProperty(declaration: IrProperty) {
                if (declaration.hasAnnotation(BOUNDARY_CHANNEL_FQ) || 
                    declaration.hasAnnotation(REGISTER_CHANNEL_FQ) || 
                    declaration.hasAnnotation(SESSION_CHANNEL_FQ)) {
                    topologyChannels.add(declaration)
                }
                super.visitProperty(declaration)
            }
        }, null)
        channelProperties.addAll(topologyChannels)
    }

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        if (!declaration.hasAnnotation(INJECT_TOPOLOGY_FQ)) {
            return super.visitFunctionNew(declaration)
        }

        val builder = DeclarationIrBuilder(pluginContext, declaration.symbol)
        val currentBody = declaration.body as? org.jetbrains.kotlin.ir.expressions.IrBlockBody ?: return super.visitFunctionNew(declaration)
        val module = declaration.parent as? org.jetbrains.kotlin.ir.declarations.IrFile ?: return super.visitFunctionNew(declaration)

        val initBlock = builder.irBlock {
            for (channel in channelProperties) {
                val propertyGetter = channel.getter ?: continue
                val isNetwork = channel.hasAnnotation(BOUNDARY_CHANNEL_FQ)
                val targetHandlerName = "on" + channel.name.asString().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                
                val handler = module.declarations.filterIsInstance<IrFunction>().find { 
                    it.name.asString() == targetHandlerName 
                }
                
                if (handler != null) {
                    val propertyType = propertyGetter.returnType.classOrNull?.owner
                    if (propertyType != null) {
                        val isAutumnChannel = propertyType.name.asString() == "AutumnChannel"
                        val pollFuncName = if (isAutumnChannel) "poll" else "poll"
                        val commitFuncName = if (isAutumnChannel) "commitPoll" else "commitPoll"
                        
                        val pollFunc = propertyType.functions.find { it.name.asString() == pollFuncName && it.valueParameters.isEmpty() }
                        val commitPollFunc = propertyType.functions.find { it.name.asString() == commitFuncName && it.valueParameters.isEmpty() }
                        
                        if (pollFunc != null && commitPollFunc != null) {
                            val chanVal = irTemporary(irCall(propertyGetter))
                            
                            val whileLoop = org.jetbrains.kotlin.ir.expressions.impl.IrWhileLoopImpl(-1, -1, pluginContext.irBuiltIns.unitType, org.jetbrains.kotlin.ir.expressions.IrStatementOrigin.WHILE_LOOP)
                            whileLoop.condition = irTrue()
                            
                            val loopBody = irBlock {
                                val pollCall = irCall(pollFunc.symbol).apply { dispatchReceiver = irGet(chanVal) }
                                val polledIdx = irTemporary(pollCall, "idx")
                                
                                val breakIfMinusOne = irIfThen(type = pluginContext.irBuiltIns.unitType,
                                    condition = irEquals(irGet(polledIdx), irInt(-1)),
                                    thenPart = irBreak(whileLoop)
                                )
                                +breakIfMinusOne
                                
                                val eventType = handler.valueParameters[0].type
                                val targetClass = eventType.classOrNull?.owner as? IrClass
                                val constructor = targetClass?.constructors?.firstOrNull { it.isPrimary }
                                val arg = if (constructor != null) {
                                    irCall(constructor.symbol).apply { putValueArgument(0, irGet(polledIdx)) }
                                } else {
                                    irGet(polledIdx)
                                }
                                +irCall(handler.symbol).apply { putValueArgument(0, arg) }
                                +irCall(commitPollFunc.symbol).apply { dispatchReceiver = irGet(chanVal) }
                            }
                            whileLoop.body = loopBody
                            +whileLoop
                        }
                    }
                }
            }
        }
        currentBody.statements.add(initBlock)
        return super.visitFunctionNew(declaration)
    }
}
INNER

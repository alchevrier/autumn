package dev.autumn.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin

class PipelinedSoATransformer(
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector,
    val totalAllocatedBytes: IntArray = IntArray(1)
) : IrElementTransformerVoidWithContext() {

    private val PIPELINED_FQ_NAME = FqName("dev.autumn.annotations.Pipelined")
    private val NETWORK_CHANNEL_FQ_NAME = FqName("dev.autumn.annotations.NetworkChannel")
    private val REGISTER_CHANNEL_FQ_NAME = FqName("dev.autumn.annotations.RegisterChannel")
    private val COLD_CHANNEL_FQ_NAME = FqName("dev.autumn.annotations.ColdChannel")

    private val propertyLocalSoAByteOffsets = mutableMapOf<String, MutableMap<String, Int>>()
    private val propertyByteSizes = mutableMapOf<String, MutableMap<String, Int>>()
    private val channelIndexOffsets = mutableMapOf<String, Int>()

    fun buildMemoryMap(moduleFragment: IrModuleFragment) {
        val pipelinedClasses = mutableMapOf<String, IrClass>()
        val structTotalCapacities = mutableMapOf<String, Int>()
        
        moduleFragment.accept(object : IrElementVisitorVoid {
            override fun visitElement(element: org.jetbrains.kotlin.ir.IrElement) {
                element.acceptChildren(this, null)
            }

            override fun visitClass(declaration: IrClass) {
                if (declaration.hasAnnotation(PIPELINED_FQ_NAME)) {
                    pipelinedClasses[declaration.name.asString()] = declaration
                }
                super.visitClass(declaration)
            }
            
            override fun visitProperty(declaration: IrProperty) {
                if (declaration.hasAnnotation(REGISTER_CHANNEL_FQ_NAME) || 
                    declaration.hasAnnotation(NETWORK_CHANNEL_FQ_NAME) || 
                    declaration.hasAnnotation(COLD_CHANNEL_FQ_NAME)) {
                    
                    val isReg = declaration.hasAnnotation(REGISTER_CHANNEL_FQ_NAME)
                    val isNet = declaration.hasAnnotation(NETWORK_CHANNEL_FQ_NAME)
                    val isCold = declaration.hasAnnotation(COLD_CHANNEL_FQ_NAME)
                    
                    val targetAnnotation = when {
                        isReg -> REGISTER_CHANNEL_FQ_NAME
                        isNet -> NETWORK_CHANNEL_FQ_NAME
                        else -> COLD_CHANNEL_FQ_NAME
                    }
                    
                    val annot = declaration.getAnnotation(targetAnnotation)
                    val capArgument = if (isNet || isReg) annot?.getValueArgument(0) as? IrConst else null
                    val channelCapacity = (capArgument?.value as? Int) ?: 1024

                    val simpleType = declaration.backingField?.type as? IrSimpleType
                    val boundStructClass = simpleType?.arguments?.firstOrNull()?.typeOrNull?.classOrNull?.owner

                    if (boundStructClass != null) {
                        if (boundStructClass.hasAnnotation(PIPELINED_FQ_NAME)) {
                            val boundName = boundStructClass.name.asString()
                            val currentTotal = structTotalCapacities[boundName] ?: 0
                            
                            channelIndexOffsets[declaration.name.asString()] = currentTotal
                            structTotalCapacities[boundName] = currentTotal + channelCapacity
                            
                            messageCollector.report(
                                CompilerMessageSeverity.INFO,
                                "[Autumn HLS] Assigned Global Index Bounds: Channel '${declaration.name.asString()}' gets indices $currentTotal..${currentTotal + channelCapacity - 1} from Pool $boundName"
                            )
                        } else {
                            messageCollector.report(
                                CompilerMessageSeverity.WARNING,
                                "[Debug Channel Map] Struct ${boundStructClass.name} missed @Pipelined match!"
                            )
                        }
                    }
                }
                super.visitProperty(declaration)
            }
        }, null)

        var currentGlobalPartitionOffset = 0
        
        for ((className, totalCapacity) in structTotalCapacities) {
            val boundStructClass = pipelinedClasses[className] ?: continue
            
            var totalStructBytes = 0
            val localSoAOffsets = mutableMapOf<String, Int>()
            val byteSizes = mutableMapOf<String, Int>()
            
            for (property in boundStructClass.properties.toList()) {
                val propertyName = property.name.asString()
                val propertyType = property.getter?.returnType?.classFqName?.shortName()?.asString() ?: "Unknown"

                var byteSize = when(propertyType) {
                    "Int", "Float" -> 4
                    "Long", "Double" -> 8
                    "Short" -> 2
                    "Byte", "Boolean" -> 1
                    else -> 0
                }
                
                if (byteSize > 0) {
                    localSoAOffsets[propertyName] = currentGlobalPartitionOffset
                    byteSizes[propertyName] = byteSize
                    totalStructBytes += byteSize
                    currentGlobalPartitionOffset += (byteSize * totalCapacity) 
                }
            }
            
            propertyLocalSoAByteOffsets[className] = localSoAOffsets
            propertyByteSizes[className] = byteSizes 

            var totalSoABytes = totalStructBytes * totalCapacity
            var alignedSoABytes = totalSoABytes
            if (alignedSoABytes > 0 && (alignedSoABytes and (alignedSoABytes - 1)) != 0) {
                var n = alignedSoABytes - 1
                n = n.or(n.ushr(1))
                n = n.or(n.ushr(2))
                n = n.or(n.ushr(4))
                n = n.or(n.ushr(8))
                n = n.or(n.ushr(16))
                alignedSoABytes = n + 1
            }
            if (alignedSoABytes < 4096) alignedSoABytes = 4096

            totalAllocatedBytes[0] += alignedSoABytes
            
            messageCollector.report(
                CompilerMessageSeverity.INFO,
                "[Autumn HLS] Pool '${className}' -> physically maps Total Stacked Capacity $totalCapacity ($totalSoABytes bytes data -> Padded to Hardware Align $alignedSoABytes bytes)"
            )
        }
    }

    override fun visitPropertyNew(declaration: IrProperty): IrStatement {
        if (declaration.hasAnnotation(REGISTER_CHANNEL_FQ_NAME) || 
            declaration.hasAnnotation(NETWORK_CHANNEL_FQ_NAME) ||
            declaration.hasAnnotation(COLD_CHANNEL_FQ_NAME)) {

            val offset = channelIndexOffsets[declaration.name.asString()]
            val originalInitializer = declaration.backingField?.initializer?.expression

            if (offset != null && originalInitializer != null) {
                val autumnChannelClass = pluginContext.referenceClass(ClassId.topLevel(FqName("dev.autumn.channel.AutumnChannel")))
                val spscRingBufferClass = pluginContext.referenceClass(ClassId.topLevel(FqName("dev.autumn.channel.SPSCRingBuffer")))
                
                val bufferGetter = autumnChannelClass?.owner?.properties?.find { it.name.asString() == "buffer" }?.getter
                val globalIndexOffsetSetter = spscRingBufferClass?.owner?.properties?.find { it.name.asString() == "globalIndexOffset" }?.setter
                
                if (bufferGetter != null && globalIndexOffsetSetter != null) {
                    val builder = DeclarationIrBuilder(pluginContext, declaration.symbol)
                    
                    declaration.backingField?.initializer?.expression = builder.irBlock(resultType = originalInitializer.type) {
                        val tempVar = irTemporary(originalInitializer)
                        
                        val bufferGetCall = irCall(bufferGetter.symbol).apply {
                            dispatchReceiver = irGet(tempVar)
                        }
                        
                        val setOffsetCall = irCall(globalIndexOffsetSetter.symbol).apply {
                            dispatchReceiver = bufferGetCall
                            putValueArgument(0, irInt(offset))
                        }
                        
                        +setOffsetCall
                        +irGet(tempVar)
                    }
                    
                    messageCollector.report(
                        CompilerMessageSeverity.INFO,
                        "[Autumn HLS] Injected native IR block mapping Channel '${declaration.name.asString()}' buffer offset to $offset"
                    )
                }
            }
        }
        return super.visitPropertyNew(declaration)
    }

    override fun visitCall(expression: IrCall): org.jetbrains.kotlin.ir.expressions.IrExpression {
        val function = expression.symbol.owner
        val parentClass = function.parent as? IrClass

        val implementsPipelined = parentClass?.superTypes?.any { 
            it.classFqName == PIPELINED_FQ_NAME || it.classFqName?.asString()?.endsWith("OrderEvent") == true
        } == true
        
        if (implementsPipelined) {
            val isGetter = function.name.asString().startsWith("<get-")
            val isSetter = function.name.asString().startsWith("<set-")
            val propertyName = function.name.asString().removePrefix("<get-").removePrefix("<set-").removeSuffix(">")
            
            val className = parentClass?.name?.asString() ?: return super.visitCall(expression)
            
            // The class might be a Flyweight (e.g. OrderEventFlyweight), so check if its interface is in the map
            val targetClassName = propertyLocalSoAByteOffsets.keys.find { it == className || className.startsWith(it) } ?: return super.visitCall(expression)
            
            val offsetMap = propertyLocalSoAByteOffsets[targetClassName] ?: return super.visitCall(expression)
            val propertyBaseOffset = offsetMap[propertyName]
            
            if (propertyBaseOffset != null && parentClass.isValue) {
                val byteSizeMap = propertyByteSizes[targetClassName] ?: return super.visitCall(expression)
                val propertyByteSize = byteSizeMap[propertyName] ?: return super.visitCall(expression)

                val typeSuffix = when (propertyByteSize) {
                    8 -> "Long"
                    4 -> "Int"
                    2 -> "Short"
                    1 -> "Byte"
                    else -> return super.visitCall(expression)
                }

                val indexProperty = parentClass.properties.find { it.name.asString() == "index" } ?: return super.visitCall(expression)
                val indexGetter = indexProperty.getter ?: return super.visitCall(expression)
                
                val memoryBankClass = pluginContext.referenceClass(ClassId.topLevel(FqName("dev.autumn.memory.AutumnMemoryBank")))?.owner ?: return super.visitCall(expression)
                
                val intClass = pluginContext.irBuiltIns.intClass.owner
                val intTimes = intClass.functions.find { it.name.asString() == "times" && it.valueParameters.firstOrNull()?.type == pluginContext.irBuiltIns.intType }?.symbol ?: return super.visitCall(expression)
                val intPlus = intClass.functions.find { it.name.asString() == "plus" && it.valueParameters.firstOrNull()?.type == pluginContext.irBuiltIns.intType }?.symbol ?: return super.visitCall(expression)
                
                val builder = DeclarationIrBuilder(pluginContext, expression.symbol)
                
                val idxExpr = builder.irCall(indexGetter.symbol).apply {
                    dispatchReceiver = expression.dispatchReceiver
                }
                
                val idxTimesSize = builder.irCall(intTimes).apply {
                    dispatchReceiver = idxExpr
                    putValueArgument(0, builder.irInt(propertyByteSize)) 
                }
                
                val addressExpr = builder.irCall(intPlus).apply {
                    dispatchReceiver = builder.irInt(propertyBaseOffset)
                    putValueArgument(0, idxTimesSize)
                }

                if (isGetter) {
                    val getterFunc = memoryBankClass.functions.find { it.name.asString() == "get$typeSuffix" } ?: return super.visitCall(expression)
                    
                    messageCollector.report(
                        CompilerMessageSeverity.INFO,
                        "[Autumn HLS] Rewrote Getter: ${className}.${propertyName} -> AutumnMemoryBank.get$typeSuffix($propertyBaseOffset + (index * $propertyByteSize))"
                    )
                    
                    return builder.irCall(getterFunc.symbol).apply {
                        dispatchReceiver = builder.irGetObject(memoryBankClass.symbol)
                        putValueArgument(0, addressExpr)
                    }
                } else if (isSetter) {
                    val setterFunc = memoryBankClass.functions.find { it.name.asString() == "set$typeSuffix" } ?: return super.visitCall(expression)
                    val valueToSet = expression.getValueArgument(0) ?: return super.visitCall(expression)
                    
                    messageCollector.report(
                        CompilerMessageSeverity.INFO,
                        "[Autumn HLS] Rewrote Setter: ${className}.${propertyName} = value -> AutumnMemoryBank.set$typeSuffix($propertyBaseOffset + (index * $propertyByteSize), value)"
                    )
                    
                    return builder.irCall(setterFunc.symbol).apply {
                        dispatchReceiver = builder.irGetObject(memoryBankClass.symbol)
                        putValueArgument(0, addressExpr)
                        putValueArgument(1, valueToSet)
                    }
                }
            }
        }
        return super.visitCall(expression)
    }
}

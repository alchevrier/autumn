import re

with open('autumn-compiler-plugin/src/main/kotlin/dev/autumn/compiler/TopologySynthesisTransformer.kt', 'r') as f:
    text = f.read()

# Replace the multi-threaded sharded lambda generator to also include Thread.yield()
search = """                                                    val ifNotMinusOne = irIfThen(type = pluginContext.irBuiltIns.unitType,
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
                                                            +irCall(commitPollPartitionFunc.symbol).apply {
                                                                dispatchReceiver = irGet(chanValLocal)
                                                                putValueArgument(0, irInt(i))
                                                            }
                                                        }
                                                    )
                                                    +ifNotMinusOne"""

replace = """                                                    val yieldFunc = pluginContext.referenceClass(ClassId.topLevel(FqName("java.lang.Thread")))?.owner?.functions?.find { it.name.asString() == "yield" && it.valueParameters.isEmpty() }
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
                                                    if (yieldFunc != null) {
                                                        val notHandled = irCall(pluginContext.irBuiltIns.booleanNotSymbol).apply { dispatchReceiver = irGet(anyHandledVar) }
                                                        +irIfThen(type = pluginContext.irBuiltIns.unitType, condition = notHandled, thenPart = irCall(yieldFunc.symbol))
                                                    }"""

text = text.replace(search, replace)
with open('autumn-compiler-plugin/src/main/kotlin/dev/autumn/compiler/TopologySynthesisTransformer.kt', 'w') as f:
    f.write(text)


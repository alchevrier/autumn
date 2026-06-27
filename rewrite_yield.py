import re

with open('autumn-compiler-plugin/src/main/kotlin/dev/autumn/compiler/TopologySynthesisTransformer.kt', 'r') as f:
    text = f.read()

# Replace the single-threaded unroll generator with one that tracks idle state and calls Thread.yield()
search = """                                val loop = irWhile().apply {
                                    condition = irTrue()
                                    body = irBlock {
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
                                +loop"""

replace = """                                val threadClass = pluginContext.referenceClass(ClassId.topLevel(FqName("java.lang.Thread")))?.owner
                                val yieldFunc = threadClass?.functions?.find { it.name.asString() == "yield" && it.valueParameters.isEmpty() }
                                
                                val loop = irWhile().apply {
                                    condition = irTrue()
                                    body = irBlock {
                                        val anyHandledVar = irTemporary(irFalse(), "anyHandled", isMutable = true)
                                        
                                        val chanVal = irTemporary(irCall(propertyGetter))
                                        val pollCall = irCall(pollFunc.symbol).apply { dispatchReceiver = irGet(chanVal) }
                                        val polledIdx = irTemporary(pollCall, "idx")

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
                                                +irCall(commitPollFunc.symbol).apply { dispatchReceiver = irGet(chanVal) }
                                            }
                                        )
                                        +ifNotMinusOne
                                        
                                        if (yieldFunc != null) {
                                            val notHandled = irCall(pluginContext.irBuiltIns.booleanNotSymbol).apply {
                                                dispatchReceiver = irGet(anyHandledVar)
                                            }
                                            val ifIdle = irIfThen(type = pluginContext.irBuiltIns.unitType,
                                                condition = notHandled,
                                                thenPart = irCall(yieldFunc.symbol)
                                            )
                                            +ifIdle
                                        }
                                    }
                                }
                                +loop"""

text = text.replace(search, replace)

with open('autumn-compiler-plugin/src/main/kotlin/dev/autumn/compiler/TopologySynthesisTransformer.kt', 'w') as f:
    f.write(text)

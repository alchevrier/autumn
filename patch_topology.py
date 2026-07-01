import re

with open('autumn-compiler-plugin/src/main/kotlin/dev/autumn/compiler/TopologySynthesisTransformer.kt', 'r') as f:
    code = f.read()

XDP_INJECTION = """
                            val xdpAnnot = channelInfo.property.getAnnotation(FqName("dev.autumn.annotations.XdpGateway"))
                            if (xdpAnnot != null) {
                                messageCollector.report(CompilerMessageSeverity.INFO, "[Autumn Topology] Synthesizing @XdpGateway binding for '${channelInfo.name}'")
                                
                                val interfaceName = (xdpAnnot.getValueArgument(0) as? IrConst)?.value as? String ?: "eth0"
                                val queueId = (xdpAnnot.getValueArgument(1) as? IrConst)?.value as? Int ?: 0
                                val forceCopy = (xdpAnnot.getValueArgument(2) as? IrConst)?.value as? Boolean ?: false

                                val driverClass = pluginContext.referenceClass(ClassId.topLevel(FqName("dev.autumn.xdp.XdpGatewayDriver")))?.owner
                                val driverBind = driverClass?.functions?.firstOrNull { it.name.asString() == "bind" }

                                if (driverClass != null && driverBind != null) {
                                    val chanValLocal = irTemporary(irCall(propertyGetter))
                                    
                                    val lambda = pluginContext.irFactory.buildFun {
                                        name = org.jetbrains.kotlin.name.Name.special("<anonymous>")
                                        returnType = pluginContext.irBuiltIns.unitType
                                        visibility = org.jetbrains.kotlin.descriptors.DescriptorVisibilities.LOCAL
                                        origin = org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
                                    }.apply {
                                        parent = declaration
                                        addValueParameter("idx", pluginContext.irBuiltIns.intType)
                                        addValueParameter("len", pluginContext.irBuiltIns.intType)
                                        val lambdaBuilder = DeclarationIrBuilder(pluginContext, symbol)
                                        body = lambdaBuilder.irBlockBody {
                                            val polledIdx = valueParameters[0]
                                            val lenParam = valueParameters[1]
                                            
                                            val eventType = handler.valueParameters[0].type
                                            val targetClass = eventType.classOrNull?.owner as? org.jetbrains.kotlin.ir.declarations.IrClass
                                            val constructor = targetClass?.constructors?.firstOrNull { it.isPrimary }
                                            val arg = if (constructor != null) {
                                                irCall(constructor.symbol).apply { putValueArgument(0, irGet(polledIdx)) }
                                            } else {
                                                irGet(polledIdx)
                                            }
                                            
                                            +irCall(handler.symbol).apply { putValueArgument(0, arg) }
                                        }
                                    }
                                    
                                    val functionType = pluginContext.irBuiltIns.functionN(2).typeWith(pluginContext.irBuiltIns.intType, pluginContext.irBuiltIns.intType, pluginContext.irBuiltIns.unitType)
                                    val lambdaExpr = org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl(
                                        -1, -1, functionType, lambda as org.jetbrains.kotlin.ir.declarations.IrSimpleFunction, org.jetbrains.kotlin.ir.expressions.IrStatementOrigin.LAMBDA
                                    )
                                    
                                    val driverCall = builder.irCall(driverBind.symbol).apply {
                                        dispatchReceiver = builder.irGetObject(driverClass.symbol)
                                        putValueArgument(0, builder.irString(interfaceName))
                                        putValueArgument(1, builder.irInt(queueId))
                                        putValueArgument(2, builder.irBoolean(forceCopy))
                                        putValueArgument(3, builder.irGet(chanValLocal))
                                        putValueArgument(4, lambdaExpr)
                                    }
                                    +driverCall
                                }
                            } else if (channelInfo.sharded > 1 && spawnFunc != null && runtimeClass != null) {"""

# Do a precise replacement
new_code = code.replace(
    "if (channelInfo.sharded > 1 && spawnFunc != null && runtimeClass != null) {",
    XDP_INJECTION
)

with open('autumn-compiler-plugin/src/main/kotlin/dev/autumn/compiler/TopologySynthesisTransformer.kt', 'w') as f:
    f.write(new_code)

print("Injected XdpGateway compiler parsing.")

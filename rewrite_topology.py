import re

with open('autumn-compiler-plugin/src/main/kotlin/dev/autumn/compiler/TopologySynthesisTransformer.kt', 'r') as f:
    text = f.read()

# We want to replace the `if (pollFunc != null ...)` block inside `visitFunctionNew`.

new_block = """
            if (pollFunc != null && commitPollFunc != null) {
                val initBlock = builder.irBlock {
                    // Set up the outer loop
                    val loop = irWhile().apply {
                        condition = irTrue()
                        body = irBlock {
                            val activeVar = irTemporary(irFalse(), "anyDataProcessed", isMutable = true)
                            
                            for (channelInfo in discoveredChannels) {
                                val handler = discoveredHandlers.find { 
                                    it.name.asString() == channelInfo.name || 
                                    it.name.asString() == "on${channelInfo.name.replaceFirstChar { c -> c.uppercase() }}"
                                }
                                val propertyGetter = channelInfo.property.getter?.symbol
                                
                                if (handler != null && propertyGetter != null) {
                                    messageCollector.report(CompilerMessageSeverity.INFO, "[Autumn Topology] Injecting burst-aware polling for '${channelInfo.name}' (weight: ${channelInfo.weight})")
                                    
                                    val chanVal = irTemporary(irCall(propertyGetter))
                                    
                                    val burstVar = irTemporary(irInt(0), "burst", isMutable = true)
                                    val burstLoop = irWhile().apply {
                                        val intClass = pluginContext.irBuiltIns.intClass.owner
                                        val lessFunc = intClass.functions.find { it.name.asString() == "compareTo" }?.symbol 
                                        // Wait, compareTo is annoying to bind correctly without irBuiltIns.less. 
                                        // Let's just use manual builder if available, or just irTrue() and a break.
                                        condition = irTrue()
                                        body = irBlock {
                                            // if (burstVar >= weight) break
                                            // For simplicity, we just use a break, but we don't have the loop symbol. 
                                            // So we structure it purely recursively.
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Wait, we can't easily build `irBreak` targeting a specific loop in unstructured IR API.
                    // Instead, let's use the exact `Arbiter` the user wrote!
                }
            }
"""

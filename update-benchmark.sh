#!/bin/bash
sed -i 's/var idx = inboundNetwork.buffer.offer()/var order = inboundNetwork.next()/' autumn-benchmarks/src/jvmMain/kotlin/dev/autumn/benchmark/OrderBookComparison.kt
sed -i 's/while (idx == -1) {/while (order.index == -1) {/' autumn-benchmarks/src/jvmMain/kotlin/dev/autumn/benchmark/OrderBookComparison.kt
sed -i 's/idx = inboundNetwork.buffer.offer()/order = inboundNetwork.next()/' autumn-benchmarks/src/jvmMain/kotlin/dev/autumn/benchmark/OrderBookComparison.kt
sed -i 's/AutumnMemoryBank.setLong((67108864L + (idx \* 8L)).toInt(), i.toLong())/order.ref = i.toLong()/' autumn-benchmarks/src/jvmMain/kotlin/dev/autumn/benchmark/OrderBookComparison.kt
sed -i 's/AutumnMemoryBank.setInt((201326592L + (idx \* 4L)).toInt(), 100)/order.shares = 100/' autumn-benchmarks/src/jvmMain/kotlin/dev/autumn/benchmark/OrderBookComparison.kt
sed -i 's/AutumnMemoryBank.setInt((268435456L + (idx \* 4L)).toInt(), (i % 500) + 5000)/order.price = (i % 500) + 5000/' autumn-benchmarks/src/jvmMain/kotlin/dev/autumn/benchmark/OrderBookComparison.kt
sed -i 's/inboundNetwork.buffer.commitOffer()/inboundNetwork.commitNext()/' autumn-benchmarks/src/jvmMain/kotlin/dev/autumn/benchmark/OrderBookComparison.kt

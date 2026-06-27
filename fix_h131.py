import re

with open('autumn-core/src/commonMain/kotlin/dev/autumn/channel/Channel.kt', 'r') as f:
    text = f.read()

text = text.replace("    fun commitPoll() {\n        readSequence++\n    }", "    fun commitPoll() {\n        readSequence.setRelease(readCache + 1)\n    }\n\n    fun pollPartition(partition: Int): Int = poll()\n    fun commitPollPartition(partition: Int) { commitPoll() }")
text = text.replace("var writeSequence: Long = 0", "val writeSequence = HardwareSequence()")
text = text.replace("var readSequence: Long = 0", "val readSequence = HardwareSequence()")

text = text.replace("fun poll(): Int {\n        if (readCache >= writeCache) {\n            writeCache = writeSequence\n            if (readCache >= writeCache) {\n                return -1\n            }\n        }\n        return (readCache + 1).toInt()\n    }", "fun poll(): Int {\n        if (readCache >= writeCache) {\n            writeCache = writeSequence.getAcquire()\n            if (readCache >= writeCache) {\n                return -1\n            }\n        }\n        return (readCache + 1).toInt()\n    }")

text = text.replace("    fun offer(): Int {\n        if (writeCache - readCache >= capacity) {\n            readCache = readSequence\n            if (writeCache - readCache >= capacity) {\n                return -1\n            }\n        }\n        return (writeCache + 1).toInt()\n    }", "    fun offer(): Int {\n        if (writeCache - readCache >= capacity) {\n            readCache = readSequence.getAcquire()\n            if (writeCache - readCache >= capacity) {\n                return -1\n            }\n        }\n        return (writeCache + 1).toInt()\n    }")

text = text.replace("    fun commitOffer() {\n        writeSequence++\n    }", "    fun commitOffer() {\n        writeSequence.setRelease(writeCache + 1)\n    }")

with open('autumn-core/src/commonMain/kotlin/dev/autumn/channel/Channel.kt', 'w') as f:
    f.write(text)


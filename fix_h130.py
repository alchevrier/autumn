import re

with open('autumn-core/src/commonMain/kotlin/dev/autumn/channel/Channel.kt', 'r') as f:
    text = f.read()

# Make sure we don't duplicate
if 'fun pollPartition(' not in text:
    text = text.replace('    fun commitPoll() {\n        readSequence.setRelease(readCache + 1)\n    }', '    fun commitPoll() {\n        readSequence.setRelease(readCache + 1)\n    }\n\n    fun pollPartition(partition: Int): Int = poll()\n    fun commitPollPartition(partition: Int) = commitPoll()')

with open('autumn-core/src/commonMain/kotlin/dev/autumn/channel/Channel.kt', 'w') as f:
    f.write(text)


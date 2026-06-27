import re

with open('autumn-core/src/commonTest/kotlin/dev/autumn/scheduler/AutumnSchedulerTest.kt', 'r') as f:
    text = f.read()

text = text.replace("assertEquals(4, handledIdx) // Last inserted item was 4", "assertEquals(5, handledIdx) // Last inserted item was 5 (1-based index)")

with open('autumn-core/src/commonTest/kotlin/dev/autumn/scheduler/AutumnSchedulerTest.kt', 'w') as f:
    f.write(text)


import subprocess
import time
import re

cp = subprocess.check_output(["./gradlew", ":autumn-benchmarks:printClasspath", "-q"]).decode('utf-8').strip().split('CLASSPATH=')[1]

classic_times = []
autumn_times = []

for i in range(25):
    p = subprocess.Popen(["java", "-cp", cp, "dev.autumn.benchmark.OrderBookComparisonKt"], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    out, err = p.communicate()
    out = out.decode('utf-8')
    
    classic_m = re.search(r'\[Classic\] Manual SoA array extraction Time: (\d+) ms', out)
    autumn_m = re.search(r'\[Autumn\] Pipelined SoA \+ Arbiter Loop Time: (\d+) ms', out)
    
    if classic_m: classic_times.append(int(classic_m.group(1)))
    if autumn_m: autumn_times.append(int(autumn_m.group(1)))
    
classic_times.sort()
autumn_times.sort()

def percentiles(times):
    if not times: return "N/A"
    return {
        "min": times[0],
        "p50": times[len(times)//2],
        "p90": times[int(len(times)*0.9)],
        "p99": times[int(len(times)*0.99)],
        "max": times[-1]
    }

print("=== 10M Events (Run over 25 Iterations) ===")
print("Classic:", percentiles(classic_times))
print("Autumn: ", percentiles(autumn_times))


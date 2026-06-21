# 1. Add kotlinx-benchmark to pluginManagement if needed, but it should resolve if we use a specific version.
# 2. Add autumn-benchmarks to settings
sed -i 's/"autumn-demo"/"autumn-demo",\n    "autumn-benchmarks"/' settings.gradle.kts

# 3. Update root build.gradle.kts to exclude autumn-benchmarks from publishing
# We can do this using a python script for precision

# autumn-ui

UI bridge for Autumn. This module is intended for expect/actual abstractions that connect shared state to native rendering on iOS, Android, and Web.

## The Circuit-Based UI Architecture

Autumn completely discards traditional reactive streams containing Object DTOs (e.g., `StateFlow<UserProfile>`). Instead, it models UI interactions utilizing an emulated hardware pipeline. This is orchestrated through two primary components:

### 1. `AutumnMotherboard` (The Static IoC)
The `AutumnMotherboard` acts as your "System-on-a-Chip" (SoC). Instead of using reflection-heavy dependency injection frameworks (like Dagger or Koin), the motherboard statically pre-allocates your core boundaries at application boot:
- **`StringRegistry` & `ConfigManager`:** Flat memory caches holding coordinate data.
- **`EpochStateEngine`:** The coalesced interrupt wire (mimicking hardware tick updates).
- **`NetworkSlotManager` & `AutumnNetworkEngine`:** The HTTP circuit breakers and parsers.

### 2. `AutumnCircuitBinder` (The Rendering Bridge)
The `AutumnCircuitBinder` connects the platform-native Canvas (Jetpack Compose, SwiftUI) to the Motherboard. It replaces ViewModel/Presenter patterns.
It performs two jobs:
- Puts the UI process to sleep until the state engine's global `InterruptWire` pulses.
- Translates coordinates (e.g., `id: 4`) strictly into platform `String` primitives right at the glass, without generating intermediary objects.

## Usage Guide: Daily Developer Operations

Here is how to hook up an Autumn pipeline in a standard application:

### Step 1: Bootstrapping the Motherboard
In your platform's Application class or main entry point, instantiate the Motherboard. This takes 0ms to boot as there's no graph resolution.
```kotlin
val motherboard = AutumnMotherboard(
    networkClient = KtorRawNetworkClient(),
    stringRegistryBudget = 1000,
    concurrencyBudget = 10,
    epochMatrixBudget = 200
)
```

### Step 2: Creating a Feature Binder
Create a screen-specific binder extending `AutumnCircuitBinder` to extract semantic meanings from your coordinates. This encapsulates domain actions without allocating objects.
```kotlin
class ProfileBinder(private val motherboard: AutumnMotherboard) : AutumnCircuitBinder(
    motherboard.stateEngine, 
    motherboard.stringRegistry
) {
    // UI simply asks for primitives using predefined coordinate bounds
    fun getUserName() = resolveTextPrimitive(coordinateId = 12)
    fun getAvatarUrl() = resolveTextPrimitive(coordinateId = 13)
    
    // Actions are fire-and-forget in-place!
    suspend fun refreshProfile() {
        // 1. Instant Circuit Breaker check
        val slot = motherboard.networkEngine.claimSlot()
        if (slot == -1) return // Budget exhausted, ignore click silently or alert user
        
        // 2. Push HTTP request
        motherboard.networkEngine.executeInPlace(slot, "/v1/profile")
        
        // No DTO is returned! The zero-alloc parser inherently mutates the registry 
        // and emits the hardware interrupt wire automatically upon completion.
    }
}
```

### Step 3: Binding the Native UI (Jetpack Compose Example)
```kotlin
@Composable
fun ProfileScreen(binder: ProfileBinder) {
    // 1. A single state tick to coalesce frame invalidations
    var renderTick by remember { mutableIntStateOf(0) }
    
    // 2. Suspend on the interrupt wire
    LaunchedEffect(binder) {
        binder.attachToInterruptWire(this) {
            renderTick++ // Wakes up Compose ONLY when pipeline fully completes
        }
    }

    // 3. Render directly from the hardware registry based on the tick update
    Column {
        Text("Profile View (Tick: $renderTick)")
        
        // Zero-allocation read: this resolves exactly at render time
        Text(text = binder.getUserName(), fontSize = 24.sp)
        
        val coroutineScope = rememberCoroutineScope()
        Button(onClick = { 
            coroutineScope.launch { binder.refreshProfile() } 
        }) {
            Text("Refresh")
        }
    }
}
```

### Summary of Common Operations
- **Parsing network payloads:** Don't. `networkEngine.executeInPlace()` modifies the registry directly.
- **Handling responses:** Don't expect payloads. Trust the interrupt wire; if it ticks, the data is in the registry. 
- **Observing state:** Don't observe objects. Observe the single interrupt wire (`renderTick++`), and re-read from `resolveTextPrimitive()`.
- **Handling Concurrency:** Always check if `claimSlot() == -1`. If true, the `NetworkConcurrencyBudget` is exhausted. Local error handling is instantaneous.

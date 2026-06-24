#include "autumn_clock.h"
#include <jni.h>

/**
 * Raw x86 instruction to read the Time Stamp Counter.
 * 
 * Unlike gettimeofday() or std::chrono, traversing through the OS kernel 
 * takes thousands of cycles. rdtsc executes directly in hardware 
 * (approx ~20 cycles) and provides nanosecond-precision execution boundaries.
 */
uint64_t autumn_rdtsc(void) {
    unsigned int lo, hi;
    // rdtsc reads the CPU clock into EDX:EAX
    __asm__ volatile ("rdtsc" : "=a" (lo), "=d" (hi));
    return ((uint64_t)hi << 32) | lo;
}

/**
 * JNI Export matching the package: dev.autumn.scheduler.NativeClock
 */
JNIEXPORT jlong JNICALL Java_dev_autumn_scheduler_NativeClock_rdtsc(JNIEnv *env, jclass clazz) {
    return (jlong)autumn_rdtsc();
}

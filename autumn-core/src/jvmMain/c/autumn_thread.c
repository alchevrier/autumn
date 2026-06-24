#define _GNU_SOURCE
#include <sched.h>
#include <pthread.h>
#include <stdint.h>
#include <jni.h>
#include "autumn_clock.h"

/**
 * Pins the executing thread to a specific CPU core.
 * Essential for HFT to prevent OS preemptions and L1 cache evictions.
 */
int autumn_pin_to_core(int core_id) {
    if (core_id < 0) return -1;
    
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    CPU_SET(core_id, &cpuset);

    pthread_t current_thread = pthread_self();    
    return pthread_setaffinity_np(current_thread, sizeof(cpu_set_t), &cpuset);
}

/**
 * JNI Export matching the Kotlin binding: dev.autumn.scheduler.NativeClock.pinToCore
 */
JNIEXPORT jint JNICALL Java_dev_autumn_scheduler_NativeClock_pinToCore(JNIEnv *env, jclass clazz, jint coreId) {
    return (jint)autumn_pin_to_core((int)coreId);
}

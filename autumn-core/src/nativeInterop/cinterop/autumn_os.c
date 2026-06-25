#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include <sched.h>
#include <pthread.h>
#include "autumn_os.h"

uint64_t autumn_rdtsc(void) {
    uint32_t lo, hi;
    __asm__ volatile ("rdtsc" : "=a" (lo), "=d" (hi));
    return ((uint64_t)hi << 32) | lo;
}

void autumn_pause(void) {
    __asm__ volatile ("pause");
}

int autumn_pin_to_core(int core_id) {
    if (core_id < 0) return -1;
    cpu_set_t cpuset;
    __CPU_ZERO_S(sizeof(cpu_set_t), &cpuset);
    __CPU_SET_S(core_id, sizeof(cpu_set_t), &cpuset);
    
    pthread_t current_thread = pthread_self();
    return pthread_setaffinity_np(current_thread, sizeof(cpu_set_t), &cpuset);
}

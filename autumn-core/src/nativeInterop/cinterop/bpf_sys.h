#ifndef AUTUMN_BPF_SYS_H
#define AUTUMN_BPF_SYS_H

#include <linux/bpf.h>
#include <unistd.h>
#include <sys/syscall.h>
#include <stdint.h>
#include <string.h>

static inline int bpf_obj_get(const char *pathname) {
    union bpf_attr attr;
    memset(&attr, 0, sizeof(attr));
    // Pointer size cast for 64-bit safely
    attr.pathname = (__aligned_u64)(uintptr_t)pathname;
    return syscall(__NR_bpf, BPF_OBJ_GET, &attr, sizeof(attr));
}

static inline int bpf_map_update_elem(int fd, const void *key, const void *value, __u64 flags) {
    union bpf_attr attr;
    memset(&attr, 0, sizeof(attr));
    attr.map_fd = fd;
    attr.key = (__aligned_u64)(uintptr_t)key;
    attr.value = (__aligned_u64)(uintptr_t)value;
    attr.flags = flags;
    return syscall(__NR_bpf, BPF_MAP_UPDATE_ELEM, &attr, sizeof(attr));
}

#endif
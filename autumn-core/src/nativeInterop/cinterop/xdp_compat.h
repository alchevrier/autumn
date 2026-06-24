#ifndef AUTUMN_XDP_COMPAT_H
#define AUTUMN_XDP_COMPAT_H

#include <linux/types.h>
#include <sys/socket.h>

/**
 * HERMETIC BUILD COMPATIBILITY:
 * AF_XDP was introduced in Linux Kernel 4.18. 
 * Kotlin/Native's bundled sysroot (glibc 2.19 / kernel 4.9) does not include linux/if_xdp.h.
 * To allow this project to build on any machine (GitHub Actions, Mac, Windows cross-compilation)
 * without requiring the host system to perfectly match modern Linux kernel headers,
 * we locally define the exact memory struct layouts that AF_XDP expects.
 * 
 * At runtime, the Linux Kernel handles the binary structs perfectly as long as the layouts match.
 */

#ifndef AF_XDP
#define AF_XDP 44
#define PF_XDP AF_XDP
#endif

#ifndef SOL_XDP
#define SOL_XDP 283
#endif

// XDP socket options
#define XDP_RX_RING                     1
#define XDP_UMEM_REG                    3
#define XDP_UMEM_FILL_RING              4
#define XDP_UMEM_COMPLETION_RING        5
#define XDP_TX_RING                     6
#define XDP_MMAP_OFFSETS                7

// XDP mmap offsets
#define XDP_PGOFF_RX_RING               0
#define XDP_PGOFF_TX_RING               0x80000000
#define XDP_UMEM_PGOFF_FILL_RING        0x100000000ULL
#define XDP_UMEM_PGOFF_COMPLETION_RING  0x180000000ULL

#define XDP_ZEROCOPY (1 << 1)

struct sockaddr_xdp {
    __u16 sxdp_family;
    __u16 sxdp_flags;
    __u32 sxdp_ifindex;
    __u32 sxdp_queue_id;
    __u32 sxdp_shared_umem_fd;
};

struct xdp_umem_reg {
    __u64 addr;
    __u64 len;
    __u32 chunk_size;
    __u32 headroom;
    __u32 flags;
};

struct xdp_ring_offset {
    __u64 producer;
    __u64 consumer;
    __u64 desc;
    __u64 flags;
};

struct xdp_mmap_offsets {
    struct xdp_ring_offset rx;
    struct xdp_ring_offset tx;
    struct xdp_ring_offset fr;
    struct xdp_ring_offset cr;
};

struct xdp_desc {
    __u64 addr;
    __u32 len;
    __u32 options;
};

#endif // AUTUMN_XDP_COMPAT_H
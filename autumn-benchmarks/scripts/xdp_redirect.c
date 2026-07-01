#include <linux/bpf.h>
#include <bpf/bpf_helpers.h>

// Array mapping hardware Network queue IDs to specific XDP socket file-descriptors.
struct {
    __uint(type, BPF_MAP_TYPE_XSKMAP);
    __uint(max_entries, 64);
    __type(key, int);
    __type(value, int);
} xsks_map SEC(".maps");

SEC("xdp")
int xdp_redirect_prog(struct xdp_md *ctx)
{
    // Capture the hardware queue index where the packet physically arrived
    int index = ctx->rx_queue_index;

    // Verify if our Kotlin Application successfully launched an AF_XDP socket listening on this queue
    if (bpf_map_lookup_elem(&xsks_map, &index)) {
        // Yes! Bypass the entire Linux Kernel Network Stack (IP/TCP/UDP) natively
        // Redirect the raw payload frames natively into our Kotlin UMEM mapped boundaries 
        return bpf_redirect_map(&xsks_map, index, XDP_PASS);
    }
    
    // Fallback: If no socket active, pass to standard linux stack safely
    return XDP_PASS;
}

char _license[] SEC("license") = "GPL";

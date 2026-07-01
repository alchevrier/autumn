#!/bin/bash
# Creates a Virtual Ethernet Pair to test Autumn's XDP Hardware Bypassing
# Run with sudo!

echo "[Autumn OS] Tearing down old veth interfaces if they exist..."
ip link delete veth0 2>/dev/null || true

echo "[Autumn OS] Creating veth0 <--> veth1 Virtual Ethernet Pair..."
ip link add dev veth0 type veth peer name veth1

echo "[Autumn OS] Bringing interfaces UP..."
ip link set dev veth0 up
ip link set dev veth1 up

echo "[Autumn OS] Assigning IPs..."
ip addr add 10.0.0.1/24 dev veth0
ip addr add 10.0.0.2/24 dev veth1

# Optional: Disable IPv6 to reduce background noise packets
sysctl -w net.ipv6.conf.veth0.disable_ipv6=1 >/dev/null 2>&1
sysctl -w net.ipv6.conf.veth1.disable_ipv6=1 >/dev/null 2>&1

echo "[Autumn OS] Compiling eBPF Kernel Bypass Program natively..."
clang -O2 -g -Wall -target bpf -c xdp_redirect.c -o xdp_redirect.o

echo "[Autumn OS] Attaching eBPF Bypasser to veth1..."
# Forces NIC to leverage our BPF hook on inbound routes
ip link set dev veth1 xdp obj xdp_redirect.o sec xdp

echo "[Autumn OS] Veth Pair Active! XDP Socket can now bind to veth1."

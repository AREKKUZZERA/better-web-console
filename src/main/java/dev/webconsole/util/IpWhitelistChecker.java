package dev.webconsole.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks if an IP address is within an allowed whitelist (supports CIDR notation).
 */
public class IpWhitelistChecker {

    private record CidrBlock(byte[] network, int prefixLen, int bytes) {}

    private final List<CidrBlock> blocks = new ArrayList<>();
    private final boolean allowAll;

    public IpWhitelistChecker(List<String> whitelist) {
        if (whitelist == null || whitelist.isEmpty()) {
            this.allowAll = true;
            return;
        }
        this.allowAll = false;
        for (String entry : whitelist) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            try {
                if (entry.contains("/")) {
                    String[] parts = entry.split("/", 2);
                    InetAddress addr = InetAddress.getByName(parts[0]);
                    int prefix = Integer.parseInt(parts[1]);
                    blocks.add(new CidrBlock(addr.getAddress(), prefix, addr.getAddress().length));
                } else {
                    InetAddress addr = InetAddress.getByName(entry);
                    byte[] b = addr.getAddress();
                    blocks.add(new CidrBlock(b, b.length * 8, b.length));
                }
            } catch (UnknownHostException | NumberFormatException e) {
                // Log warning but continue
                System.err.println("[Better-WebConsole] Invalid whitelist entry: " + entry);
            }
        }
    }

    public boolean isAllowed(String remoteIp) {
        if (allowAll) return true;
        if (remoteIp == null) return false;

        // Strip IPv6 brackets and port
        remoteIp = remoteIp.replaceAll("^\\[|]:\\d+$|:\\d+$", "");

        try {
            byte[] addr = InetAddress.getByName(remoteIp).getAddress();
            for (CidrBlock block : blocks) {
                if (block.bytes() != addr.length) continue;
                if (matchesCidr(addr, block.network(), block.prefixLen())) return true;
            }
        } catch (UnknownHostException e) {
            return false;
        }
        return false;
    }

    private boolean matchesCidr(byte[] addr, byte[] network, int prefixLen) {
        int fullBytes = prefixLen / 8;
        int remainBits = prefixLen % 8;

        for (int i = 0; i < fullBytes && i < addr.length; i++) {
            if (addr[i] != network[i]) return false;
        }
        if (remainBits > 0 && fullBytes < addr.length) {
            int mask = 0xFF << (8 - remainBits) & 0xFF;
            if ((addr[fullBytes] & mask) != (network[fullBytes] & mask)) return false;
        }
        return true;
    }
}

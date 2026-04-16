package org.peoplemesh.util;

import org.jboss.logging.Logger;

import java.net.InetAddress;
import java.security.MessageDigest;
import java.util.List;

public final class IpAllowlistUtils {
    private static final Logger LOG = Logger.getLogger(IpAllowlistUtils.class);

    private IpAllowlistUtils() {
    }

    public static boolean matchesAnyCidr(String ip, List<String> cidrs) {
        try {
            byte[] addr = InetAddress.getByName(ip).getAddress();
            for (String cidr : cidrs) {
                if (cidr.contains("/")) {
                    String[] parts = cidr.split("/", 2);
                    byte[] net = InetAddress.getByName(parts[0]).getAddress();
                    int prefixLen = Integer.parseInt(parts[1]);
                    if (addr.length == net.length && prefixMatch(addr, net, prefixLen)) {
                        return true;
                    }
                } else {
                    byte[] single = InetAddress.getByName(cidr).getAddress();
                    if (MessageDigest.isEqual(addr, single)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            LOG.debugf("CIDR matching failed");
        }
        return false;
    }

    private static boolean prefixMatch(byte[] addr, byte[] net, int prefixLen) {
        int fullBytes = prefixLen / 8;
        for (int i = 0; i < fullBytes && i < addr.length; i++) {
            if (addr[i] != net[i]) {
                return false;
            }
        }
        int remaining = prefixLen % 8;
        if (remaining > 0 && fullBytes < addr.length) {
            int mask = 0xFF << (8 - remaining);
            if ((addr[fullBytes] & mask) != (net[fullBytes] & mask)) {
                return false;
            }
        }
        return true;
    }
}

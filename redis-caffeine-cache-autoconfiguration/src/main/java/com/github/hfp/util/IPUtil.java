package com.github.hfp.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * todo 支持 pattern 过滤 IP
 */
public class IPUtil {

    private static String ipAddress = null;
    private static ReentrantLock lock = new ReentrantLock();

    public static String getIP() {
        if (ipAddress != null) {
            return ipAddress;
        }

        try {
            lock.lock();
            if (ipAddress != null) {
                return ipAddress;
            }
            final List<String> inetAddresses = resolveLocalIps();
            if (inetAddresses.size() > 0) {
                ipAddress = inetAddresses.get(0);
            } else {
                ipAddress = InetAddress.getLocalHost().getHostAddress();
            }
            return ipAddress;
        } catch (UnknownHostException e) {
            throw new RuntimeException(e.getCause());
        } finally {
            lock.unlock();
        }
    }

    private static Set<InetAddress> resolveLocalAddresses() {
        Set<InetAddress> addrs = new HashSet<>();
        Enumeration<NetworkInterface> ns = null;
        try {
            ns = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            // ignored...
        }
        while (ns != null && ns.hasMoreElements()) {
            NetworkInterface n = ns.nextElement();
            Enumeration<InetAddress> is = n.getInetAddresses();
            while (is.hasMoreElements()) {
                InetAddress i = is.nextElement();
                if (!i.isLoopbackAddress() && !i.isLinkLocalAddress() && !i.isMulticastAddress()
                        && !isSpecialIp(i.getHostAddress())) {
                    addrs.add(i);
                }
            }
        }
        return addrs;
    }

    private static List<String> resolveLocalIps() {
        Set<InetAddress> addrs = resolveLocalAddresses();
        return addrs.stream().map(InetAddress::getHostAddress).collect(Collectors.toList());
    }

    private static boolean isSpecialIp(String ip) {
        if (ip.contains(":")) {
            return true;
        }
        if (ip.startsWith("127.")) {
            return true;
        }
        if (ip.startsWith("10.8")) {
            return true;
        }
        if (ip.startsWith("118.178")) {
            return true;
        }
        return "255.255.255.255".equals(ip);
    }
}

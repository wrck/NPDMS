package com.dp.deviceops.core.model;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** DNS-free SSH target validation shared by HTTP and transport adapters. */
public final class IpLiteralAddress {
    private IpLiteralAddress() { }
    public static String requireRoutable(String host) {
        if (host == null || host.isBlank() || host.length() > 253) throw new IllegalArgumentException("host must be an IP literal");
        String value=host.strip(); InetAddress address;
        try { if (value.indexOf(':') >= 0) { if(value.indexOf('%')>=0 || !value.chars().allMatch(c -> Character.digit(c,16)>=0||c==':'||c=='.')) throw new IllegalArgumentException("host must be an IP literal"); address=InetAddress.getByName(value); } else { String[] octets=value.split("\\.",-1); if(octets.length!=4) throw new IllegalArgumentException("host must be an IP literal"); byte[] bytes=new byte[4]; for(int i=0;i<4;i++){ if(!octets[i].matches("(0|[1-9]\\d{0,2})")) throw new IllegalArgumentException("host must be an IP literal"); int n=Integer.parseInt(octets[i]); if(n>255) throw new IllegalArgumentException("host must be an IP literal"); bytes[i]=(byte)n; } address=InetAddress.getByAddress(bytes); } }
        catch (UnknownHostException ex) { throw new IllegalArgumentException("host must be an IP literal"); }
        if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isMulticastAddress()) throw new IllegalArgumentException("host is not allowed");
        return address.getHostAddress();
    }
}

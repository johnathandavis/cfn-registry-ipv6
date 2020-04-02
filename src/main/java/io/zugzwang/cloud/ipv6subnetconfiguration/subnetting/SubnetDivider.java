package io.zugzwang.cloud.ipv6subnetconfiguration.subnetting;

import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import inet.ipaddr.ipv4.IPv4Address;
import inet.ipaddr.ipv6.IPv6Address;
import inet.ipaddr.ipv6.IPv6AddressNetwork;

public class SubnetDivider {

    private final String bigCidr;

    public SubnetDivider(final String bigCidr) {
        this.bigCidr = bigCidr;
        parseAddress(bigCidr);
    }

    public List<String> smallCidrs(final int size) {
        IPAddress address = parseAddress(bigCidr);
        IPAddress newSubnets = address.setPrefixLength(size, false);
        TreeSet<IPAddress> subnetSet = new TreeSet<>();

        Iterator<? extends IPAddress> iterator = newSubnets.prefixBlockIterator();

        while (iterator.hasNext()) {
            subnetSet.add((IPAddress)iterator.next());
        }
        return subnetSet.stream()
                .map(IPAddress::toCompressedString)
                .map(String::toLowerCase)
                .collect(Collectors.toList());
    }

    public int rangeIndexOf(final String smallRange, final int size) {
        List<String> smallCidrs = smallCidrs(size);
        return smallCidrs.indexOf(smallRange.toLowerCase());
    }

    public String nthSmallCidr(final int size, final int n) {
        List<String> smallCidrs = smallCidrs(size);
        return smallCidrs.get(n);
    }

    protected static IPAddress parseAddress(String cidr) {
        IPAddressString addressString = new IPAddressString(cidr);
        return addressString.getAddress();
    }
}

package io.zugzwang.cloud.ipv6subnetconfiguration.subnetting;

import inet.ipaddr.ipv6.IPv6Address;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class SubnetDividerTest {

    private static final String BIG_CIDR = "2001:db8::/32";

    @Test
    public void dividesIpv4SubnetsCorrectly() {
        SubnetDivider divider = new SubnetDivider("192.168.0.0/16");
        List<String> smallCidrs = divider.smallCidrs(24);

        System.out.println(smallCidrs);

        Assertions.assertEquals(256, smallCidrs.size());
            Assertions.assertEquals("192.168.0.0/24", smallCidrs.get(0));
    }

    @Test
    public void dividesIpv6SubnetsCorrectly() {
        SubnetDivider divider = new SubnetDivider(BIG_CIDR);
        List<String> smallCidrs = divider.smallCidrs(34);

        System.out.println(smallCidrs);

        Assertions.assertEquals(4, smallCidrs.size());
        Assertions.assertEquals("2001:db8::/34", smallCidrs.get(0));
    }

    @Test
    public void findsIpv6SubnetsIndexOfCorrectly() {
        SubnetDivider divider = new SubnetDivider(BIG_CIDR);
        int indexOf = divider.rangeIndexOf("2001:db8:4000::/34", 34);

        Assertions.assertEquals(1, indexOf);
    }

    @Test
    public void parsesAddress() {
        IPv6Address address = (IPv6Address)SubnetDivider.parseAddress(BIG_CIDR);
        Assertions.assertEquals(32, address.getPrefixLength());
    }
}

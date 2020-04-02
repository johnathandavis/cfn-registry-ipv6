package io.zugzwang.cloud.ipv6subnetconfiguration.info;

import com.amazonaws.services.ec2.model.Subnet;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data @Builder
public class SubnetInfo {

    private boolean publicIpv4Enabled;
    private boolean alreadyAllocated;
    private String subnetCidrBlock;
    private String subnetCidrId;
    private String vpcId;
    private String vpcCidrBlock;
    private String vpcCidrId;
    private List<Subnet> vpcSubnets;
    private int subnetIndex;
}

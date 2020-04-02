package io.zugzwang.cloud.ipv6subnetconfiguration.info;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.*;
import io.zugzwang.cloud.ipv6subnetconfiguration.subnetting.SubnetDivider;
import io.zugzwang.cloud.ipv6subnetconfiguration.tagger.SubnetTagger;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;

import java.util.*;
import java.util.stream.Collectors;

public class SubnetInfoProvider {

    private final AmazonEC2 ec2;
    private final AmazonWebServicesClientProxy clientProxy;
    private final String subnetId;
    final Logger logger;

    public SubnetInfoProvider(
            final AmazonEC2 ec2,
            final AmazonWebServicesClientProxy clientProxy,
            final String subnetId,
            final Logger logger) {
        this.ec2 = ec2;
        this.clientProxy = clientProxy;
        this.subnetId = subnetId;
        this.logger = logger;
    }

    public SubnetInfo loadSubnetInfo(int subnetCidrSize) {
        Subnet thisSubnet = getThisSubnet();
        String vpcId = thisSubnet.getVpcId();
        Vpc vpc = getVpc(vpcId);

        List<Subnet> vpcSubnets = getVpcSubnets(vpcId);
        logger.log(String.format("Vpc %s has %s subnets: %s",
                vpcId,
                vpcSubnets.size(),
                String.join(", ", vpcSubnets.stream().map(Subnet::getSubnetId).collect(Collectors.toList()))));

        List<Subnet> subnetsWithBlocks = vpcSubnets.stream()
                .filter(this::subnetHasIPv6Block)
                .collect(Collectors.toList());

        boolean iHaveBlock = subnetHasIPv6Block(thisSubnet);
        logger.log(String.format(
                "Status of current subnet %s: Has block? %s",
                subnetId,
                iHaveBlock));

        int subnetIndex = subnetsWithBlocks.size();
        if (iHaveBlock) {
            Optional<Integer> maybeIndex = SubnetTagger.getSubnetIndexFromTag(thisSubnet);
            if (maybeIndex.isPresent()) {
                subnetIndex = maybeIndex.get();
            } else {
                throw new IllegalStateException(String.format(
                        "Subnet with ID %s has an IPv6 block but is missing the index tag.",
                        subnetId
                ));
            }
        } else {
            boolean foundSubnet = false;
            Map<Integer, Subnet> indexMap = new HashMap<>();
            for (Subnet sub : subnetsWithBlocks) {
                Optional<Integer> indexOptional = SubnetTagger.getSubnetIndexFromTag(sub);
                int index = indexOptional.get();
                indexMap.put(index, sub);
            }

            for (int i = 0; i < Integer.MAX_VALUE; i++) {
                if (indexMap.containsKey(i)) {
                    continue;
                }
                subnetIndex = i;
                break;
            }
        }

        String vpcCidrBlock;
        String vpcCidrAllocationId;
        if (vpc.getIpv6CidrBlockAssociationSet() != null && vpc.getIpv6CidrBlockAssociationSet().size() > 0) {
            vpcCidrBlock = vpc.getIpv6CidrBlockAssociationSet().get(0).getIpv6CidrBlock();
            vpcCidrAllocationId = vpc.getIpv6CidrBlockAssociationSet().get(0).getAssociationId();
        } else {
            throw new IllegalArgumentException(String.format(
                    "The subnet provided exists in VPC %s, but that VPC has no valid IPv6 CidrBlockAssociation.",
                    vpcId));
        }

        SubnetDivider divider = new SubnetDivider(vpcCidrBlock);
        String subnetCidrBlock = divider.nthSmallCidr(subnetCidrSize, subnetIndex);
        String subnetCidrId = null;

        logger.log(String.format(
                "Decided subnet %s will have subnet index %s, which divides VPC subnet %s into the smaller subnet %s.",
                subnetId, subnetIndex, vpcCidrBlock, subnetCidrBlock));

        if (subnetHasIPv6Block(thisSubnet)) {
            subnetCidrId = thisSubnet.getIpv6CidrBlockAssociationSet().get(0).getAssociationId();
        }

        SubnetInfo info = SubnetInfo.builder()
                .publicIpv4Enabled(thisSubnet.isMapPublicIpOnLaunch())
                .vpcId(vpcId)
                .vpcCidrBlock(vpcCidrBlock)
                .vpcCidrId(vpcCidrAllocationId)
                .vpcSubnets(vpcSubnets)
                .subnetCidrBlock(subnetCidrBlock)
                .subnetCidrId(subnetCidrId)
                .subnetIndex(subnetIndex)
                .alreadyAllocated(subnetHasIPv6Block(thisSubnet))
                .build();

        logger.log(String.format("Final subnet information: %s", info.toString()));
        return info;
    }

    private Vpc getVpc(String vpcId) {
        DescribeVpcsRequest request = new DescribeVpcsRequest();
        request.setVpcIds(Collections.singletonList(vpcId));

        DescribeVpcsResult result = clientProxy.injectCredentialsAndInvoke(request, ec2::describeVpcs);
        return result.getVpcs().get(0);
    }

    private Subnet getThisSubnet() {
        DescribeSubnetsRequest request = new DescribeSubnetsRequest();
        request.setSubnetIds(Collections.singletonList(this.subnetId));

        DescribeSubnetsResult result = clientProxy.injectCredentialsAndInvoke(request, ec2::describeSubnets);
        return result.getSubnets().get(0);
    }

    private List<Subnet> getVpcSubnets(String vpcId) {

        List<Subnet> subnets = new ArrayList<>();
        String nextToken = null;
        do {
            DescribeSubnetsRequest request = new DescribeSubnetsRequest();
            Filter vpcSubnetFilter = new Filter();
            vpcSubnetFilter.setName("vpc-id");
            vpcSubnetFilter.setValues(Collections.singletonList(vpcId));
            request.setFilters(Collections.singletonList(vpcSubnetFilter));
            request.setNextToken(nextToken);

            DescribeSubnetsResult result = clientProxy.injectCredentialsAndInvoke(request, ec2::describeSubnets);
            if (result.getSubnets() != null) {
                subnets.addAll(result.getSubnets());
            }
            nextToken = result.getNextToken();

        } while (nextToken != null);

        return subnets;
    }

    private boolean subnetHasIPv6Block(Subnet subnet) {
        return subnet.getIpv6CidrBlockAssociationSet() != null && subnet.getIpv6CidrBlockAssociationSet().size() > 0;
    }
    private boolean subnetMissingIPv6Block(Subnet subnet) {
        return subnet.getIpv6CidrBlockAssociationSet() == null || subnet.getIpv6CidrBlockAssociationSet().size() == 0;
    }
}

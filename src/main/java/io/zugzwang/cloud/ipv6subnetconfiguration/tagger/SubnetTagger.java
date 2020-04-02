package io.zugzwang.cloud.ipv6subnetconfiguration.tagger;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.*;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SubnetTagger {

    public static final String SUBNET_INDEX = "zugzwang:subnet-index";

    private final AmazonEC2 ec2;
    private final AmazonWebServicesClientProxy clientProxy;

    public SubnetTagger(
            final AmazonEC2 ec2,
            final AmazonWebServicesClientProxy clientProxy) {
        this.ec2 = ec2;
        this.clientProxy = clientProxy;
    }

    public void tagSubnet(String subnetId, int allocationIndex) {
        CreateTagsRequest request = new CreateTagsRequest();
        request.setResources(Collections.singletonList(subnetId));
        request.setTags(Collections.singletonList(new Tag(SUBNET_INDEX, Integer.toString(allocationIndex))));

        clientProxy.injectCredentialsAndInvoke(request, ec2::createTags);
    }

    public void untagSubnet(String subnetId) {

        DescribeSubnetsRequest describeRequest = new DescribeSubnetsRequest();
        describeRequest.setSubnetIds(Collections.singletonList(subnetId));

        DescribeSubnetsResult describeResult = clientProxy.injectCredentialsAndInvoke(describeRequest, ec2::describeSubnets);

        if (describeResult.getSubnets() == null || describeResult.getSubnets().size() == 0) {
            // Intentional no-op
            return;
        }

        try {
            Subnet subnet = describeResult.getSubnets().get(0);
            DeleteTagsRequest request = new DeleteTagsRequest();
            List<Tag> matchingTags = subnet.getTags().stream()
                    .filter(tag -> tag.getKey().equals(SUBNET_INDEX))
                    .collect(Collectors.toList());

            request.setResources(Collections.singletonList(subnetId));
            request.setTags(matchingTags);

            clientProxy.injectCredentialsAndInvoke(request, ec2::deleteTags);
        } catch (Throwable t) {
            // Intentional no-op
        }
    }

    public static Optional<Integer> getSubnetIndexFromTag(Subnet subnet) {
        if (subnet.getTags() == null) {
            return Optional.empty();
        }

        List<Tag> matchingTags = subnet.getTags().stream()
                .filter(tag -> tag.getKey().equals(SUBNET_INDEX))
                .collect(Collectors.toList());

        if (matchingTags.size() == 0) {
            return Optional.empty();
        }

        if (matchingTags.size() > 1) {
            throw new IllegalArgumentException(String.format(
                    "Provided subnet has %s matching subnet index tags.", matchingTags.size()));
        }

        Tag onlyTag = matchingTags.get(0);
        int subnetIndex;
        try {
            subnetIndex = Integer.parseInt(onlyTag.getValue());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(String.format(
                    "Provided subnet had a matching subnet index tag with an illegal value ('%s').",
                    onlyTag.getValue()));
        }
        return Optional.of(subnetIndex);
    }
}

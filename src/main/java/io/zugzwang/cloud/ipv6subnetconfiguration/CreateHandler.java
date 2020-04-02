package io.zugzwang.cloud.ipv6subnetconfiguration;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import io.zugzwang.cloud.ipv6subnetconfiguration.info.SubnetInfo;
import io.zugzwang.cloud.ipv6subnetconfiguration.info.SubnetInfoProvider;
import io.zugzwang.cloud.ipv6subnetconfiguration.tagger.SubnetTagger;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Collections;
import java.util.Set;

public class CreateHandler extends BaseHandler<CallbackContext> {

    private AmazonEC2 ec2;
    private AmazonWebServicesClientProxy proxy;
    private Logger logger;

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {

        this.ec2 = AmazonEC2ClientBuilder.standard().withRegion(request.getRegion()).build();
        this.proxy = proxy;
        this.logger = logger;

        final ResourceModel model = request.getDesiredResourceState();

        String associationId = createCidrAssociation(model.getSubnetId(), model.getCidrSize(), logger);
        String cidrBlock = waitForCidrAssociation(model.getSubnetId(), associationId);

        updateSubnetRouteTable(model.getRouteTableId(), model.getEgressOnlyInternetGatewayId());
        updateSubnetIpv6AssignmentOnStartup(model.getSubnetId());

        model.setSubnetIPv6CidrAssociationId(associationId);
        model.setSubnetIPv6Cidr(cidrBlock);

        return ProgressEvent.defaultSuccessHandler(model);
    }

    private String createCidrAssociation(String subnetId, int cidrSize, Logger logger) {

        SubnetInfoProvider provider = new SubnetInfoProvider(ec2, proxy, subnetId, logger);
        SubnetInfo subnetInfo = provider.loadSubnetInfo(cidrSize);

        SubnetTagger tagger = new SubnetTagger(ec2, proxy);
        tagger.tagSubnet(subnetId, subnetInfo.getSubnetIndex());

        AssociateSubnetCidrBlockRequest associateRequest = new AssociateSubnetCidrBlockRequest()
                .withIpv6CidrBlock(subnetInfo.getSubnetCidrBlock())
                .withSubnetId(subnetId);

        SubnetIpv6CidrBlockAssociation association = proxy.injectCredentialsAndInvoke(associateRequest, ec2::associateSubnetCidrBlock).getIpv6CidrBlockAssociation();
        return association.getAssociationId();
    }

    private String waitForCidrAssociation(String subnetId, String associationId) {
        while (true) {
            DescribeSubnetsRequest request = new DescribeSubnetsRequest()
                    .withSubnetIds(Collections.singleton(subnetId));
            DescribeSubnetsResult result = proxy.injectCredentialsAndInvoke(request, ec2::describeSubnets);
            Subnet subnet = result.getSubnets().get(0);
            if (subnet.getIpv6CidrBlockAssociationSet() == null) {
                throw new RuntimeException("Ipv6CidrBlockAssociationSet was null for subnet " + subnetId);
            }
            for (SubnetIpv6CidrBlockAssociation association : subnet.getIpv6CidrBlockAssociationSet()) {
                if (!association.getAssociationId().equals(associationId)) {
                    continue;
                }
                if ("associated".equals(association.getIpv6CidrBlockState().getState())) {
                    return association.getIpv6CidrBlock();
                }
            }

            try {
                Thread.sleep(3000);
            } catch (Throwable t) { }
        }
    }

    private void updateSubnetRouteTable(String routeTableId, String egressOnlyInternetGatewayId) {

        CreateRouteRequest request = new CreateRouteRequest()
                .withRouteTableId(routeTableId)
                .withEgressOnlyInternetGatewayId(egressOnlyInternetGatewayId)
                .withDestinationIpv6CidrBlock("::/0");
        proxy.injectCredentialsAndInvoke(request, ec2::createRoute);
    }

    private void updateSubnetIpv6AssignmentOnStartup(String subnetId) {
        ModifySubnetAttributeRequest modifyRequest = new ModifySubnetAttributeRequest();
        modifyRequest.setSubnetId(subnetId);
        modifyRequest.setAssignIpv6AddressOnCreation(true);
        proxy.injectCredentialsAndInvoke(modifyRequest, ec2::modifySubnetAttribute);
    }
}

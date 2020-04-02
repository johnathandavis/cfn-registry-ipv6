package io.zugzwang.cloud.ipv6subnetconfiguration.tagger;

import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;

import com.amazonaws.services.ec2.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.services.ec2.AmazonEC2;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class SubnetTaggerTest {

    private SubnetTagger tagger;

    @Mock private AmazonEC2 ec2Client;
    @Mock private AmazonWebServicesClientProxy proxy;

    @BeforeEach @SuppressWarnings({"unchecked", "rawtypes"})
    public void setup() {

        lenient().when(proxy.injectCredentialsAndInvoke(any(AmazonWebServiceRequest.class), any(Function.class)))
                .thenAnswer(new Answer<Object>() {
                    @Override @SuppressWarnings({"unchecked", "rawtypes"})
                    public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                        Function func = invocationOnMock.getArgument(1);
                        AmazonWebServiceRequest request = invocationOnMock.getArgument(0);
                        return func.apply(request);
                    }
                });

        tagger = new SubnetTagger(ec2Client, proxy);
    }

    @Test @SuppressWarnings("rawtypes")
    public void tagSubnetCallsApiCorrectly() {
        when(ec2Client.createTags(any(CreateTagsRequest.class))).thenReturn(new CreateTagsResult());

        tagger.tagSubnet("sn-1234", 4);

        ArgumentCaptor<CreateTagsRequest> requestCaptor = ArgumentCaptor.forClass(CreateTagsRequest.class);
        verify(ec2Client, times(1)).createTags(requestCaptor.capture());
        CreateTagsRequest request = requestCaptor.getValue();

        assertEquals("sn-1234", request.getResources().get(0));
        assertEquals(1, request.getResources().size());

        Tag tag = request.getTags().get(0);
        assertEquals(SubnetTagger.SUBNET_INDEX, tag.getKey());
        assertEquals("4", tag.getValue());
        assertEquals(1, request.getTags().size());
    }

    @Test @SuppressWarnings("rawtypes")
    public void untagSubnetCallsApiCorrectly() {
        when(ec2Client.describeSubnets(any(DescribeSubnetsRequest.class))).thenReturn(new DescribeSubnetsResult()
        .withSubnets(Collections.singleton(
                new Subnet()
                        .withTags(new Tag()
                                .withKey(SubnetTagger.SUBNET_INDEX)
                                .withValue("3")))));
        when(ec2Client.deleteTags(any(DeleteTagsRequest.class))).thenReturn(new DeleteTagsResult());

        tagger.untagSubnet("sn-1234");

        ArgumentCaptor<DeleteTagsRequest> requestCaptor = ArgumentCaptor.forClass(DeleteTagsRequest.class);
        verify(ec2Client, times(1)).deleteTags(requestCaptor.capture());
        DeleteTagsRequest request = requestCaptor.getValue();

        assertEquals("sn-1234", request.getResources().get(0));
        assertEquals(1, request.getResources().size());

        Tag tag = request.getTags().get(0);
        assertEquals(SubnetTagger.SUBNET_INDEX, tag.getKey());
        assertEquals("3", tag.getValue());
        assertEquals(1, request.getTags().size());
    }

    @Test @SuppressWarnings("rawtypes")
    public void getSubnetIndexFromTagFindsValidTag() {
        Subnet thisSubnet = new Subnet()
                .withTags(new Tag()
                        .withKey(SubnetTagger.SUBNET_INDEX)
                        .withValue("3"));

        Optional<Integer> subnetIndexOp = SubnetTagger.getSubnetIndexFromTag(thisSubnet);
        assertEquals(3, (int)subnetIndexOp.get());
    }

}

package com.codecoachai.common.feign.multipart;

import feign.Capability;
import feign.Client;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;

/**
 * Adds streaming multipart support without replacing the normal Feign client.
 */
public final class StreamingMultipartCapability implements Capability {

    private final LoadBalancerClient loadBalancerClient;

    public StreamingMultipartCapability(LoadBalancerClient loadBalancerClient) {
        this.loadBalancerClient = loadBalancerClient;
    }

    @Override
    public Client enrich(Client client) {
        return new StreamingMultipartClient(client, loadBalancerClient);
    }
}

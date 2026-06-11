package com.minispring.orderservice.config;

import com.minispring.grpc.service.UserGrpcServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class GrpcClientConfig {

    @Bean
    public UserGrpcServiceGrpc.UserGrpcServiceBlockingStub userGrpcServiceStub(GrpcChannelFactory channelFactory) {
        return UserGrpcServiceGrpc.newBlockingStub(channelFactory.createChannel("user-service"));
    }
}

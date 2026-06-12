package com.minispring.orderservice.config;

import com.minispring.grpc.service.UserGrpcServiceGrpc;
import com.minispring.orderservice.client.BearerTokenInterceptor;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class GrpcClientConfig {

    @Bean
    public UserGrpcServiceGrpc.UserGrpcServiceBlockingStub userGrpcServiceStub(
            GrpcChannelFactory channelFactory, BearerTokenInterceptor bearerTokenInterceptor) {
        Channel channel = channelFactory.createChannel("user-service-grpc");
        Channel interceptedChannel = ClientInterceptors.intercept(channel, bearerTokenInterceptor);
        return UserGrpcServiceGrpc.newBlockingStub(interceptedChannel);
    }
}

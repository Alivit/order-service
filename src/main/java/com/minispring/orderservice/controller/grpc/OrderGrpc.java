package com.minispring.orderservice.controller.grpc;

import com.minispring.grpc.order.GetOrderPriceByIdRequest;
import com.minispring.grpc.order.GetOrderPriceByIdResponse;
import com.minispring.grpc.order.OrderGrpcServiceGrpc;
import com.minispring.grpc.order.OrderPriceDto;
import com.minispring.orderservice.dto.response.OrderPriceView;
import com.minispring.orderservice.mapper.OrderMapper;
import com.minispring.orderservice.service.OrderService;
import io.grpc.stub.StreamObserver;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.security.access.prepost.PreAuthorize;

@Slf4j
@GrpcService
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER', 'ADMIN', 'INTERNAL_SERVICE')")
public class OrderGrpc extends OrderGrpcServiceGrpc.OrderGrpcServiceImplBase {

    private final OrderService orderService;
    private final OrderMapper grpcOrderMapper;

    @Override
    public void getOrderPriceById(
            GetOrderPriceByIdRequest request, StreamObserver<GetOrderPriceByIdResponse> responseObserver) {
        OrderPriceView priceView = orderService.getOrderPriceById(UUID.fromString(request.getOrderId()));
        OrderPriceDto orderPriceDto = grpcOrderMapper.toGrpcOrderPriceDto(priceView);

        GetOrderPriceByIdResponse response = GetOrderPriceByIdResponse.newBuilder()
                .setOrderPrice(orderPriceDto)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}

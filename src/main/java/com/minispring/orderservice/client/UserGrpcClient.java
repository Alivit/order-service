package com.minispring.orderservice.client;

import static com.minispring.orderservice.exception.ExceptionAnswer.EMAIL_NOT_FOUND;
import static com.minispring.orderservice.exception.ExceptionAnswer.USER_NOT_FOUND;

import com.minispring.grpc.service.GetUserByEmailRequest;
import com.minispring.grpc.service.GetUserByIdRequest;
import com.minispring.grpc.service.GetUsersByIdsRequest;
import com.minispring.grpc.service.GetUsersByIdsResponse;
import com.minispring.grpc.service.UserGrpcServiceGrpc;
import com.minispring.orderservice.dto.response.UserProfileView;
import com.minispring.orderservice.exception.ResourceNotFoundException;
import com.minispring.orderservice.mapper.UserMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserGrpcClient {

    private final UserGrpcServiceGrpc.UserGrpcServiceBlockingStub userGrpcStub;
    private final UserMapper userMapper;

    @Value("${app.grpc.timeout.seconds}")
    private long grpcTimeoutSeconds;

    @CircuitBreaker(name = "userServiceGrpc", fallbackMethod = "getUserFallback")
    public UserProfileView getUserByEmail(String email) {
        log.debug("Get user profile from user service for email={}", email);

        GetUserByEmailRequest request =
                GetUserByEmailRequest.newBuilder().setEmail(email).build();

        return executeGrpcCall(
                () -> userMapper.toView(userGrpcStub
                        .withDeadlineAfter(grpcTimeoutSeconds, TimeUnit.SECONDS)
                        .getUserByEmail(request)
                        .getUser()),
                String.format(EMAIL_NOT_FOUND, email));
    }

    @CircuitBreaker(name = "userServiceGrpc", fallbackMethod = "getUserFallback")
    public UserProfileView getUserById(UUID userId) {
        log.debug("Get user profile from user service for userId={}", userId);

        GetUserByIdRequest request =
                GetUserByIdRequest.newBuilder().setUserId(userId.toString()).build();

        return executeGrpcCall(
                () -> userMapper.toView(userGrpcStub
                        .withDeadlineAfter(grpcTimeoutSeconds, TimeUnit.SECONDS)
                        .getUserById(request)
                        .getUser()),
                String.format(USER_NOT_FOUND, userId));
    }

    @CircuitBreaker(name = "userServiceGrpc", fallbackMethod = "getUsersMapFallback")
    public Map<UUID, UserProfileView> getUsersByIds(Set<UUID> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyMap();
        }

        log.debug("Get user profiles from user service for userIds count={}", userIds.size());

        List<String> stringList = userIds.stream().map(UUID::toString).toList();

        GetUsersByIdsRequest request =
                GetUsersByIdsRequest.newBuilder().addAllUserIds(stringList).build();

        return executeGrpcCall(
                () -> {
                    GetUsersByIdsResponse response = userGrpcStub
                            .withDeadlineAfter(grpcTimeoutSeconds, TimeUnit.SECONDS)
                            .getUsersByIds(request);

                    return response.getUsersList().stream()
                            .map(userMapper::toView)
                            .collect(Collectors.toMap(UserProfileView::id, user -> user));
                },
                "Batch users not found");
    }

    private <T> T executeGrpcCall(Supplier<T> grpcCall, String notFoundMessage) {
        try {
            return grpcCall.get();
        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new ResourceNotFoundException(notFoundMessage);
            }
            log.error(
                    "gRPC call failed with status: {}. Reason: {}",
                    ex.getStatus().getCode(),
                    ex.getMessage());
            throw ex;
        }
    }

    private UserProfileView getUserFallback(Throwable ex) {
        if (ex instanceof ResourceNotFoundException || ex instanceof StatusRuntimeException) {
            throw (RuntimeException) ex;
        }
        log.warn("User service unavailable for reason={}", ex.getMessage());
        return null;
    }

    private Map<UUID, UserProfileView> getUsersMapFallback(Throwable ex) {
        if (ex instanceof ResourceNotFoundException || ex instanceof StatusRuntimeException) {
            throw (RuntimeException) ex;
        }
        log.warn("User service unavailable for batch request, reason={}", ex.getMessage());
        return Collections.emptyMap();
    }
}

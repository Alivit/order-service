package com.minispring.orderservice.client;

import com.minispring.grpc.service.GetUserByEmailRequest;
import com.minispring.grpc.service.GetUserByIdRequest;
import com.minispring.grpc.service.GetUsersByIdsRequest;
import com.minispring.grpc.service.GetUsersByIdsResponse;
import com.minispring.grpc.service.UserGrpcServiceGrpc;
import com.minispring.orderservice.dto.UserProfileDto;
import com.minispring.orderservice.exception.ResourceNotFoundException;
import com.minispring.orderservice.mapper.UserMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.minispring.orderservice.exception.ExceptionAnswer.EMAIL_NOT_FOUND;
import static com.minispring.orderservice.exception.ExceptionAnswer.USER_NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserGrpClient {

    private final UserGrpcServiceGrpc.UserGrpcServiceBlockingStub userGrpcStub;
    private final UserMapper userMapper;

    @CircuitBreaker(name = "userServiceGrpc", fallbackMethod = "getUserFallback")
    public UserProfileDto getUserByEmail(String email) {
        log.debug("Get user profile from user service for email={}", email);

        GetUserByEmailRequest request = GetUserByEmailRequest.newBuilder()
                .setEmail(email)
                .build();
        try {
            return userMapper.fromGrpcToUserProfileDto(userGrpcStub.withDeadlineAfter(2, TimeUnit.SECONDS)
                    .getUserByEmail(request)
                    .getUser()
            );
        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new ResourceNotFoundException(String.format(EMAIL_NOT_FOUND, email));
            }
            throw ex;
        }
    }

    @CircuitBreaker(name = "userServiceGrpc", fallbackMethod = "getUserFallback")
    public UserProfileDto getUserById(UUID userId) {
        log.debug("Get user profile from user service for userId={}", userId);

        GetUserByIdRequest request = GetUserByIdRequest.newBuilder()
                .setUserId(userId.toString())
                .build();
        try {
            return userMapper.fromGrpcToUserProfileDto(userGrpcStub.withDeadlineAfter(2, TimeUnit.SECONDS)
                    .getUserById(request)
                    .getUser()
            );
        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new ResourceNotFoundException(String.format(USER_NOT_FOUND, userId));
            }

            throw ex;
        }
    }

    @CircuitBreaker(name = "userServiceGrpc", fallbackMethod = "getUsersMapFallback")
    public Map<UUID, UserProfileDto> getUsersByIds(Set<UUID> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyMap();
        }

        log.debug("Get user profiles from user service for userIds count={}", userIds.size());

        List<String> stringList = userIds.stream()
                .map(UUID::toString)
                .toList();

        GetUsersByIdsRequest request = GetUsersByIdsRequest.newBuilder()
                .addAllUserIds(stringList)
                .build();

        try {
            GetUsersByIdsResponse response = userGrpcStub.withDeadlineAfter(2, TimeUnit.SECONDS)
                    .getUsersByIds(request);

            return response.getUsersList().stream()
                    .map(userMapper::fromGrpcToUserProfileDto)
                    .collect(Collectors.toMap(UserProfileDto::id, user -> user));

        } catch (StatusRuntimeException ex) {
            log.error("Failed to fetch batch users via gRPC", ex);
            throw ex;
        }
    }

    private UserProfileDto getUserFallback(Throwable ex) {
        log.warn("User service unavailable for reason={}", ex.getMessage());
        return null;
    }

    private Map<UUID, UserProfileDto> getUsersMapFallback(Throwable ex) {
        log.warn("User service unavailable for batch request, reason={}", ex.getMessage());
        return Collections.emptyMap();
    }
}

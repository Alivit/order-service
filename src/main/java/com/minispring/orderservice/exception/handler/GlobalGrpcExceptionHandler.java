package com.minispring.orderservice.exception.handler;

import com.minispring.orderservice.exception.ResourceNotFoundException;
import com.minispring.orderservice.exception.ServiceUnavailableException;
import io.grpc.Status;
import io.grpc.StatusException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GlobalGrpcExceptionHandler implements GrpcExceptionHandler {

    @Override
    public @Nullable StatusException handleException(@NonNull Throwable exception) {
        return switch (exception) {
            case ResourceNotFoundException resourceNotFoundEx -> {
                log.warn("gRPC Resource not found: {}", resourceNotFoundEx.getMessage());
                yield Status.NOT_FOUND
                        .withDescription(resourceNotFoundEx.getMessage())
                        .asException();
            }
            case ObjectOptimisticLockingFailureException optimisticLockingEx -> {
                log.warn("gRPC Optimistic locking failure: {}", optimisticLockingEx.getMessage());
                yield Status.ABORTED
                        .withDescription("Concurrent update detected. Please retry.")
                        .asException();
            }
            case ServiceUnavailableException serviceUnavailableEx -> {
                log.warn("gRPC Service unavailable: {}", serviceUnavailableEx.getMessage());
                yield Status.UNAVAILABLE
                        .withDescription(serviceUnavailableEx.getMessage())
                        .asException();
            }
            case IllegalArgumentException illegalArgumentEx -> {
                log.warn("gRPC Invalid argument: {}", illegalArgumentEx.getMessage());
                yield Status.INVALID_ARGUMENT
                        .withDescription("Invalid argument format or UUID")
                        .asException();
            }
            case AuthenticationException authEx -> {
                log.warn("gRPC Authentication failed: {}", authEx.getMessage());
                yield Status.UNAUTHENTICATED
                        .withDescription("Invalid or missing token")
                        .asException();
            }
            case AccessDeniedException accessDeniedEx -> {
                log.warn("gRPC Access denied: {}", accessDeniedEx.getMessage());
                yield Status.PERMISSION_DENIED
                        .withDescription("Insufficient privileges")
                        .asException();
            }
            default -> null;
        };
    }
}

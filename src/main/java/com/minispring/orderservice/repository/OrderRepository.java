package com.minispring.orderservice.repository;

import com.minispring.orderservice.dto.OrderParamsDto;
import com.minispring.orderservice.model.Order;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<Order> findOrderWithItemsById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(UUID id);

    default Page<Order> findByParams(OrderParamsDto params, Pageable pageable) {
        if (params == null) {
            return findAll(pageable);
        }

        Specification<Order> specification = Specification.where(statusesIn(params.statuses()))
                .and(createdAtBetween(params.createdAtFrom(), params.createdAtTo()));

        return findAll(specification, pageable);
    }

    private Specification<Order> statusesIn(List<String> statuses) {
        return (root, query, cb) -> CollectionUtils.isEmpty(statuses)
                ? null
                : root.get("status").in(statuses);
    }

    private Specification<Order> createdAtBetween(Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

}
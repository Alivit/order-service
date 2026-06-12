package com.minispring.orderservice.repository;

import com.minispring.orderservice.dto.OrderParamsDto;
import com.minispring.orderservice.model.Order;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    @Query(value = "SELECT * FROM orders WHERE id = :id", nativeQuery = true)
    Optional<Order> findOrderByIdIncludingDeleted(@Param("id") UUID id);

    @Query("SELECT o FROM Order o WHERE o.userId = :userId AND (:includeDeleted = true OR o.deleted = false)")
    List<Order> findAllByUserId(@Param("userId") UUID userId, @Param("includeDeleted") boolean includeDeleted);

    Optional<Order> findByIdAndUserIdAndDeletedFalse(UUID id, UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Order o SET o.deleted = true, o.updatedAt = :now WHERE o.id = :orderId AND o.userId = :userId")
    int deleteOrderByIdAndUserId(@Param("orderId") UUID orderId, @Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Order o SET o.deleted = true, o.updatedAt = :now WHERE o.id = :orderId")
    int deleteOrderById(@Param("orderId") UUID orderId, @Param("now") Instant now);

    default Page<Order> findByParams(OrderParamsDto params, Pageable pageable) {
        if (params == null) {
            return findAll(isDeletedFilter(false), pageable);
        }
        
        Specification<Order> specification = Specification.where(statusesIn(params.statuses()))
                .and(createdAtBetween(params.createdAtFrom(), params.createdAtTo()))
                .and(isDeletedFilter(params.includeDeleted()));

        return findAll(specification, pageable);
    }
    
    private Specification<Order> isDeletedFilter(Boolean includeDeleted) {
        return (root, query, cb) -> {
            if (Boolean.TRUE.equals(includeDeleted)) {
                return null;
            }
            return cb.equal(root.get("deleted"), false);
        };
    }

    private Specification<Order> statusesIn(List<String> statuses) {
        return (root, query, cb) ->
                CollectionUtils.isEmpty(statuses) ? null : root.get("status").in(statuses);
    }

    private Specification<Order> createdAtBetween(Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));

            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
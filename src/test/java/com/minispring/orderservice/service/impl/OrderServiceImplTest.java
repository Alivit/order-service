package com.minispring.orderservice.service.impl;

import static com.minispring.orderservice.exception.ExceptionAnswer.ITEM_NOT_FOUND;
import static com.minispring.orderservice.exception.ExceptionAnswer.ORDER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.minispring.orderservice.dto.request.OrderCreateRequest;
import com.minispring.orderservice.dto.request.OrderItemCreateRequest;
import com.minispring.orderservice.dto.request.OrderSearchCriteria;
import com.minispring.orderservice.dto.request.OrderUpdateRequest;
import com.minispring.orderservice.dto.response.OrderView;
import com.minispring.orderservice.dto.response.UserProfileView;
import com.minispring.orderservice.exception.ResourceNotFoundException;
import com.minispring.orderservice.mapper.OrderMapper;
import com.minispring.orderservice.model.Item;
import com.minispring.orderservice.model.Order;
import com.minispring.orderservice.model.Status;
import com.minispring.orderservice.repository.ItemRepository;
import com.minispring.orderservice.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
public class OrderServiceImplTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Nested
    class CreateTest {

        private OrderCreateRequest createDto;
        private UserProfileView userProfileView;
        private Order order;
        private OrderView expectedDto;
        private List<Item> items;

        @BeforeEach
        void init() {
            createDto = Instancio.create(OrderCreateRequest.class);
            userProfileView = Instancio.create(UserProfileView.class);
            order = Instancio.create(Order.class);
            expectedDto = Instancio.create(OrderView.class);

            List<Long> generatedItemIds = createDto.items().stream()
                    .map(OrderItemCreateRequest::itemId)
                    .toList();

            items = Instancio.ofList(Item.class).size(generatedItemIds.size()).create();

            for (int i = 0; i < items.size(); i++) {
                items.get(i).setId(generatedItemIds.get(i));
            }
        }

        @Test
        void createShouldReturnOrderProfileDto() {
            given(orderMapper.toEntity(createDto)).willReturn(order);
            given(itemRepository.findAllById(anySet())).willReturn(items);
            given(orderRepository.save(order)).willReturn(order);
            given(orderMapper.toView(order, userProfileView)).willReturn(expectedDto);

            OrderView result = orderService.create(createDto, userProfileView);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            verify(orderRepository).save(order);
        }

        @Test
        void createShouldThrowResourceNotFoundExceptionWhenItemDoesNotExist() {
            given(orderMapper.toEntity(createDto)).willReturn(order);
            given(itemRepository.findAllById(anySet())).willReturn(List.of());

            Long invalidItemId = createDto.items().getFirst().itemId();

            assertThatThrownBy(() -> orderService.create(createDto, userProfileView))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(ITEM_NOT_FOUND, invalidItemId));

            verify(orderRepository, never()).save(any(Order.class));
        }
    }

    @Nested
    class GetByIdTest {

        @Test
        void getByIdShouldReturnOrderProfileDtoWhenOrderExists() {
            UUID orderId = UUID.randomUUID();
            UserProfileView userProfileView = Instancio.create(UserProfileView.class);
            Order order = Instancio.create(Order.class);
            OrderView expectedDto = Instancio.create(OrderView.class);

            given(orderRepository.findOrderByIdIncludingDeleted(orderId)).willReturn(Optional.of(order));
            given(orderMapper.toView(order, userProfileView)).willReturn(expectedDto);

            OrderView result = orderService.getById(orderId, userProfileView);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
        }

        @Test
        void getByIdShouldThrowResourceNotFoundExceptionWhenOrderDoesNotExist() {
            UUID orderId = UUID.randomUUID();
            UserProfileView userProfileView = Instancio.create(UserProfileView.class);

            given(orderRepository.findOrderByIdIncludingDeleted(orderId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getById(orderId, userProfileView))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(ORDER_NOT_FOUND, orderId));

            verifyNoInteractions(orderMapper);
        }
    }

    @Nested
    class GetByIdAndUserIdTest {

        @Test
        void getByIdAndUserIdShouldReturnOrderProfileDtoWhenOrderExists() {
            UUID orderId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Order order = Instancio.create(Order.class);
            OrderView expectedDto = Instancio.create(OrderView.class);

            given(orderRepository.findByIdAndUserIdAndDeletedFalse(orderId, userId))
                    .willReturn(Optional.of(order));
            given(orderMapper.toView(order, null)).willReturn(expectedDto);

            OrderView result = orderService.getByIdAndUserId(orderId, userId);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
        }

        @Test
        void getByIdAndUserIdShouldThrowResourceNotFoundExceptionWhenOrderDoesNotExist() {
            UUID orderId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            given(orderRepository.findByIdAndUserIdAndDeletedFalse(orderId, userId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getByIdAndUserId(orderId, userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(ORDER_NOT_FOUND, orderId));

            verifyNoInteractions(orderMapper);
        }
    }

    @Nested
    class GetAllByUserIdTest {

        @Test
        void getAllByUserIdShouldReturnListOfOrderProfileDto() {
            UUID userId = UUID.randomUUID();
            UserProfileView userProfileView = Instancio.create(UserProfileView.class);
            boolean includeDeleted = true;
            List<Order> orders = Instancio.ofList(Order.class).size(2).create();
            List<OrderView> expectedList =
                    Instancio.ofList(OrderView.class).size(2).create();

            given(orderRepository.findAllByUserId(userId, includeDeleted)).willReturn(orders);
            given(orderMapper.toView(orders.get(0), userProfileView)).willReturn(expectedList.get(0));
            given(orderMapper.toView(orders.get(1), userProfileView)).willReturn(expectedList.get(1));

            List<OrderView> result = orderService.getAllByUserId(userId, userProfileView, includeDeleted);

            assertThat(result).isNotNull().hasSize(2).containsExactlyElementsOf(expectedList);
        }

        @Test
        void getAllByUserIdShouldReturnEmptyListWhenNoOrdersExist() {
            UUID userId = UUID.randomUUID();
            UserProfileView userProfileView = Instancio.create(UserProfileView.class);
            boolean includeDeleted = false;

            given(orderRepository.findAllByUserId(userId, includeDeleted)).willReturn(List.of());

            List<OrderView> result = orderService.getAllByUserId(userId, userProfileView, includeDeleted);

            assertThat(result).isNotNull().isEmpty();
            verifyNoInteractions(orderMapper);
        }
    }

    @Nested
    class GetAllByTest {

        @Test
        void getAllByShouldReturnPageOfOrderProfileDto() {
            OrderSearchCriteria paramsDto = Instancio.create(OrderSearchCriteria.class);
            Pageable pageable = PageRequest.of(0, 10);
            List<Order> orders = Instancio.ofList(Order.class).size(2).create();
            List<OrderView> expectedList =
                    Instancio.ofList(OrderView.class).size(2).create();
            Page<Order> orderPage = new PageImpl<>(orders, pageable, orders.size());

            given(orderRepository.findByParams(paramsDto, pageable)).willReturn(orderPage);
            given(orderMapper.toView(orders.get(0), null)).willReturn(expectedList.get(0));
            given(orderMapper.toView(orders.get(1), null)).willReturn(expectedList.get(1));

            Page<OrderView> result = orderService.getAllBy(paramsDto, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).containsExactlyElementsOf(expectedList);
        }

        @Test
        void getAllByShouldReturnEmptyPageWhenNoOrdersMatchParams() {
            OrderSearchCriteria paramsDto = Instancio.create(OrderSearchCriteria.class);
            Pageable pageable = PageRequest.of(0, 10);

            given(orderRepository.findByParams(paramsDto, pageable)).willReturn(Page.empty(pageable));

            Page<OrderView> result = orderService.getAllBy(paramsDto, pageable);

            assertThat(result).isNotNull().isEmpty();
            verifyNoInteractions(orderMapper);
        }
    }

    @Nested
    class UpdateTest {

        private UUID orderId;
        private Order existingOrder;
        private OrderView expectedDto;

        @BeforeEach
        void init() {
            orderId = UUID.randomUUID();
            existingOrder = Instancio.create(Order.class);
            expectedDto = Instancio.create(OrderView.class);
        }

        @Test
        void updateShouldReturnUpdatedOrderProfileDtoWhenStatusChanges() {
            existingOrder.setStatus(Status.CREATED);
            OrderUpdateRequest updateDto = new OrderUpdateRequest(Status.PAID);

            // ИСПРАВЛЕНО: Сервис вызывает findByIdAndDeletedFalse, а не findById
            given(orderRepository.findByIdAndDeletedFalse(orderId)).willReturn(Optional.of(existingOrder));
            given(orderRepository.saveAndFlush(existingOrder)).willReturn(existingOrder);
            given(orderMapper.toView(existingOrder, null)).willReturn(expectedDto);

            OrderView result = orderService.update(orderId, updateDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(existingOrder.getStatus()).isEqualTo(Status.PAID);
            verify(orderRepository).saveAndFlush(existingOrder);
        }

        @Test
        void updateShouldReturnOrderProfileWithNoChangesWhenStatusIsSame() {
            existingOrder.setStatus(Status.CREATED);
            OrderUpdateRequest updateDto = new OrderUpdateRequest(Status.CREATED);

            // ИСПРАВЛЕНО: Используем правильный метод репозитория
            given(orderRepository.findByIdAndDeletedFalse(orderId)).willReturn(Optional.of(existingOrder));
            given(orderMapper.toView(existingOrder, null)).willReturn(expectedDto);

            OrderView result = orderService.update(orderId, updateDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(existingOrder.getStatus()).isEqualTo(Status.CREATED);
            verify(orderRepository, never()).saveAndFlush(any());
        }

        @Test
        void updateShouldThrowResourceNotFoundExceptionWhenOrderDoesNotExist() {
            OrderUpdateRequest updateDto = Instancio.create(OrderUpdateRequest.class);

            given(orderRepository.findByIdAndDeletedFalse(orderId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.update(orderId, updateDto))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(ORDER_NOT_FOUND, orderId));

            verifyNoInteractions(orderMapper);
        }
    }

    @Nested
    class ProcessPaymentTest {

        private UUID orderId;
        private Order existingOrder;

        @BeforeEach
        void init() {
            orderId = UUID.randomUUID();
            existingOrder = Instancio.create(Order.class);
            existingOrder.setStatus(Status.CREATED);
        }

        @Test
        void processPaymentShouldChangeStatusToPaidWhenSuccess() {
            given(orderRepository.findByIdAndDeletedFalse(orderId)).willReturn(Optional.of(existingOrder));

            orderService.processPayment(orderId, "SUCCESS");

            assertThat(existingOrder.getStatus()).isEqualTo(Status.PAID);
        }

        @Test
        void processPaymentShouldChangeStatusToCanceledWhenFailed() {
            given(orderRepository.findByIdAndDeletedFalse(orderId)).willReturn(Optional.of(existingOrder));

            orderService.processPayment(orderId, "FAILED");

            assertThat(existingOrder.getStatus()).isEqualTo(Status.CANCELED);
        }

        @Test
        void processPaymentShouldChangeStatusToCanceledWhenRejected() {
            given(orderRepository.findByIdAndDeletedFalse(orderId)).willReturn(Optional.of(existingOrder));

            orderService.processPayment(orderId, "REJECTED");

            assertThat(existingOrder.getStatus()).isEqualTo(Status.CANCELED);
        }

        @Test
        void processPaymentShouldDoNothingWhenAlreadyPaid() {
            existingOrder.setStatus(Status.PAID);
            given(orderRepository.findByIdAndDeletedFalse(orderId)).willReturn(Optional.of(existingOrder));

            orderService.processPayment(orderId, "SUCCESS");

            assertThat(existingOrder.getStatus()).isEqualTo(Status.PAID);
        }

        @Test
        void processPaymentShouldDoNothingWhenUnknownStatusReceived() {
            given(orderRepository.findByIdAndDeletedFalse(orderId)).willReturn(Optional.of(existingOrder));

            orderService.processPayment(orderId, "UNKNOWN_STATUS");

            assertThat(existingOrder.getStatus()).isEqualTo(Status.CREATED);
        }

        @Test
        void processPaymentShouldThrowResourceNotFoundWhenOrderDoesNotExist() {
            given(orderRepository.findByIdAndDeletedFalse(orderId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.processPayment(orderId, "SUCCESS"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(ORDER_NOT_FOUND, orderId));
        }
    }

    @Nested
    class DeleteTest {

        @Test
        void deleteShouldSoftDeleteOrderWhenUserOwnsOrder() {
            UUID orderId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            given(orderRepository.deleteOrderByIdAndUserId(any(UUID.class), any(UUID.class), any(Instant.class)))
                    .willReturn(1);

            orderService.delete(orderId, userId);

            verify(orderRepository).deleteOrderByIdAndUserId(any(UUID.class), any(UUID.class), any(Instant.class));
        }

        @Test
        void deleteShouldThrowResourceNotFoundExceptionWhenUserDoesNotOwnOrderOrOrderDoesNotExist() {
            UUID orderId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            given(orderRepository.deleteOrderByIdAndUserId(any(UUID.class), any(UUID.class), any(Instant.class)))
                    .willReturn(0);

            assertThatThrownBy(() -> orderService.delete(orderId, userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(ORDER_NOT_FOUND, orderId));
        }
    }

    @Nested
    class DeleteByAdminTest {

        @Test
        void deleteShouldSoftDeleteOrderByAdminWhenOrderExists() {
            UUID orderId = UUID.randomUUID();

            given(orderRepository.deleteOrderById(any(UUID.class), any(Instant.class)))
                    .willReturn(1);

            orderService.delete(orderId);

            verify(orderRepository).deleteOrderById(any(UUID.class), any(Instant.class));
        }

        @Test
        void deleteShouldThrowResourceNotFoundExceptionWhenOrderDoesNotExist() {
            UUID orderId = UUID.randomUUID();

            given(orderRepository.deleteOrderById(any(UUID.class), any(Instant.class)))
                    .willReturn(0);

            assertThatThrownBy(() -> orderService.delete(orderId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(ORDER_NOT_FOUND, orderId));
        }
    }
}

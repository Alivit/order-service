package com.minispring.orderservice.service.impl;

import com.minispring.orderservice.dto.OrderCreateDto;
import com.minispring.orderservice.dto.OrderItemCreateDto;
import com.minispring.orderservice.dto.OrderParamsDto;
import com.minispring.orderservice.dto.OrderProfileDto;
import com.minispring.orderservice.dto.OrderUpdateDto;
import com.minispring.orderservice.dto.UserProfileDto;
import com.minispring.orderservice.exception.ResourceNotFoundException;
import com.minispring.orderservice.mapper.OrderMapper;
import com.minispring.orderservice.model.Item;
import com.minispring.orderservice.model.Order;
import com.minispring.orderservice.model.Status;
import com.minispring.orderservice.repository.ItemRepository;
import com.minispring.orderservice.repository.OrderRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

        private OrderCreateDto createDto;
        private UserProfileDto userProfileDto;
        private Order order;
        private OrderProfileDto expectedDto;
        private List<Item> items;

        @BeforeEach
        void init() {
            createDto = Instancio.create(OrderCreateDto.class);
            userProfileDto = Instancio.create(UserProfileDto.class);
            order = Instancio.create(Order.class);
            expectedDto = Instancio.create(OrderProfileDto.class);

            List<Long> generatedItemIds = createDto.items().stream()
                    .map(OrderItemCreateDto::itemId)
                    .toList();

            items = Instancio.ofList(Item.class).size(generatedItemIds.size()).create();

            for (int i = 0; i < items.size(); i++) {
                items.get(i).setId(generatedItemIds.get(i));
            }
        }

        @Test
        void createShouldReturnOrderProfileDto() {
            given(orderMapper.orderCreateDtoToOrder(createDto)).willReturn(order);
            given(itemRepository.findAllById(anySet())).willReturn(items);
            given(orderRepository.save(order)).willReturn(order);
            given(orderMapper.orderToOrderProfileDto(order, userProfileDto)).willReturn(expectedDto);

            OrderProfileDto result = orderService.create(createDto, userProfileDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            verify(orderRepository).save(order);
        }

        @Test
        void createShouldThrowResourceNotFoundExceptionWhenItemDoesNotExist() {
            given(orderMapper.orderCreateDtoToOrder(createDto)).willReturn(order);
            given(itemRepository.findAllById(anySet())).willReturn(List.of());

            Long invalidItemId = createDto.items().getFirst().itemId();

            assertThatThrownBy(() -> orderService.create(createDto, userProfileDto))
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
            UserProfileDto userProfileDto = Instancio.create(UserProfileDto.class);
            Order order = Instancio.create(Order.class);
            OrderProfileDto expectedDto = Instancio.create(OrderProfileDto.class);

            given(orderRepository.findOrderByIdIncludingDeleted(orderId)).willReturn(Optional.of(order));
            given(orderMapper.orderToOrderProfileDto(order, userProfileDto)).willReturn(expectedDto);

            OrderProfileDto result = orderService.getById(orderId, userProfileDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
        }

        @Test
        void getByIdShouldThrowResourceNotFoundExceptionWhenOrderDoesNotExist() {
            UUID orderId = UUID.randomUUID();
            UserProfileDto userProfileDto = Instancio.create(UserProfileDto.class);

            given(orderRepository.findOrderByIdIncludingDeleted(orderId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getById(orderId, userProfileDto))
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
            OrderProfileDto expectedDto = Instancio.create(OrderProfileDto.class);

            given(orderRepository.findByIdAndUserIdAndDeletedFalse(orderId, userId)).willReturn(Optional.of(order));
            given(orderMapper.orderToOrderProfileDto(order, null)).willReturn(expectedDto);

            OrderProfileDto result = orderService.getByIdAndUserId(orderId, userId);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
        }

        @Test
        void getByIdAndUserIdShouldThrowResourceNotFoundExceptionWhenOrderDoesNotExist() {
            UUID orderId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            given(orderRepository.findByIdAndUserIdAndDeletedFalse(orderId, userId)).willReturn(Optional.empty());

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
            UserProfileDto userProfileDto = Instancio.create(UserProfileDto.class);
            boolean includeDeleted = true;
            List<Order> orders = Instancio.ofList(Order.class).size(2).create();
            List<OrderProfileDto> expectedList = Instancio.ofList(OrderProfileDto.class).size(2).create();

            given(orderRepository.findAllByUserId(userId, includeDeleted)).willReturn(orders);
            given(orderMapper.orderToOrderProfileDto(orders.get(0), userProfileDto)).willReturn(expectedList.get(0));
            given(orderMapper.orderToOrderProfileDto(orders.get(1), userProfileDto)).willReturn(expectedList.get(1));

            List<OrderProfileDto> result = orderService.getAllByUserId(userId, userProfileDto, includeDeleted);

            assertThat(result).isNotNull().hasSize(2).containsExactlyElementsOf(expectedList);
        }

        @Test
        void getAllByUserIdShouldReturnEmptyListWhenNoOrdersExist() {
            UUID userId = UUID.randomUUID();
            UserProfileDto userProfileDto = Instancio.create(UserProfileDto.class);
            boolean includeDeleted = false;

            given(orderRepository.findAllByUserId(userId, includeDeleted)).willReturn(List.of());

            List<OrderProfileDto> result = orderService.getAllByUserId(userId, userProfileDto, includeDeleted);

            assertThat(result).isNotNull().isEmpty();
            verifyNoInteractions(orderMapper);
        }
    }

    @Nested
    class GetAllByTest {

        @Test
        void getAllByShouldReturnPageOfOrderProfileDto() {
            OrderParamsDto paramsDto = Instancio.create(OrderParamsDto.class);
            Pageable pageable = PageRequest.of(0, 10);
            List<Order> orders = Instancio.ofList(Order.class).size(2).create();
            List<OrderProfileDto> expectedList = Instancio.ofList(OrderProfileDto.class).size(2).create();
            Page<Order> orderPage = new PageImpl<>(orders, pageable, orders.size());

            given(orderRepository.findByParams(paramsDto, pageable)).willReturn(orderPage);
            given(orderMapper.orderToOrderProfileDto(orders.get(0), null)).willReturn(expectedList.get(0));
            given(orderMapper.orderToOrderProfileDto(orders.get(1), null)).willReturn(expectedList.get(1));

            Page<OrderProfileDto> result = orderService.getAllBy(paramsDto, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).containsExactlyElementsOf(expectedList);
        }

        @Test
        void getAllByShouldReturnEmptyPageWhenNoOrdersMatchParams() {
            OrderParamsDto paramsDto = Instancio.create(OrderParamsDto.class);
            Pageable pageable = PageRequest.of(0, 10);

            given(orderRepository.findByParams(paramsDto, pageable)).willReturn(Page.empty(pageable));

            Page<OrderProfileDto> result = orderService.getAllBy(paramsDto, pageable);

            assertThat(result).isNotNull().isEmpty();
            verifyNoInteractions(orderMapper);
        }
    }

    @Nested
    class UpdateTest {

        private UUID orderId;
        private Order existingOrder;
        private OrderProfileDto expectedDto;

        @BeforeEach
        void init() {
            orderId = UUID.randomUUID();
            existingOrder = Instancio.create(Order.class);
            expectedDto = Instancio.create(OrderProfileDto.class);
        }

        @Test
        void updateShouldReturnUpdatedOrderProfileDtoWhenStatusChanges() {
            existingOrder.setStatus(Status.CREATED);
            OrderUpdateDto updateDto = new OrderUpdateDto(Status.PAID);

            given(orderRepository.findById(orderId)).willReturn(Optional.of(existingOrder));
            given(orderMapper.orderToOrderProfileDtoWithoutItems(existingOrder, null)).willReturn(expectedDto);

            OrderProfileDto result = orderService.update(orderId, updateDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(existingOrder.getStatus()).isEqualTo(Status.PAID);
        }

        @Test
        void updateShouldReturnOrderProfileWithNoChangesWhenStatusIsSame() {
            existingOrder.setStatus(Status.CREATED);
            OrderUpdateDto updateDto = new OrderUpdateDto(Status.CREATED);

            given(orderRepository.findById(orderId)).willReturn(Optional.of(existingOrder));
            given(orderMapper.orderToOrderProfileDtoWithoutItems(existingOrder, null)).willReturn(expectedDto);

            OrderProfileDto result = orderService.update(orderId, updateDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(existingOrder.getStatus()).isEqualTo(Status.CREATED);
        }

        @Test
        void updateShouldThrowResourceNotFoundExceptionWhenOrderDoesNotExist() {
            OrderUpdateDto updateDto = Instancio.create(OrderUpdateDto.class);
            given(orderRepository.findById(orderId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.update(orderId, updateDto))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(ORDER_NOT_FOUND, orderId));

            verifyNoInteractions(orderMapper);
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

            given(orderRepository.deleteOrderById(any(UUID.class), any(Instant.class))).willReturn(1);

            orderService.delete(orderId);

            verify(orderRepository).deleteOrderById(any(UUID.class), any(Instant.class));
        }

        @Test
        void deleteShouldThrowResourceNotFoundExceptionWhenOrderDoesNotExist() {
            UUID orderId = UUID.randomUUID();

            given(orderRepository.deleteOrderById(any(UUID.class), any(Instant.class))).willReturn(0);

            assertThatThrownBy(() -> orderService.delete(orderId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(ORDER_NOT_FOUND, orderId));
        }
    }
}
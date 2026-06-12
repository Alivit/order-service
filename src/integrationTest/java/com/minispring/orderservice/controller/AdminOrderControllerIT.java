package com.minispring.orderservice.controller;

import com.minispring.orderservice.BaseIntegrationTest;
import com.minispring.orderservice.client.UserGrpClient;
import com.minispring.orderservice.dto.OrderCreateDto;
import com.minispring.orderservice.dto.OrderItemCreateDto;
import com.minispring.orderservice.dto.OrderUpdateDto;
import com.minispring.orderservice.dto.UserProfileDto;
import com.minispring.orderservice.exception.ResourceNotFoundException;
import com.minispring.orderservice.model.Item;
import com.minispring.orderservice.model.Order;
import com.minispring.orderservice.model.Status;
import com.minispring.orderservice.repository.ItemRepository;
import com.minispring.orderservice.repository.OrderRepository;
import org.instancio.Instancio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.minispring.orderservice.exception.ExceptionAnswer.EMAIL_NOT_FOUND;
import static com.minispring.orderservice.exception.ExceptionAnswer.USER_NOT_FOUND;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@AutoConfigureMockMvc
public class AdminOrderControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private UserGrpClient userGrpcService;

    private static final String BASE_URL = "/api/v1/admin/orders";

    private UserProfileDto mockUser;
    private Item savedItem;
    private Order savedOrder;

    @BeforeEach
    public void init(TestInfo testInfo) {
        mockUser = Instancio.of(UserProfileDto.class)
                .generate(field(UserProfileDto::email), gen -> gen.text().pattern("#c#c#c#c#c@domain.com"))
                .set(field(UserProfileDto::birthDate), LocalDate.now().minusYears(20))
                .create();
        given(userGrpcService.getUserByEmail(mockUser.email())).willReturn(mockUser);
        given(userGrpcService.getUserById(mockUser.id())).willReturn(mockUser);
        given(userGrpcService.getUsersByIds(anySet())).willReturn(Map.of(mockUser.id(), mockUser));

        if (testInfo.getTags().contains("init")) {
            return;
        }

        Item item = Instancio.of(Item.class)
                .ignore(field(Item::getId))
                .create();
        savedItem = itemRepository.saveAndFlush(item);

        Order order = Instancio.of(Order.class)
                .ignore(field(Order::getId))
                .ignore(field(Order::getVersion))
                .ignore(field(Order::getItems))
                .set(field(Order::getUserId), mockUser.id())
                .set(field(Order::getStatus), Status.CREATED)
                .set(field(Order::isDeleted), false)
                .set(field(Order::getCreatedAt), Instant.now())
                .create();
        savedOrder = orderRepository.saveAndFlush(order);
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        itemRepository.deleteAll();
    }

    @Nested
    class CreateOrderTest {

        @Test
        void createShouldReturnCreatedOrder() {
            OrderItemCreateDto itemDto = Instancio.of(OrderItemCreateDto.class)
                    .set(field(OrderItemCreateDto::itemId), savedItem.getId())
                    .set(field(OrderItemCreateDto::quantity), 2)
                    .create();

            OrderCreateDto createDto = Instancio.of(OrderCreateDto.class)
                    .set(field(OrderCreateDto::email), mockUser.email())
                    .set(field(OrderCreateDto::items), List.of(itemDto))
                    .create();

            assertThat(mockMvcTester.perform(post(BASE_URL)
                    .param("userEmail", mockUser.email())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(createDto))))
                    .hasStatus(HttpStatus.CREATED)
                    .bodyJson()
                    .hasPath("$.id")
                    .hasPathSatisfying("$.user.id", userId -> assertThat(userId).isEqualTo(mockUser.id().toString()));
        }

        @Test
        @Tag("init")
        void createShouldReturnNotFoundWhenItemDoesNotExist() {
            OrderItemCreateDto missingItemDto = Instancio.of(OrderItemCreateDto.class)
                    .set(field(OrderItemCreateDto::itemId), 999L)
                    .set(field(OrderItemCreateDto::quantity), 1)
                    .create();

            OrderCreateDto createDto = Instancio.of(OrderCreateDto.class)
                    .set(field(OrderCreateDto::email), mockUser.email())
                    .set(field(OrderCreateDto::items), List.of(missingItemDto))
                    .create();

            assertThat(mockMvcTester.perform(post(BASE_URL)
                    .param("userEmail", mockUser.email())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(createDto))))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        @Tag("init")
        void createShouldReturnServiceUnavailableWhenUserNotFound() {
            given(userGrpcService.getUserByEmail("unknown@mail.com")).willReturn(null);

            OrderItemCreateDto itemDto = Instancio.of(OrderItemCreateDto.class)
                    .set(field(OrderItemCreateDto::itemId), 1L)
                    .set(field(OrderItemCreateDto::quantity), 1)
                    .create();

            OrderCreateDto createDto = Instancio.of(OrderCreateDto.class)
                    .set(field(OrderCreateDto::email), "unknown@mail.com")
                    .set(field(OrderCreateDto::items), List.of(itemDto))
                    .create();

            assertThat(mockMvcTester.perform(post(BASE_URL)
                    .param("userEmail", "unknown@mail.com")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(createDto))))
                    .hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @Nested
    class GetOrderByIdTest {

        @Test
        void getOrderByIdShouldReturnSuccessfulOrderWithUserData() {
            assertThat(mockMvcTester.perform(get(BASE_URL + "/{id}", savedOrder.getId())
                    .param("userEmail", mockUser.email())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.id", id -> assertThat(id).isEqualTo(savedOrder.getId().toString()))
                    .hasPathSatisfying("$.user.email", email -> assertThat(email).isEqualTo(mockUser.email()));
        }

        @Test
        @Tag("init")
        void getOrderByIdShouldReturnNotFoundWhenOrderDoesNotExist() {
            assertThat(mockMvcTester.perform(get(BASE_URL + "/{id}", UUID.randomUUID())
                    .param("userEmail", mockUser.email())))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void getOrderByIdShouldReturnOrderWithNullUserWhenUserServiceIsUnavailable(){
            String email = "user@mail.com";
            given(userGrpcService.getUserByEmail(email)).willReturn(null);

            assertThat(mockMvcTester.perform(get(BASE_URL + "/{id}", savedOrder.getId())
                    .param("userEmail", email)))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.id", id -> assertThat(id).isEqualTo(savedOrder.getId().toString()))
                    .hasPathSatisfying("$.user", userField -> assertThat(userField).isNull());
        }

        @Test
        void getOrderByIdShouldReturnNotFoundWhenUserEmailDoesNotExist() {
            String unknownEmail = "invalid@mail.com";
            given(userGrpcService.getUserByEmail(unknownEmail))
                    .willThrow(new ResourceNotFoundException(String.format(EMAIL_NOT_FOUND, unknownEmail)));

            assertThat(mockMvcTester.perform(get(BASE_URL + "/{id}", savedOrder.getId())
                    .param("userEmail", unknownEmail)))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class GetAllOrdersTest {

        @Test
        void getAllOrdersShouldReturnPagedOrdersWithUserData() {
            assertThat(mockMvcTester.perform(get(BASE_URL)
                    .param("page", "0")
                    .param("size", "10")
                    .param("includeDeleted", "false")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPath("$.content")
                    .hasPathSatisfying("$.page.totalElements", total -> assertThat(total).isEqualTo(1))
                    .hasPathSatisfying("$.content[0].user.id", id -> assertThat(id).isEqualTo(mockUser.id().toString()))
                    .hasPathSatisfying("$.content[0].userServiceAvailable", av -> assertThat(av).isEqualTo(true));
        }

        @Test
        void getAllOrdersShouldFilterByStatuses() {
            assertThat(mockMvcTester.perform(get(BASE_URL)
                    .param("page", "0")
                    .param("size", "10")
                    .param("statuses", "CREATED", "PAID")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.page.totalElements", total -> assertThat(total).isEqualTo(1))
                    .hasPathSatisfying("$.content[0].status", status -> assertThat(status).isEqualTo("CREATED"));
        }

        @Test
        void getAllOrdersShouldFilterByValidDateRange() {
            Instant now = Instant.now();
            Instant dateFrom = now.minus(Duration.ofDays(1));
            Instant dateTo = now.plus(Duration.ofDays(1));

            assertThat(mockMvcTester.perform(get(BASE_URL)
                    .param("page", "0")
                    .param("size", "10")
                    .param("createdAtFrom", dateFrom.toString())
                    .param("createdAtTo", dateTo.toString())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.page.totalElements", total -> assertThat(total).isEqualTo(1));
        }

        @Test
        void getAllOrdersShouldReturnOrdersWithNullUserWhenGrpcBatchFails() {
            given(userGrpcService.getUsersByIds(anySet())).willReturn(emptyMap());

            assertThat(mockMvcTester.perform(get(BASE_URL)
                    .param("page", "0")
                    .param("size", "10")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.content[0].user", userField -> assertThat(userField).isNull())
                    .hasPathSatisfying("$.content[0].userServiceAvailable", av -> assertThat(av).isEqualTo(false));
        }

        @Test
        @Tag("init")
        void getAllOrdersShouldReturnEmptyPageWhenNoOrdersExist() {
            assertThat(mockMvcTester.perform(get(BASE_URL)
                    .param("page", "0")
                    .param("size", "10")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.page.totalElements", total -> assertThat(total).isEqualTo(0))
                    .hasPathSatisfying("$.content", content -> assertThat(content).asInstanceOf(LIST).isEmpty());
        }
    }

    @Nested
    class GetUserOrdersTest {

        @Test
        void getUserOrdersShouldReturnListOfOrdersWithUserDataWhenEverythingIsOk() {
            assertThat(mockMvcTester.perform(get(BASE_URL + "/user/{userId}", mockUser.id())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$", orders -> assertThat(orders).asInstanceOf(LIST).hasSize(1))
                    .hasPathSatisfying("$[0].user.email", email -> assertThat(email).isEqualTo(mockUser.email()));
        }

        @Test
        void getUserOrdersShouldReturnOrdersWithNullUserWhenUserServiceIsUnavailable() {
            UUID fallbackUserId = UUID.randomUUID();
            given(userGrpcService.getUserById(fallbackUserId)).willReturn(null);

            Order order = Instancio.of(Order.class)
                    .ignore(field(Order::getId))
                    .ignore(field(Order::getVersion))
                    .ignore(field(Order::getItems))
                    .set(field(Order::getUserId), fallbackUserId)
                    .set(field(Order::isDeleted), false)
                    .create();
            orderRepository.saveAndFlush(order);

            assertThat(mockMvcTester.perform(get(BASE_URL + "/user/{userId}", fallbackUserId)))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$", orders -> assertThat(orders).asInstanceOf(LIST).hasSize(1))
                    .hasPathSatisfying("$[0].user", userField -> assertThat(userField).isNull());
        }

        @Test
        void getUserOrdersShouldReturnNotFoundWhenUserDoesNotExistInUserService() {
            UUID unknownUserId = UUID.randomUUID();
            given(userGrpcService.getUserById(unknownUserId))
                    .willThrow(new ResourceNotFoundException(String.format(USER_NOT_FOUND, unknownUserId)));

            assertThat(mockMvcTester.perform(get(BASE_URL + "/user/{userId}", unknownUserId)))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        @Tag("init")
        void getUserOrdersShouldReturnEmptyListWhenUserHasNoOrders() {
            assertThat(mockMvcTester.perform(get(BASE_URL + "/user/{userId}", mockUser.id())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$", orders -> assertThat(orders).asInstanceOf(LIST).isEmpty());
        }
    }

    @Nested
    class UpdateOrderStatusTest {

        @Test
        void updateOrderStatusShouldReturnUpdatedOrderProfileDtoWithUserData() {
            OrderUpdateDto updateDto = new OrderUpdateDto(Status.PAID);

            assertThat(mockMvcTester.perform(put(BASE_URL + "/{id}/status", savedOrder.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(updateDto))))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.status", status -> assertThat(status).isEqualTo("PAID"))
                    .hasPathSatisfying("$.user.email", email -> assertThat(email).isEqualTo(mockUser.email()));

            Order updatedOrder = orderRepository.findById(savedOrder.getId()).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(Status.PAID);
        }

        @Test
        @Tag("init")
        void updateOrderStatusShouldReturnNotFoundWhenOrderDoesNotExist() {
            OrderUpdateDto updateDto = new OrderUpdateDto(Status.PROCESSING);

            assertThat(mockMvcTester.perform(put(BASE_URL + "/{id}/status", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(updateDto))))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void updateOrderStatusShouldReturnUpdatedOrderWithNullUserWhenUserServiceIsUnavailable() {
            given(userGrpcService.getUserById(mockUser.id())).willReturn(null);

            OrderUpdateDto updateDto = new OrderUpdateDto(Status.DELIVERED);

            assertThat(mockMvcTester.perform(put(BASE_URL + "/{id}/status", savedOrder.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(updateDto))))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.status", status -> assertThat(status).isEqualTo("DELIVERED"))
                    .hasPathSatisfying("$.user", userField -> assertThat(userField).isNull())
                    .hasPathSatisfying("$.userServiceAvailable", av -> assertThat(av).isEqualTo(false));

            Order updatedOrder = orderRepository.findById(savedOrder.getId()).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(Status.DELIVERED);
        }

        @Test
        void updateOrderStatusShouldReturnNotFound()  {
            given(userGrpcService.getUserById(mockUser.id()))
                    .willThrow(new ResourceNotFoundException(String.format(USER_NOT_FOUND, mockUser.id())));

            OrderUpdateDto updateDto = new OrderUpdateDto(Status.PROCESSING);

            assertThat(mockMvcTester.perform(put(BASE_URL + "/{id}/status", savedOrder.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(updateDto))))
                    .hasStatus(HttpStatus.NOT_FOUND);

            Order updatedOrder = orderRepository.findById(savedOrder.getId()).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(Status.PROCESSING);
        }
    }

    @Nested
    class DeleteOrderTest {

        @Test
        void deleteShouldSoftDeleteOrderSuccessfully() {
            assertThat(mockMvcTester.perform(delete(BASE_URL + "/{id}", savedOrder.getId())))
                    .hasStatus(HttpStatus.NO_CONTENT);

            Order deletedOrder = orderRepository.findOrderByIdIncludingDeleted(savedOrder.getId()).orElseThrow();
            assertThat(deletedOrder.isDeleted()).isTrue();
        }

        @Test
        @Tag("init")
        void deleteShouldReturnNotFoundWhenOrderDoesNotExist() {
            assertThat(mockMvcTester.perform(delete(BASE_URL + "/{id}", UUID.randomUUID())))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }
}
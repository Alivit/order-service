package com.minispring.orderservice.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.minispring.orderservice.BaseIntegrationTest;
import com.minispring.orderservice.client.UserGrpcClient;
import com.minispring.orderservice.dto.request.OrderCreateRequest;
import com.minispring.orderservice.dto.request.OrderItemCreateRequest;
import com.minispring.orderservice.dto.request.OrderUpdateRequest;
import com.minispring.orderservice.dto.response.UserProfileView;
import com.minispring.orderservice.exception.ResourceNotFoundException;
import com.minispring.orderservice.model.Item;
import com.minispring.orderservice.model.Order;
import com.minispring.orderservice.model.Status;
import com.minispring.orderservice.repository.ItemRepository;
import com.minispring.orderservice.repository.OrderRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.instancio.Instancio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

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
    private UserGrpcClient userGrpcService;

    private static final String BASE_URL = "/api/v1/admin/orders";
    private final UUID adminId = UUID.randomUUID();

    private UserProfileView mockUser;
    private Item savedItem;
    private Order savedOrder;

    @BeforeEach
    public void init() {
        mockUser = Instancio.of(UserProfileView.class)
                .generate(field(UserProfileView::email), gen -> gen.text().pattern("#c#c#c#c#c@domain.com"))
                .set(field(UserProfileView::birthDate), LocalDate.now().minusYears(20))
                .create();
        given(userGrpcClient.getUserByEmail(mockUser.email())).willReturn(mockUser);
        given(userGrpcClient.getUserById(mockUser.id())).willReturn(mockUser);
        given(userGrpcClient.getUsersByIds(anySet())).willReturn(Map.of(mockUser.id(), mockUser));

        Item item = Instancio.of(Item.class).ignore(field(Item::getId)).create();
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
            OrderItemCreateRequest itemDto = Instancio.of(OrderItemCreateRequest.class)
                    .set(field(OrderItemCreateRequest::itemId), savedItem.getId())
                    .set(field(OrderItemCreateRequest::quantity), 2)
                    .create();

            OrderCreateRequest createDto = Instancio.of(OrderCreateRequest.class)
                    .set(field(OrderCreateRequest::items), List.of(itemDto))
                    .create();

            assertThat(mockMvcTester.perform(post(BASE_URL)
                            .with(adminJwt(adminId))
                            .param("userEmail", mockUser.email())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(createDto))))
                    .hasStatus(HttpStatus.CREATED)
                    .bodyJson()
                    .hasPath("$.id")
                    .hasPathSatisfying("$.user.id", userId -> assertThat(userId)
                            .isEqualTo(mockUser.id().toString()));
        }

        @Test
        void createShouldReturnNotFoundWhenItemDoesNotExist() {
            OrderItemCreateRequest missingItemDto = Instancio.of(OrderItemCreateRequest.class)
                    .set(field(OrderItemCreateRequest::itemId), 999L)
                    .set(field(OrderItemCreateRequest::quantity), 1)
                    .create();

            OrderCreateRequest createDto = Instancio.of(OrderCreateRequest.class)
                    .set(field(OrderCreateRequest::items), List.of(missingItemDto))
                    .create();

            assertThat(mockMvcTester.perform(post(BASE_URL)
                            .with(adminJwt(adminId))
                            .param("userEmail", mockUser.email())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(createDto))))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void createShouldReturnServiceUnavailableWhenUserNotFound() {
            given(userGrpcClient.getUserByEmail("unknown@mail.com")).willReturn(null);

            OrderItemCreateRequest itemDto = Instancio.of(OrderItemCreateRequest.class)
                    .set(field(OrderItemCreateRequest::itemId), 1L)
                    .set(field(OrderItemCreateRequest::quantity), 1)
                    .create();

            OrderCreateRequest createDto = Instancio.of(OrderCreateRequest.class)
                    .set(field(OrderCreateRequest::items), List.of(itemDto))
                    .create();

            assertThat(mockMvcTester.perform(post(BASE_URL)
                            .with(adminJwt(adminId))
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
                            .with(adminJwt(adminId))
                            .param("userEmail", mockUser.email())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.id", id -> assertThat(id)
                            .isEqualTo(savedOrder.getId().toString()))
                    .hasPathSatisfying(
                            "$.user.email", email -> assertThat(email).isEqualTo(mockUser.email()));
        }

        @Test
        void getOrderByIdShouldReturnNotFoundWhenOrderDoesNotExist() {
            assertThat(mockMvcTester.perform(get(BASE_URL + "/{id}", UUID.randomUUID())
                            .with(adminJwt(adminId))
                            .param("userEmail", mockUser.email())))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void getOrderByIdShouldReturnOrderWithNullUserWhenUserServiceIsUnavailable() {
            String email = "user@mail.com";
            given(userGrpcClient.getUserByEmail(email)).willReturn(null);

            assertThat(mockMvcTester.perform(get(BASE_URL + "/{id}", savedOrder.getId())
                            .with(adminJwt(adminId))
                            .param("userEmail", email)))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.id", id -> assertThat(id)
                            .isEqualTo(savedOrder.getId().toString()))
                    .hasPathSatisfying(
                            "$.user", userField -> assertThat(userField).isNull());
        }

        @Test
        void getOrderByIdShouldReturnNotFoundWhenUserEmailDoesNotExist() {
            String unknownEmail = "invalid@mail.com";
            given(userGrpcClient.getUserByEmail(unknownEmail))
                    .willThrow(new ResourceNotFoundException(String.format(EMAIL_NOT_FOUND, unknownEmail)));

            assertThat(mockMvcTester.perform(get(BASE_URL + "/{id}", savedOrder.getId())
                            .with(adminJwt(adminId))
                            .param("userEmail", unknownEmail)))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class GetAllOrdersTest {

        static Stream<Arguments> provideInvalidSearchCriteria() {
            Instant future = Instant.now().plus(Duration.ofDays(10));
            Instant past = Instant.now().minus(Duration.ofDays(10));

            return Stream.of(
                    Arguments.of(
                            List.of("CREATED", "PAID", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELED"), null, null),
                    Arguments.of(null, future, null),
                    Arguments.of(null, null, future),
                    Arguments.of(null, past, past.minus(Duration.ofDays(5))));
        }

        @ParameterizedTest
        @MethodSource("provideInvalidSearchCriteria")
        void getAllOrdersShouldReturnBadRequestForInvalidValidation(List<String> statuses, Instant from, Instant to) {
            MockHttpServletRequestBuilder requestBuilder = get(BASE_URL).with(adminJwt(adminId));

            if (statuses != null) {
                requestBuilder.param("statuses", statuses.toArray(new String[0]));
            }
            if (from != null) {
                requestBuilder.param("createdAtFrom", from.toString());
            }
            if (to != null) {
                requestBuilder.param("createdAtTo", to.toString());
            }

            assertThat(mockMvcTester.perform(requestBuilder)).hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        void getAllOrdersShouldReturnPagedOrdersWithUserData() {
            assertThat(mockMvcTester.perform(get(BASE_URL)
                            .with(adminJwt(adminId))
                            .param("page", "0")
                            .param("size", "10")
                            .param("includeDeleted", "false")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPath("$.content")
                    .hasPathSatisfying(
                            "$.page.totalElements", total -> assertThat(total).isEqualTo(1))
                    .hasPathSatisfying("$.content[0].user.id", id -> assertThat(id)
                            .isEqualTo(mockUser.id().toString()))
                    .hasPathSatisfying("$.content[0].userServiceAvailable", av -> assertThat(av)
                            .isEqualTo(true));
        }

        @Test
        void getAllOrdersShouldFilterByStatuses() {
            assertThat(mockMvcTester.perform(get(BASE_URL)
                            .with(adminJwt(adminId))
                            .param("page", "0")
                            .param("size", "10")
                            .param("statuses", "CREATED", "PAID")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.page.totalElements", total -> assertThat(total).isEqualTo(1))
                    .hasPathSatisfying(
                            "$.content[0].status", status -> assertThat(status).isEqualTo("CREATED"));
        }

        @Test
        void getAllOrdersShouldFilterByValidDateRange() {
            Instant now = Instant.now();
            Instant dateFrom = now.minus(Duration.ofDays(2));

            assertThat(mockMvcTester.perform(get(BASE_URL)
                            .with(adminJwt(adminId))
                            .param("page", "0")
                            .param("size", "10")
                            .param("createdAtFrom", dateFrom.toString())
                            .param("createdAtTo", now.toString())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.page.totalElements", total -> assertThat(total).isEqualTo(1));
        }

        @Test
        void getAllOrdersShouldReturnOrdersWithNullUserWhenGrpcBatchFails() {
            given(userGrpcClient.getUsersByIds(anySet())).willReturn(emptyMap());

            assertThat(mockMvcTester.perform(get(BASE_URL)
                            .with(adminJwt(adminId))
                            .param("page", "0")
                            .param("size", "10")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.content[0].user", userField -> assertThat(userField)
                            .isNull())
                    .hasPathSatisfying("$.content[0].userServiceAvailable", av -> assertThat(av)
                            .isEqualTo(false));
        }

        @Test
        void getAllOrdersShouldReturnEmptyPageWhenNoOrdersExist() {
            orderRepository.deleteAll();

            assertThat(mockMvcTester.perform(get(BASE_URL)
                            .with(adminJwt(adminId))
                            .param("page", "0")
                            .param("size", "10")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.page.totalElements", total -> assertThat(total).isEqualTo(0))
                    .hasPathSatisfying(
                            "$.content",
                            content -> assertThat(content).asInstanceOf(LIST).isEmpty());
        }
    }

    @Nested
    class GetUserOrdersTest {

        @Test
        void getUserOrdersShouldReturnListOfOrdersWithUserDataWhenEverythingIsOk() {
            assertThat(mockMvcTester.perform(
                            get(BASE_URL + "/user/{userId}", mockUser.id()).with(adminJwt(adminId))))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$", orders -> assertThat(orders).asInstanceOf(LIST).hasSize(1))
                    .hasPathSatisfying(
                            "$[0].user.email", email -> assertThat(email).isEqualTo(mockUser.email()));
        }

        @Test
        void getUserOrdersShouldReturnOrdersWithNullUserWhenUserServiceIsUnavailable() {
            UUID fallbackUserId = UUID.randomUUID();
            given(userGrpcClient.getUserById(fallbackUserId)).willReturn(null);

            Order order = Instancio.of(Order.class)
                    .ignore(field(Order::getId))
                    .ignore(field(Order::getVersion))
                    .ignore(field(Order::getItems))
                    .set(field(Order::getUserId), fallbackUserId)
                    .set(field(Order::isDeleted), false)
                    .create();
            orderRepository.saveAndFlush(order);

            assertThat(mockMvcTester.perform(
                            get(BASE_URL + "/user/{userId}", fallbackUserId).with(adminJwt(adminId))))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$", orders -> assertThat(orders).asInstanceOf(LIST).hasSize(1))
                    .hasPathSatisfying(
                            "$[0].user", userField -> assertThat(userField).isNull());
        }

        @Test
        void getUserOrdersShouldReturnNotFoundWhenUserDoesNotExistInUserService() {
            UUID unknownUserId = UUID.randomUUID();
            given(userGrpcClient.getUserById(unknownUserId))
                    .willThrow(new ResourceNotFoundException(String.format(USER_NOT_FOUND, unknownUserId)));

            assertThat(mockMvcTester.perform(
                            get(BASE_URL + "/user/{userId}", unknownUserId).with(adminJwt(adminId))))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void getUserOrdersShouldReturnEmptyListWhenUserHasNoOrders() {
            orderRepository.deleteAll();

            assertThat(mockMvcTester.perform(
                            get(BASE_URL + "/user/{userId}", mockUser.id()).with(adminJwt(adminId))))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$", orders -> assertThat(orders).asInstanceOf(LIST).isEmpty());
        }
    }

    @Nested
    class UpdateOrderStatusTest {

        static Stream<OrderUpdateRequest> provideInvalidUpdateRequests() {
            return Stream.of(new OrderUpdateRequest(null));
        }

        static Stream<OrderUpdateRequest> provideValidUpdateRequests() {
            return Stream.of(
                    new OrderUpdateRequest(Status.PAID),
                    new OrderUpdateRequest(Status.DELIVERED),
                    new OrderUpdateRequest(Status.CANCELED));
        }

        @ParameterizedTest
        @MethodSource("provideInvalidUpdateRequests")
        void updateOrderStatusShouldReturnBadRequestForInvalidValidation(OrderUpdateRequest invalidDto) {
            assertThat(mockMvcTester.perform(put(BASE_URL + "/{id}/status", savedOrder.getId())
                            .with(adminJwt(adminId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(invalidDto))))
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @ParameterizedTest
        @MethodSource("provideValidUpdateRequests")
        void updateOrderStatusShouldReturnOkAndUpdatedFields(OrderUpdateRequest updateDto) {
            assertThat(mockMvcTester.perform(put(BASE_URL + "/{id}/status", savedOrder.getId())
                            .with(adminJwt(adminId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(updateDto))))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.status", status -> assertThat(status)
                            .isEqualTo(updateDto.status().name()));

            Order updatedOrder = orderRepository.findById(savedOrder.getId()).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(updateDto.status());
        }

        @Test
        void updateOrderStatusShouldReturnNotFoundWhenOrderDoesNotExist() {
            OrderUpdateRequest updateDto = new OrderUpdateRequest(Status.PROCESSING);

            assertThat(mockMvcTester.perform(put(BASE_URL + "/{id}/status", UUID.randomUUID())
                            .with(adminJwt(adminId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(updateDto))))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void updateOrderStatusShouldReturnUpdatedOrderWithNullUserWhenUserServiceIsUnavailable() {
            given(userGrpcClient.getUserById(mockUser.id())).willReturn(null);

            OrderUpdateRequest updateDto = new OrderUpdateRequest(Status.DELIVERED);

            assertThat(mockMvcTester.perform(put(BASE_URL + "/{id}/status", savedOrder.getId())
                            .with(adminJwt(adminId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(updateDto))))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.status", status -> assertThat(status).isEqualTo("DELIVERED"))
                    .hasPathSatisfying(
                            "$.user", userField -> assertThat(userField).isNull())
                    .hasPathSatisfying(
                            "$.userServiceAvailable", av -> assertThat(av).isEqualTo(false));
        }
    }

    @Nested
    class DeleteOrderTest {

        @Test
        void deleteShouldSoftDeleteOrderSuccessfully() {
            assertThat(mockMvcTester.perform(
                            delete(BASE_URL + "/{id}", savedOrder.getId()).with(adminJwt(adminId))))
                    .hasStatus(HttpStatus.NO_CONTENT);

            Order deletedOrder = orderRepository
                    .findOrderByIdIncludingDeleted(savedOrder.getId())
                    .orElseThrow();
            assertThat(deletedOrder.isDeleted()).isTrue();
        }

        @Test
        void deleteShouldReturnNotFoundWhenOrderDoesNotExist() {
            assertThat(mockMvcTester.perform(
                            delete(BASE_URL + "/{id}", UUID.randomUUID()).with(adminJwt(adminId))))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class SecurityAuthorizationTests {

        @ParameterizedTest
        @CsvFileSource(resources = "/testdata/admin/admin-security-routes.csv", numLinesToSkip = 1)
        void shouldDenyAccessWithoutToken(String method, String route) {
            assertThat(mockMvcTester.perform(request(HttpMethod.valueOf(method), route)))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @ParameterizedTest
        @CsvFileSource(resources = "/testdata/admin/admin-security-routes.csv", numLinesToSkip = 1)
        void shouldDenyAccessForRegularUser(String method, String route) {
            UUID regularUserId = UUID.randomUUID();
            String regularUserEmail = "user@domain.com";

            assertThat(mockMvcTester.perform(
                            request(HttpMethod.valueOf(method), route).with(userJwt(regularUserId, regularUserEmail))))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }
    }
}

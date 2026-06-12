package com.minispring.orderservice.controller;

import static com.minispring.orderservice.exception.ExceptionAnswer.USER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.minispring.orderservice.BaseIntegrationTest;
import com.minispring.orderservice.client.UserGrpcClient;
import com.minispring.orderservice.dto.request.OrderCreateRequest;
import com.minispring.orderservice.dto.request.OrderItemCreateRequest;
import com.minispring.orderservice.dto.response.UserProfileView;
import com.minispring.orderservice.exception.ResourceNotFoundException;
import com.minispring.orderservice.model.Item;
import com.minispring.orderservice.model.Order;
import com.minispring.orderservice.model.Status;
import com.minispring.orderservice.repository.ItemRepository;
import com.minispring.orderservice.repository.OrderRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.instancio.Instancio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

@AutoConfigureMockMvc
public class UserOrderControllerIT extends BaseIntegrationTest {

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

    private static final String BASE_URL = "/api/v1/orders";

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
        void createShouldReturnCreatedOrderForAuthorizedUser() {
            OrderItemCreateRequest itemDto = Instancio.of(OrderItemCreateRequest.class)
                    .set(field(OrderItemCreateRequest::itemId), savedItem.getId())
                    .set(field(OrderItemCreateRequest::quantity), 2)
                    .create();

            OrderCreateRequest createDto = Instancio.of(OrderCreateRequest.class)
                    .set(field(OrderCreateRequest::items), List.of(itemDto))
                    .create();

            assertThat(mockMvcTester.perform(post(BASE_URL)
                            .with(userJwt(mockUser.id(), mockUser.email()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(createDto))))
                    .hasStatus(HttpStatus.CREATED)
                    .bodyJson()
                    .hasPath("$.id")
                    .hasPathSatisfying("$.user.id", id -> assertThat(id)
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
                            .with(userJwt(mockUser.id(), mockUser.email()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(createDto))))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void createShouldReturnServiceUnavailableWhenUserServiceFallbackTriggers() {
            given(userGrpcClient.getUserByEmail(mockUser.email())).willReturn(null);

            OrderItemCreateRequest itemDto = Instancio.of(OrderItemCreateRequest.class)
                    .set(field(OrderItemCreateRequest::itemId), 1L)
                    .set(field(OrderItemCreateRequest::quantity), 1)
                    .create();

            OrderCreateRequest createDto = Instancio.of(OrderCreateRequest.class)
                    .set(field(OrderCreateRequest::items), List.of(itemDto))
                    .create();

            assertThat(mockMvcTester.perform(post(BASE_URL)
                            .with(userJwt(mockUser.id(), mockUser.email()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(createDto))))
                    .hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @Nested
    class GetOrderByIdTest {

        @Test
        void getOrderByIdShouldReturnSingleOrderWhenUserOwnsIt() {
            assertThat(mockMvcTester.perform(
                            get(BASE_URL + "/{id}", savedOrder.getId()).with(userJwt(mockUser.id(), mockUser.email()))))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.id", id -> assertThat(id)
                            .isEqualTo(savedOrder.getId().toString()))
                    .hasPathSatisfying("$.user.id", id -> assertThat(id)
                            .isEqualTo(mockUser.id().toString()));
        }

        @Test
        void getOrderByIdShouldReturnNotFoundWhenUserAttemptsToFetchSomeoneElseOrder() {
            assertThat(mockMvcTester.perform(get(BASE_URL + "/{id}", savedOrder.getId())
                            .with(userJwt(UUID.randomUUID(), "stranger@mail.com"))))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void getOrderByIdShouldReturnOrderWithNullUserWhenGrpcServiceIsDown() {
            given(userGrpcClient.getUserById(mockUser.id())).willReturn(null);

            assertThat(mockMvcTester.perform(
                            get(BASE_URL + "/{id}", savedOrder.getId()).with(userJwt(mockUser.id(), mockUser.email()))))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.user", userField -> assertThat(userField).isNull())
                    .hasPathSatisfying(
                            "$.userServiceAvailable", av -> assertThat(av).isEqualTo(false));
        }
    }

    @Nested
    class GetUserOrdersTest {

        @Test
        void getUserOrdersShouldReturnListOfOrdersWithUserData() {
            assertThat(mockMvcTester.perform(get(BASE_URL).with(userJwt(mockUser.id(), mockUser.email()))))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$", orders -> assertThat(orders).asInstanceOf(LIST).hasSize(1))
                    .hasPathSatisfying(
                            "$[0].user.email", email -> assertThat(email).isEqualTo(mockUser.email()));
        }

        @Test
        void getUserOrdersShouldReturnOrdersWithNullUserWhenGrpcFails() {
            given(userGrpcClient.getUserById(mockUser.id())).willReturn(null);

            assertThat(mockMvcTester.perform(get(BASE_URL).with(userJwt(mockUser.id(), mockUser.email()))))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$[0].user", userField -> assertThat(userField).isNull());
        }

        @Test
        void getUserOrdersShouldReturnNotFoundWhenUserDoesNotExistInSystem() {
            UUID unknownUserId = UUID.randomUUID();
            given(userGrpcClient.getUserById(unknownUserId))
                    .willThrow(new ResourceNotFoundException(String.format(USER_NOT_FOUND, unknownUserId)));

            assertThat(mockMvcTester.perform(get(BASE_URL).with(userJwt(unknownUserId, "ghost@mail.com"))))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void getUserOrdersShouldReturnEmptyListWhenUserHasNoActiveOrders() {
            orderRepository.deleteAll();
            assertThat(mockMvcTester.perform(get(BASE_URL).with(userJwt(mockUser.id(), mockUser.email()))))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$", orders -> assertThat(orders).asInstanceOf(LIST).isEmpty());
        }
    }

    @Nested
    class DeleteOrderTest {

        @Test
        void deleteShouldSoftDeleteOwnOrderAndLeaveOtherDataIntact() {
            Order order = Instancio.of(Order.class)
                    .ignore(field(Order::getId))
                    .ignore(field(Order::getVersion))
                    .ignore(field(Order::getItems))
                    .set(field(Order::getUserId), UUID.randomUUID())
                    .set(field(Order::isDeleted), false)
                    .create();
            orderRepository.saveAndFlush(order);

            long itemsCount = itemRepository.count();

            assertThat(mockMvcTester.perform(delete(BASE_URL + "/{id}", savedOrder.getId())
                            .with(userJwt(mockUser.id(), mockUser.email()))))
                    .hasStatus(HttpStatus.NO_CONTENT);

            Order deletedOrder = orderRepository
                    .findOrderByIdIncludingDeleted(savedOrder.getId())
                    .orElseThrow();
            assertThat(deletedOrder.isDeleted()).isTrue();

            Order untouchedOrder = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(untouchedOrder.isDeleted()).isFalse();

            assertThat(itemRepository.count()).isEqualTo(itemsCount);
        }

        @Test
        void deleteShouldReturnNotFoundWhenUserTriesToDeleteStrangersOrder() {
            assertThat(mockMvcTester.perform(delete(BASE_URL + "/{id}", savedOrder.getId())
                            .with(userJwt(UUID.randomUUID(), "stranger@mail.com"))))
                    .hasStatus(HttpStatus.NOT_FOUND);

            Order notDeletedOrder = orderRepository.findById(savedOrder.getId()).orElseThrow();
            assertThat(notDeletedOrder.isDeleted()).isFalse();
        }
    }

    @Nested
    class SecurityAuthorizationTests {

        @ParameterizedTest
        @CsvFileSource(resources = "/testdata/user/user-security-routes.csv", numLinesToSkip = 1)
        void shouldDenyAccessWithoutToken(String method, String route) {
            assertThat(mockMvcTester.perform(request(HttpMethod.valueOf(method), route)))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }
}

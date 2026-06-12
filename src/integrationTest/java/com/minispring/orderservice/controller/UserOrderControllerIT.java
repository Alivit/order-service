package com.minispring.orderservice.controller;

import com.minispring.orderservice.BaseIntegrationTest;
import com.minispring.orderservice.client.UserGrpClient;
import com.minispring.orderservice.dto.OrderCreateDto;
import com.minispring.orderservice.dto.OrderItemCreateDto;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.minispring.orderservice.exception.ExceptionAnswer.USER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
    private UserGrpClient userGrpcService;

    private static final String BASE_URL = "/api/v1/orders";

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
            OrderItemCreateDto itemDto = Instancio.of(OrderItemCreateDto.class)
                    .set(field(OrderItemCreateDto::itemId), savedItem.getId())
                    .set(field(OrderItemCreateDto::quantity), 2)
                    .create();

            OrderCreateDto createDto = Instancio.of(OrderCreateDto.class)
                    .set(field(OrderCreateDto::email), mockUser.email())
                    .set(field(OrderCreateDto::items), List.of(itemDto))
                    .create();

            assertThat(mockMvcTester.perform(post(BASE_URL)
                    .header("TokenEmail", mockUser.email())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(createDto))))
                    .hasStatus(HttpStatus.CREATED)
                    .bodyJson()
                    .hasPath("$.id")
                    .hasPathSatisfying("$.user.id", id -> assertThat(id).isEqualTo(mockUser.id().toString()));
        }

        @Test
        @Tag("init")
        void createShouldReturnNotFoundWhenItemDoesNotExist() {
            OrderItemCreateDto missingItemDto = Instancio.of(OrderItemCreateDto.class)
                    .set(field(OrderItemCreateDto::itemId), 999L)
                    .create();

            OrderCreateDto createDto = Instancio.of(OrderCreateDto.class)
                    .set(field(OrderCreateDto::email), mockUser.email())
                    .set(field(OrderCreateDto::items), List.of(missingItemDto))
                    .create();

            assertThat(mockMvcTester.perform(post(BASE_URL)
                    .header("TokenEmail", mockUser.email())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(createDto))))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        @Tag("init")
        void createShouldReturnServiceUnavailableWhenUserServiceFallbackTriggers() {
            given(userGrpcService.getUserByEmail(mockUser.email())).willReturn(null);

            OrderItemCreateDto itemDto = Instancio.of(OrderItemCreateDto.class)
                    .set(field(OrderItemCreateDto::itemId), 1L)
                    .set(field(OrderItemCreateDto::quantity), 1)
                    .create();

            OrderCreateDto createDto = Instancio.of(OrderCreateDto.class)
                    .set(field(OrderCreateDto::email), mockUser.email())
                    .set(field(OrderCreateDto::items), List.of(itemDto))
                    .create();

            assertThat(mockMvcTester.perform(post(BASE_URL)
                    .header("TokenEmail", mockUser.email())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(createDto))))
                    .hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @Nested
    class GetOrderByIdTest {

        @Test
        void getOrderByIdShouldReturnSingleOrderWhenUserOwnsIt() {
            assertThat(mockMvcTester.perform(get(BASE_URL + "/{id}", savedOrder.getId())
                    .header("TokenId", mockUser.id().toString())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.id", id -> assertThat(id).isEqualTo(savedOrder.getId().toString()))
                    .hasPathSatisfying("$.user.id", id -> assertThat(id).isEqualTo(mockUser.id().toString()));
        }

        @Test
        void getOrderByIdShouldReturnNotFoundWhenUserAttemptsToFetchSomeoneElseOrder() {
            assertThat(mockMvcTester.perform(get(BASE_URL + "/{id}", savedOrder.getId())
                    .header("TokenId", UUID.randomUUID().toString())))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void getOrderByIdShouldReturnOrderWithNullUserWhenGrpcServiceIsDown() {
            given(userGrpcService.getUserById(mockUser.id())).willReturn(null);

            assertThat(mockMvcTester.perform(get(BASE_URL + "/{id}", savedOrder.getId())
                    .header("TokenId", mockUser.id().toString())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.user", userField -> assertThat(userField).isNull())
                    .hasPathSatisfying("$.userServiceAvailable", av -> assertThat(av).isEqualTo(false));
        }
    }

    @Nested
    class GetUserOrdersTest {

        @Test
        void getUserOrdersShouldReturnListOfOrdersWithUserData() {
            assertThat(mockMvcTester.perform(get(BASE_URL)
                    .header("TokenId", mockUser.id().toString())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$", orders -> assertThat(orders).asInstanceOf(LIST).hasSize(1))
                    .hasPathSatisfying("$[0].user.email", email -> assertThat(email).isEqualTo(mockUser.email()));
        }

        @Test
        void getUserOrdersShouldReturnOrdersWithNullUserWhenGrpcFails() {
            given(userGrpcService.getUserById(mockUser.id())).willReturn(null);

            assertThat(mockMvcTester.perform(get(BASE_URL)
                    .header("TokenId", mockUser.id().toString())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$[0].user", userField -> assertThat(userField).isNull());
        }

        @Test
        void getUserOrdersShouldReturnNotFoundWhenUserDoesNotExistInSystem() {
            UUID unknownUserId = UUID.randomUUID();
            given(userGrpcService.getUserById(unknownUserId))
                    .willThrow(new ResourceNotFoundException(String.format(USER_NOT_FOUND, unknownUserId)));

            assertThat(mockMvcTester.perform(get(BASE_URL)
                    .header("TokenId", unknownUserId.toString())))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        @Tag("init")
        void getUserOrdersShouldReturnEmptyListWhenUserHasNoActiveOrders() {
            assertThat(mockMvcTester.perform(get(BASE_URL)
                    .header("TokenId", mockUser.id().toString())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$", orders -> assertThat(orders).asInstanceOf(LIST).isEmpty());
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
                    .header("TokenId", mockUser.id().toString())))
                    .hasStatus(HttpStatus.NO_CONTENT);

            Order deletedOrder = orderRepository.findOrderByIdIncludingDeleted(savedOrder.getId()).orElseThrow();
            assertThat(deletedOrder.isDeleted()).isTrue();

            Order untouchedOrder = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(untouchedOrder.isDeleted()).isFalse();

            assertThat(itemRepository.count()).isEqualTo(itemsCount);
        }

        @Test
        void deleteShouldReturnNotFoundWhenUserTriesToDeleteStrangersOrder() {
            assertThat(mockMvcTester.perform(delete(BASE_URL + "/{id}", savedOrder.getId())
                    .header("TokenId", UUID.randomUUID().toString())))
                    .hasStatus(HttpStatus.NOT_FOUND);

            Order notDeletedOrder = orderRepository.findById(savedOrder.getId()).orElseThrow();
            assertThat(notDeletedOrder.isDeleted()).isFalse();
        }
    }
}
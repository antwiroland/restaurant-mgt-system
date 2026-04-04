package com.restaurantmanager.core.phase10;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.order.OrderService;
import com.restaurantmanager.core.order.OrderType;
import com.restaurantmanager.core.order.dto.OrderResponse;
import com.restaurantmanager.core.order.dto.OrderCreateItemRequest;
import com.restaurantmanager.core.order.dto.OrderCreateRequest;
import com.restaurantmanager.core.payment.PaymentMethod;
import com.restaurantmanager.core.payment.PaymentService;
import com.restaurantmanager.core.payment.dto.PaymentInitiateRequest;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentWorkflowIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentService paymentService;

    @Test
    void concurrentOrderCreation_createsDistinctOrdersAndPickupCodes() throws Exception {
        MenuItemEntity menuItem = createMenuItem("Jollof", "25.00");
        UserEntity customer = createUser("Ama", "+233240001000", "ama@example.com", "secret123", Role.CUSTOMER);
        UserPrincipal principal = new UserPrincipal(customer.getId(), Role.CUSTOMER, null);

        List<OrderResponse> results = runConcurrently(8, index -> orderService.createOrder(
                new OrderCreateRequest(
                        OrderType.PICKUP,
                        null,
                        null,
                        List.of(new OrderCreateItemRequest(menuItem.getId(), index + 1, null, List.of())),
                        null,
                        null,
                        null,
                        null,
                        "batch-" + index
                ),
                principal
        ));

        assertEquals(8, results.size());
        assertEquals(8, orderRepository.count());

        Set<String> pickupCodes = results.stream()
                .map(OrderResponse::pickupCode)
                .collect(Collectors.toSet());
        assertEquals(8, pickupCodes.size());
        assertTrue(pickupCodes.stream().allMatch(code -> code != null && code.startsWith("PK")));
    }

    @Test
    void concurrentPaymentInitiation_persistsOnePaymentPerOrder() throws Exception {
        MenuItemEntity menuItem = createMenuItem("Waakye", "30.00");
        UserEntity customer = createUser("Kojo", "+233240001001", "kojo@example.com", "secret123", Role.CUSTOMER);
        UserPrincipal principal = new UserPrincipal(customer.getId(), Role.CUSTOMER, null);

        List<OrderResponse> orders = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            orders.add(orderService.createOrder(
                    new OrderCreateRequest(
                            OrderType.PICKUP,
                            null,
                            null,
                            List.of(new OrderCreateItemRequest(menuItem.getId(), 1, null, List.of())),
                            null,
                            null,
                            null,
                            null,
                            "payment-batch-" + i
                    ),
                    principal
            ));
        }

        runConcurrently(orders.size(), index -> paymentService.initiate(
                new PaymentInitiateRequest(
                        orders.get(index).id(),
                        PaymentMethod.MOBILE_MONEY,
                        "+23324000999" + index,
                        "idem-" + index
                ),
                principal
        ));

        assertEquals(6, paymentRepository.count());
        assertEquals(6, paymentRepository.findAll().stream()
                .map(payment -> payment.getIdempotencyKey())
                .collect(Collectors.toSet())
                .size());
    }

    private MenuItemEntity createMenuItem(String name, String price) {
        CategoryEntity category = new CategoryEntity();
        category.setName("Mains-" + UUID.randomUUID());
        category.setDescription("Concurrent test category");
        category.setDisplayOrder(1);
        category.setActive(true);
        CategoryEntity savedCategory = categoryRepository.save(category);

        MenuItemEntity item = new MenuItemEntity();
        item.setCategory(savedCategory);
        item.setName(name);
        item.setDescription("Concurrent test item");
        item.setPrice(new BigDecimal(price));
        item.setAvailable(true);
        item.setActive(true);
        return menuItemRepository.save(item);
    }

    private <T> List<T> runConcurrently(int workers, IndexedTask<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                final int index = i;
                futures.add(executor.submit(new Callable<>() {
                    @Override
                    public T call() throws Exception {
                        ready.countDown();
                        if (!start.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting to start concurrent task");
                        }
                        return task.run(index);
                    }
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface IndexedTask<T> {
        T run(int index) throws Exception;
    }
}

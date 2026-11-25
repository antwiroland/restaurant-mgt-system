package com.restaurantmanager.core.dining;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.table.RestaurantTableEntity;
import com.restaurantmanager.core.table.TableStatus;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Course dining end to end: course configuration, ordering by course from the table QR page and the POS, the guest's
 * "next course" request reaching waiters and cashiers alike, one release only when two staff press at once, the
 * kitchen (KDS) seeing only released dishes split by station, serving, beverages at any time, the single table bill,
 * and the existing waiter / bill buttons and cash payment still working.
 */
class CourseDiningIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private BranchRepository branchRepository;
    @SpyBean
    private SimpMessagingTemplate messagingTemplate;

    private BranchEntity osu;
    private BranchEntity legon;
    private RestaurantTableEntity table;
    private UserEntity admin;
    private String adminAuth;
    private String managerAuth;
    private String waiterAuth;
    private String cashierAuth;
    private String otherBranchCashierAuth;
    private MenuItemEntity soup;
    private MenuItemEntity springRolls;
    private MenuItemEntity jollof;
    private MenuItemEntity juice;
    private UUID starter;
    private UUID main;
    private UUID dessert;
    private MenuItemEntity cake;

    @BeforeEach
    void setUpData() throws Exception {
        clearInvocations(messagingTemplate);
        doCallRealMethod().when(messagingTemplate).convertAndSend(anyString(), any(Object.class));
        osu = branch("Osu");
        legon = branch("Legon");
        admin = createUser("Ama Admin", "+233300009001", null, "secret123", Role.ADMIN);
        adminAuth = "Bearer " + accessToken(admin);
        managerAuth = staffAuth("Mensah Manager", "+233300009002", Role.MANAGER, osu);
        waiterAuth = staffAuth("Kofi Waiter", "+233300009003", Role.CASHIER, osu);
        cashierAuth = staffAuth("Esi Cashier", "+233300009004", Role.CASHIER, osu);
        otherBranchCashierAuth = staffAuth("Yaw Legon", "+233300009005", Role.CASHIER, legon);

        CategoryEntity starters = category("Starters", false, null);
        CategoryEntity mains = category("Mains", false, "HOT_KITCHEN");
        CategoryEntity desserts = category("Desserts", false, "PASTRY");
        CategoryEntity drinks = category("Drinks", true, "BAR");
        soup = item(starters, "Chicken soup", "20.00");
        springRolls = item(starters, "Spring rolls", "15.00");
        jollof = item(mains, "Jollof rice", "40.00");
        cake = item(desserts, "Chocolate cake", "25.00");
        juice = item(drinks, "Orange juice", "10.00");

        table = new RestaurantTableEntity();
        table.setNumber("05");
        table.setCapacity(4);
        table.setStatus(TableStatus.AVAILABLE);
        table.setQrToken("t05-" + UUID.randomUUID());
        table.setBranch(osu);
        table = restaurantTableRepository.save(table);

        starter = createCourse(adminAuth, "Starter", 10);
        main = createCourse(adminAuth, "Main Course", 30);
        dessert = createCourse(adminAuth, "Dessert", 50);
    }

    // ---- 1. Course configuration ------------------------------------------------------------------------------------

    @Test
    void coursesCanBeCreatedRenamedReorderedAndDeactivated_andMealsKeepTheNameTheyWereOrderedUnder() throws Exception {
        UUID soupCourse = createCourse(managerAuth, "Soup", 20); // a branch manager adds one for their branch
        JsonNode listed = json(perform(get("/dining/courses"), waiterAuth).andExpect(status().isOk()));
        assertThat(names(listed)).containsExactly("Starter", "Soup", "Main Course", "Dessert");

        guestOrder(List.of(line(soup, 1, starter)));
        perform(put("/dining/courses/" + starter).content("{\"name\":\"Small plates\"}"), adminAuth).andExpect(status().isOk());
        perform(put("/dining/courses/order").content("{\"courseIds\":[\"" + main + "\",\"" + starter + "\",\"" + dessert + "\"]}"), adminAuth)
                .andExpect(status().isOk());
        perform(patch("/dining/courses/" + dessert + "/status").content("{\"active\":false}"), adminAuth)
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

        JsonNode afterwards = json(perform(get("/dining/courses?includeInactive=false"), adminAuth));
        assertThat(names(afterwards)).containsExactly("Main Course", "Small plates", "Soup").doesNotContain("Dessert");
        // The meal ordered earlier still says "Starter".
        JsonNode meal = publicView();
        assertThat(meal.get("mealCourses").get(0).get("courseName").asText()).isEqualTo("Starter");

        // Only admins change restaurant-wide courses; cashiers cannot configure courses; duplicates are refused.
        perform(put("/dining/courses/" + main).content("{\"name\":\"Mains\"}"), managerAuth).andExpect(status().isForbidden());
        perform(post("/dining/courses").content("{\"name\":\"Cheese\"}"), cashierAuth).andExpect(status().isForbidden());
        perform(post("/dining/courses").content("{\"name\":\"soup\"}"), managerAuth).andExpect(status().isConflict());
        assertThat(soupCourse).isNotNull();
    }

    // ---- 2, 4, 13, 14, 17. Guest ordering, held courses, beverages, KDS and the bill -------------------------------

    @Test
    void guestOrdersSeveralCourses_onlyDrinksReachTheKitchen_andEverythingIsOnOneBill() throws Exception {
        JsonNode view = guestOrder(List.of(
                line(soup, 2, starter), line(springRolls, 1, starter),
                line(jollof, 2, main), line(cake, 2, dessert),
                line(juice, 2, main))); // a drink tagged with a course still goes straight to the bar

        assertThat(view.get("mealCourses")).hasSize(3);
        assertThat(view.get("mealCourses").get(0).get("courseName").asText()).isEqualTo("Starter");
        assertThat(view.get("mealCourses").get(0).get("items")).hasSize(2);
        assertThat(view.get("mealCourses").get(0).get("status").asText()).isEqualTo("PENDING");
        assertThat(view.get("canRequestNextCourse").asBoolean()).isTrue();
        assertThat(view.get("nextCourseName").asText()).isEqualTo("Starter");

        // One meal; four orders (starter, main, dessert held; drinks sent), all for the same table and meal.
        List<OrderEntity> orders = orderRepository.findByTableIdOrderByCreatedAtAsc(table.getId());
        assertThat(orders).hasSize(4);
        assertThat(orders.stream().map(OrderEntity::getDiningOrderId).distinct()).hasSize(1);
        assertThat(orders.stream().filter(o -> o.getStatus() == OrderStatus.HELD)).hasSize(3);
        OrderEntity drinks = orders.stream().filter(o -> "BAR".equals(o.getKitchenStation())).findFirst().orElseThrow();
        assertThat(drinks.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(drinks.getDiningOrderCourseId()).isNull();

        // The kitchen sees only the drinks.
        JsonNode board = kdsBoard(null);
        assertThat(board.get("columns").get("PENDING")).hasSize(1);
        assertThat(board.get("columns").get("PENDING").get(0).get("kitchenStation").asText()).isEqualTo("BAR");

        // The whole meal is on the table's bill straight away (held courses included): 40 + 15 + 80 + 50 + 20.
        JsonNode bill = json(perform(get("/orders/dine-in/tables/" + table.getId() + "/bill"), cashierAuth));
        assertThat(bill.get("totalOrdered").decimalValue()).isEqualByComparingTo("205.00");
        assertThat(bill.get("activeOrderCount").asInt()).isEqualTo(4);
    }

    // ---- 3. Waiter ordering through the POS --------------------------------------------------------------------------

    @Test
    void waiterOrdersByCourseFromThePos_andAddsToTheSameMeal() throws Exception {
        JsonNode meal = json(perform(post("/dining/tables/" + table.getId() + "/items")
                .content(body(Map.of("items", List.of(line(soup, 1, starter), line(jollof, 1, main))))), waiterAuth)
                .andExpect(status().isOk()));
        assertThat(meal.get("tableNumber").asText()).isEqualTo("05");
        assertThat(meal.get("courses")).hasSize(2);

        // The guest then adds dessert from their phone: same meal.
        guestOrder(List.of(line(cake, 1, dessert)));
        JsonNode active = json(perform(get("/dining/tables/" + table.getId() + "/active-order"), waiterAuth).andExpect(status().isOk()));
        assertThat(active.get("id").asText()).isEqualTo(meal.get("id").asText());
        assertThat(active.get("courses")).hasSize(3);

        // Adding through the meal itself works too, and other branches' staff cannot.
        perform(post("/dining/orders/" + meal.get("id").asText() + "/items")
                .content(body(Map.of("items", List.of(line(juice, 1, null))))), cashierAuth).andExpect(status().isOk());
        perform(post("/dining/tables/" + table.getId() + "/items")
                .content(body(Map.of("items", List.of(line(juice, 1, null))))), otherBranchCashierAuth).andExpect(status().isForbidden());
    }

    // ---- 5, 6, 7, 9, 11. Request next course: one request for waiter and cashier ---------------------------------

    @Test
    void nextCourseRequest_reachesWaiterAndCashierAsOneRequest_andCashierCanReleaseIt() throws Exception {
        guestOrder(List.of(line(soup, 2, starter), line(jollof, 2, main)));
        clearInvocations(messagingTemplate);

        JsonNode response = requestNext().andExpect(status().isOk()).andReturn().getResponse().getContentAsString().transform(this::readJson);
        assertThat(response.get("outcome").asText()).isEqualTo("REQUESTED");
        assertThat(response.get("courseName").asText()).isEqualTo("Starter");
        assertThat(response.get("message").asText()).isEqualTo("Your request has been sent to the waiter and cashier.");
        // Pushed to the branch's staff channel (waiters and cashiers of Osu) and to every-branch staff.
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/dining.courses." + osu.getId()),
                argThat((Object payload) -> payload instanceof Map<?, ?> map && "COURSE_REQUESTED".equals(map.get("event"))
                        && "05".equals(map.get("tableNumber"))));
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/dining.courses"), any(Object.class));

        // Tapping again does not create another request.
        JsonNode again = readJson(requestNext().andReturn().getResponse().getContentAsString());
        assertThat(again.get("outcome").asText()).isEqualTo("ALREADY_PENDING");
        assertThat(again.get("requestId").asText()).isEqualTo(response.get("requestId").asText());
        assertThat(publicView().get("canRequestNextCourse").asBoolean()).isFalse();

        // The waiter and the cashier see the very same request; another branch does not.
        JsonNode waiterSees = json(perform(get("/dining/course-release-requests/pending"), waiterAuth));
        JsonNode cashierSees = json(perform(get("/dining/course-release-requests/pending"), cashierAuth));
        assertThat(waiterSees).hasSize(1);
        assertThat(cashierSees).hasSize(1);
        assertThat(waiterSees.get(0).get("id").asText()).isEqualTo(cashierSees.get(0).get("id").asText());
        assertThat(waiterSees.get(0).get("courseName").asText()).isEqualTo("Starter");
        assertThat(waiterSees.get(0).get("tableNumber").asText()).isEqualTo("05");
        assertThat(waiterSees.get(0).get("items").get(0).get("name").asText()).isEqualTo("Chicken soup");
        assertThat(json(perform(get("/dining/course-release-requests/pending"), otherBranchCashierAuth))).isEmpty();

        // The cashier releases it; the waiter then sees it handled (by whom) and cannot release it again.
        String requestId = response.get("requestId").asText();
        perform(post("/dining/course-release-requests/" + requestId + "/release"), cashierAuth).andExpect(status().isOk())
                .andExpect(jsonPath("$.releasedOrders").value(1))
                .andExpect(jsonPath("$.course.releasedByName").value("Esi Cashier"));
        perform(post("/dining/course-release-requests/" + requestId + "/release"), waiterAuth)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Starter has already been released to the kitchen by Esi Cashier"));
        JsonNode all = json(perform(get("/dining/course-release-requests"), waiterAuth));
        assertThat(all.get(0).get("status").asText()).isEqualTo("APPROVED");
        assertThat(all.get(0).get("handledByName").asText()).isEqualTo("Esi Cashier");
        assertThat(all.get(0).get("handledByRole").asText()).isEqualTo("CASHIER");
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/dining.courses." + osu.getId()),
                argThat((Object payload) -> payload instanceof Map<?, ?> map && "COURSE_RELEASED".equals(map.get("event"))));

        // Next tap asks for the main course.
        assertThat(readJson(requestNext().andReturn().getResponse().getContentAsString()).get("courseName").asText()).isEqualTo("Main Course");
    }

    @Test
    void nextCourseRequest_withNothingOrdered_tellsTheGuestToOrderFirst() throws Exception {
        JsonNode none = readJson(requestNext().andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(none.get("outcome").asText()).isEqualTo("NO_PENDING_COURSE");
        assertThat(none.get("message").asText()).isEqualTo("You have no pending course. Please order your next course first.");

        guestOrder(List.of(line(juice, 1, null))); // drinks only: nothing to request
        assertThat(readJson(requestNext().andReturn().getResponse().getContentAsString()).get("outcome").asText()).isEqualTo("NO_PENDING_COURSE");
        assertThat(courseReleaseRequestRepository.findAll()).isEmpty();
    }

    // ---- 8, 10. Release by the waiter; waiter and cashier at the same moment ----------------------------------------

    @Test
    void waiterReleasesACourse_andItReachesTheKitchenOnce_evenIfCashierPressesAtTheSameMoment() throws Exception {
        guestOrder(List.of(line(soup, 2, starter), line(springRolls, 1, starter), line(jollof, 1, main)));
        UUID starterCourse = mealCourseId("Starter");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (String auth : List.of(waiterAuth, cashierAuth)) {
            Callable<Integer> release = () -> {
                go.await();
                return perform(post("/dining/order-courses/" + starterCourse + "/release"), auth).andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(release));
        }
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get());
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        // One kitchen ticket for the starter, its items once.
        JsonNode pending = kdsBoard(null).get("columns").get("PENDING");
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).get("courseName").asText()).isEqualTo("Starter");
        assertThat(pending.get(0).get("items")).hasSize(2);
        assertThat(pending.get(0).get("releasedByName").asText()).isIn("Kofi Waiter", "Esi Cashier");
        assertThat(courseStatusHistoryRepository.findByDiningOrderCourseIdOrderByCreatedAtAsc(starterCourse))
                .extracting(CourseStatusHistoryEntity::getToStatus).containsExactly(CourseStatus.PENDING, CourseStatus.RELEASED);
        assertThat(auditLogRepository.findAll()).anyMatch(log -> log.getAction().name().equals("COURSE_RELEASED"));
    }

    // ---- 12, 14, 15, 16. Adding after release, station split, kitchen progress and serving ------------------------

    @Test
    void kitchenTicketsAreSplitByStation_progressFollowsTheKitchen_andWaiterServes() throws Exception {
        UUID drinkCategory = juice.getCategory().getId();
        assertThat(drinkCategory).isNotNull();
        // A main with a dish from the hot kitchen and one from the pastry station.
        guestOrder(List.of(line(jollof, 2, main), line(cake, 1, main)));
        UUID mainCourse = mealCourseId("Main Course");
        perform(post("/dining/order-courses/" + mainCourse + "/release"), waiterAuth).andExpect(status().isOk())
                .andExpect(jsonPath("$.releasedOrders").value(2));

        JsonNode tickets = kdsBoard(null).get("columns").get("PENDING");
        assertThat(tickets).hasSize(2);
        assertThat(List.of(tickets.get(0).get("kitchenStation").asText(), tickets.get(1).get("kitchenStation").asText()))
                .containsExactlyInAnyOrder("HOT_KITCHEN", "PASTRY");
        assertThat(kdsBoard("PASTRY").get("columns").get("PENDING")).hasSize(1);

        // The guest adds another jollof to the released main: a new ticket, the first ones are not sent again.
        guestOrder(List.of(line(jollof, 1, main)));
        assertThat(kdsBoard("HOT_KITCHEN").get("columns").get("PENDING")).hasSize(2);
        assertThat(orderRepository.findByDiningOrderCourseIdOrderByCreatedAtAsc(mainCourse)).hasSize(3)
                .allMatch(o -> o.getStatus() == OrderStatus.PENDING);

        // The kitchen accepts, cooks and finishes every ticket: the course follows.
        List<OrderEntity> courseOrders = orderRepository.findByDiningOrderCourseIdOrderByCreatedAtAsc(mainCourse);
        for (OrderEntity order : courseOrders) {
            setStatus(order.getId(), "CONFIRMED");
            setStatus(order.getId(), "PREPARING");
        }
        assertThat(courseStatus(mainCourse)).isEqualTo(CourseStatus.PREPARING);
        perform(post("/dining/order-courses/" + mainCourse + "/serve"), waiterAuth)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Nothing in this course is ready to serve yet"));
        clearInvocations(messagingTemplate);
        for (OrderEntity order : courseOrders) {
            setStatus(order.getId(), "READY");
        }
        assertThat(courseStatus(mainCourse)).isEqualTo(CourseStatus.READY);
        // The waiters are told the course is ready to take to the table.
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/dining.courses." + osu.getId()),
                argThat((Object payload) -> payload instanceof Map<?, ?> map && "COURSE_READY".equals(map.get("event"))));

        perform(post("/dining/order-courses/" + mainCourse + "/serve"), waiterAuth).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SERVED"));
        assertThat(orderRepository.findByDiningOrderCourseIdOrderByCreatedAtAsc(mainCourse))
                .allMatch(o -> o.getStatus() == OrderStatus.COMPLETED);
    }

    @Test
    void aCourseIsOnlyServedWhenEveryTicketIs() throws Exception {
        guestOrder(List.of(line(jollof, 1, main), line(cake, 1, main)));
        UUID mainCourse = mealCourseId("Main Course");
        perform(post("/dining/order-courses/" + mainCourse + "/release"), waiterAuth).andExpect(status().isOk());
        List<OrderEntity> tickets = orderRepository.findByDiningOrderCourseIdOrderByCreatedAtAsc(mainCourse);
        setStatus(tickets.get(0).getId(), "CONFIRMED");
        setStatus(tickets.get(0).getId(), "PREPARING");
        setStatus(tickets.get(0).getId(), "READY");
        perform(post("/dining/order-courses/" + mainCourse + "/serve"), waiterAuth).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_SERVED"));
    }

    // ---- Hold, cancel, and guests can never release ----------------------------------------------------------------

    @Test
    void staffCanHoldAndCancelAWaitingCourse_butGuestsCannotRelease() throws Exception {
        guestOrder(List.of(line(soup, 1, starter), line(cake, 1, dessert)));
        UUID starterCourse = mealCourseId("Starter");
        UUID dessertCourse = mealCourseId("Dessert");

        perform(post("/dining/order-courses/" + starterCourse + "/hold"), waiterAuth).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_HOLD"));
        JsonNode next = readJson(requestNext().andReturn().getResponse().getContentAsString());
        assertThat(next.get("courseName").asText()).isEqualTo("Dessert"); // the held starter is skipped
        perform(post("/dining/order-courses/" + starterCourse + "/resume"), waiterAuth).andExpect(jsonPath("$.status").value("PENDING"));

        perform(post("/dining/order-courses/" + dessertCourse + "/cancel").content("{\"reason\":\"Too full\"}"), cashierAuth)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(courseReleaseRequestRepository.findAll()).allMatch(r -> r.getStatus() != CourseReleaseRequestEntity.Status.PENDING);
        JsonNode bill = json(perform(get("/orders/dine-in/tables/" + table.getId() + "/bill"), cashierAuth));
        assertThat(bill.get("totalOrdered").decimalValue()).isEqualByComparingTo("20.00"); // the cancelled dessert is off the bill

        // No sign-in, a customer account, or the HR role: none can release a course.
        mockMvc.perform(post("/dining/order-courses/" + starterCourse + "/release")).andExpect(status().isUnauthorized());
        UserEntity customer = createUser("Guest", "+233300009010", null, "secret123", Role.CUSTOMER);
        perform(post("/dining/order-courses/" + starterCourse + "/release"), "Bearer " + accessToken(customer)).andExpect(status().isForbidden());
        perform(post("/dining/order-courses/" + starterCourse + "/release"), otherBranchCashierAuth).andExpect(status().isForbidden());
        assertThat(courseStatus(starterCourse)).isEqualTo(CourseStatus.PENDING);
    }

    // ---- 17, 24. One bill, paid the usual way; 19. a closed table ---------------------------------------------------

    @Test
    void releasingDoesNotChargeAnything_theMealIsPaidAsOneBill_andAClosedTableStartsAfresh() throws Exception {
        guestOrder(List.of(line(soup, 1, starter), line(juice, 1, null)));
        perform(post("/dining/order-courses/" + mealCourseId("Starter") + "/release"), waiterAuth).andExpect(status().isOk());
        assertThat(paymentRepository.findAll()).isEmpty(); // release never takes payment

        // A table with a course still held cannot be closed.
        for (OrderEntity order : orderRepository.findByTableIdOrderByCreatedAtAsc(table.getId())) {
            pay(order);
        }
        guestOrder(List.of(line(cake, 1, dessert)));
        pay(orderRepository.findByTableIdOrderByCreatedAtAsc(table.getId()).stream()
                .filter(o -> o.getStatus() == OrderStatus.HELD).findFirst().orElseThrow());
        perform(post("/orders/dine-in/tables/" + table.getId() + "/close"), cashierAuth)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This table still has courses waiting to be released. Release or cancel them first"));

        JsonNode bill = json(perform(get("/orders/dine-in/tables/" + table.getId() + "/bill"), cashierAuth));
        assertThat(bill.get("totalOrdered").decimalValue()).isEqualByComparingTo("55.00");
        assertThat(bill.get("outstandingTotal").decimalValue()).isEqualByComparingTo("0.00");

        perform(post("/dining/order-courses/" + mealCourseId("Dessert") + "/release"), waiterAuth).andExpect(status().isOk());
        perform(post("/orders/dine-in/tables/" + table.getId() + "/close"), cashierAuth).andExpect(status().is2xxSuccessful());

        // The meal is closed: the QR page shows nothing of it, and "next course" has nothing to ask for.
        assertThat(diningOrderRepository.findAll()).allMatch(d -> d.getStatus() == DiningOrderEntity.Status.CLOSED);
        JsonNode view = publicView();
        assertThat(view.path("diningOrderId").asText(null)).isNull();
        assertThat(view.get("mealCourses")).isEmpty();
        assertThat(readJson(requestNext().andReturn().getResponse().getContentAsString()).get("outcome").asText()).isEqualTo("NO_PENDING_COURSE");

        // The next party starts a new meal.
        guestOrder(List.of(line(soup, 1, starter)));
        assertThat(diningOrderRepository.findAll()).hasSize(2);
    }

    // ---- 20. Branch isolation and guest input that cannot pick a branch -----------------------------------------

    @Test
    void guestsCannotUseAnotherBranchsCourse_andStaffOnlySeeTheirBranch() throws Exception {
        UUID legonOnly = createCourse(adminAuth, "Legon tasting", 15, legon.getId());
        guestOrder(List.of(line(soup, 1, legonOnly)), 400);
        guestOrderTo("not-a-table-token", List.of(line(soup, 1, starter)), 404);

        guestOrder(List.of(line(soup, 1, starter)));
        assertThat(json(perform(get("/dining/orders"), waiterAuth))).hasSize(1);
        assertThat(json(perform(get("/dining/orders"), otherBranchCashierAuth))).isEmpty();
        assertThat(json(perform(get("/dining/orders"), adminAuth))).hasSize(1);
        String mealId = json(perform(get("/dining/orders"), waiterAuth)).get(0).get("id").asText();
        perform(get("/dining/orders/" + mealId), otherBranchCashierAuth).andExpect(status().isForbidden());
    }

    // ---- 21. A notification failure never undoes an order ----------------------------------------------------------

    @Test
    void whenLiveUpdatesFail_theOrderAndTheRequestAreStillSaved() throws Exception {
        doThrow(new org.springframework.messaging.MessageDeliveryException("broker down"))
                .when(messagingTemplate).convertAndSend(anyString(), any(Object.class));
        guestOrder(List.of(line(soup, 1, starter)));
        requestNext().andExpect(status().isOk());
        assertThat(orderRepository.findAll()).hasSize(1);
        assertThat(courseReleaseRequestRepository.findAll()).hasSize(1);
        // Screens recover by reloading: the request is there for staff.
        doCallRealMethod().when(messagingTemplate).convertAndSend(anyString(), any(Object.class));
        assertThat(json(perform(get("/dining/course-release-requests/pending"), cashierAuth))).hasSize(1);
    }

    // ---- 22, 23. The existing waiter and bill buttons still work -------------------------------------------------

    @Test
    void callWaiterAndRequestBillStillWorkAlongsideCourses() throws Exception {
        guestOrder(List.of(line(soup, 1, starter)));
        requestNext().andExpect(status().isOk());
        for (String type : List.of("CALL_WAITER", "BILL")) {
            mockMvc.perform(post("/tables/public/" + table.getQrToken() + "/requests").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"" + type + "\"}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.type").value(type));
        }
        JsonNode serviceRequests = json(perform(get("/service-requests"), waiterAuth));
        assertThat(serviceRequests).hasSize(2);
        assertThat(json(perform(get("/dining/course-release-requests/pending"), waiterAuth))).hasSize(1);
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    private JsonNode guestOrder(List<Map<String, Object>> lines) throws Exception {
        return guestOrder(lines, 200);
    }

    private JsonNode guestOrder(List<Map<String, Object>> lines, int expected) throws Exception {
        return guestOrderTo(table.getQrToken(), lines, expected);
    }

    private JsonNode guestOrderTo(String token, List<Map<String, Object>> lines, int expected) throws Exception {
        String response = mockMvc.perform(post("/dining/public/tables/" + token + "/items").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("items", lines, "guestPhone", "0244123456", "guestName", "Abena"))))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return expected == 200 ? readJson(response) : null;
    }

    private ResultActions requestNext() throws Exception {
        return mockMvc.perform(post("/dining/public/tables/" + table.getQrToken() + "/request-next-course")
                .contentType(MediaType.APPLICATION_JSON).content("{\"sessionRef\":\"phone-1\"}"));
    }

    private JsonNode publicView() throws Exception {
        return readJson(mockMvc.perform(get("/dining/public/tables/" + table.getQrToken())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private UUID mealCourseId(String name) throws Exception {
        for (JsonNode course : publicView().get("mealCourses")) {
            if (course.get("courseName").asText().equals(name)) {
                return UUID.fromString(course.get("id").asText());
            }
        }
        throw new AssertionError("No course " + name);
    }

    private CourseStatus courseStatus(UUID orderCourseId) {
        return diningOrderCourseRepository.findById(orderCourseId).orElseThrow().getStatus();
    }

    private JsonNode kdsBoard(String station) throws Exception {
        return json(perform(get("/kds/board" + (station == null ? "" : "?station=" + station)), cashierAuth).andExpect(status().isOk()));
    }

    private void setStatus(UUID orderId, String next) throws Exception {
        perform(patch("/orders/" + orderId + "/status").content("{\"status\":\"" + next + "\"}"), cashierAuth).andExpect(status().isOk());
    }

    private void pay(OrderEntity order) throws Exception {
        perform(post("/payments/initiate").content("{\"orderId\":\"" + order.getId() + "\",\"method\":\"CASH\",\"idempotencyKey\":\"cash-"
                + order.getId() + "\",\"amountTendered\":" + order.getTotal() + "}"), cashierAuth).andExpect(status().isCreated());
    }

    private UUID createCourse(String auth, String name, int order) throws Exception {
        return createCourse(auth, name, order, null);
    }

    private UUID createCourse(String auth, String name, int order, UUID branchId) throws Exception {
        Map<String, Object> request = new java.util.HashMap<>(Map.of("name", name, "displayOrder", order));
        if (branchId != null) {
            request.put("branchId", branchId);
        }
        return UUID.fromString(json(perform(post("/dining/courses").content(body(request)), auth)
                .andExpect(status().isCreated())).get("id").asText());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String auth) throws Exception {
        return mockMvc.perform(request.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return readJson(result.andReturn().getResponse().getContentAsString());
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String body(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static Map<String, Object> line(MenuItemEntity item, int quantity, UUID courseId) {
        Map<String, Object> line = new java.util.HashMap<>();
        line.put("menuItemId", item.getId());
        line.put("quantity", quantity);
        if (courseId != null) {
            line.put("courseId", courseId);
        }
        return line;
    }

    private static List<String> names(JsonNode courses) {
        List<String> names = new ArrayList<>();
        courses.forEach(course -> names.add(course.get("name").asText()));
        return names;
    }

    private String staffAuth(String name, String phone, Role role, BranchEntity branch) {
        UserEntity user = createUser(name, phone, null, "secret123", role);
        user.setBranch(branch);
        userRepository.save(user);
        return "Bearer " + jwtService.generateAccessToken(user.getId(), role, branch.getId());
    }

    private BranchEntity branch(String name) {
        BranchEntity branch = new BranchEntity();
        branch.setCode(name.toUpperCase() + "-" + UUID.randomUUID().toString().substring(0, 6));
        branch.setName(name + " " + UUID.randomUUID().toString().substring(0, 4));
        branch.setActive(true);
        return branchRepository.save(branch);
    }

    private CategoryEntity category(String name, boolean beverage, String station) {
        CategoryEntity category = new CategoryEntity();
        category.setName(name + "-" + UUID.randomUUID());
        category.setDisplayOrder(1);
        category.setActive(true);
        category.setBeverage(beverage);
        category.setKitchenStation(station);
        return categoryRepository.save(category);
    }

    private MenuItemEntity item(CategoryEntity category, String name, String price) {
        MenuItemEntity item = new MenuItemEntity();
        item.setCategory(category);
        item.setName(name);
        item.setPrice(new BigDecimal(price));
        item.setAvailable(true);
        item.setActive(true);
        return menuItemRepository.save(item);
    }
}

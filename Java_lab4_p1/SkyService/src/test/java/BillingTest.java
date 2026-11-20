
import exception.InvalidSubscriptionException;
import exception.SubscriptionNotFoundException;
import model.CorporateSubscription;
import model.Option;
import model.PromoCode;
import model.Subscription;
import model.UserSubscription;
import billing.Billing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import servise.impl.EmailNotification;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

import repository.SubscriptionRepository;// new
import java.util.UUID; //new

class BillingTest {

    private Billing billing;

    // Этот метод выполняется автоматически ПЕРЕД каждым тестом, создавая чистую базу
    @BeforeEach
    void setUp() {  // update setup
        // Создаем изолированную базу H2 в памяти для каждого теста
        String testDbUrl = "jdbc:h2:mem:testdb_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        SubscriptionRepository repository = new SubscriptionRepository(testDbUrl, "sa", "");
        repository.initDatabase(); // Создает таблицы из schema.sql

        billing = new Billing(new EmailNotification(), repository);
    }

    @Test
    @DisplayName("1. Расчет цены: тариф STANDARD + 2 опции + скидка по промокоду")
    void shouldCalculatePriceWithOptionsAndPromoCode() {
        // Arrange (Подготовка данных)
        // База: 500 руб., тариф STANDARD (коэф. 1.40) -> 700 руб.
        UserSubscription sub = UserSubscription.builder()
                .ownerName("Иван Иванов")
                .baseMonthlyPrice(500.0)
                .planName("STANDARD")
                .build();

        // Добавляем 2 опции: 100 + 200 = 300 руб. (Итого до скидки: 700 + 300 = 1000 руб.)
        sub.addOption(Option.builder().name("Качество 4K").price(100.0).build());
        sub.addOption(Option.builder().name("Спорт-пакет").price(200.0).build());

        // Применяем промокод на скидку 20% (1000 * 0.80 = 800.0 руб.)
        sub.setAppliedPromo(PromoCode.builder().code("SKY20").discountPercent(20.0).build());

        // Act (Выполнение тестируемого действия)
        double actualPrice = sub.calculateBillingPrice();

        // Assert (Проверка результата с допустимой погрешностью 0.01 для double)
        assertEquals(800.0, actualPrice, 0.01, "Итоговая цена должна учитывать тариф, опции и скидку 20%");
    }

    @Test
    @DisplayName("2. Исключение: отрицательная цена при создании подписки")
    void shouldThrowExceptionWhenPriceIsNegative() {
        // Arrange
        double invalidPrice = -150.0;

        // Act & Assert (Для проверки исключений действие оборачивается внутрь assertThrows)
        assertThrows(InvalidSubscriptionException.class, () -> {
            new UserSubscription("Анна Смирнова", invalidPrice, "BASIC");
        }, "Создание подписки с отрицательной ценой должно вызывать InvalidSubscriptionException");
    }

    @Test
    @DisplayName("3. Исключение: попытка оплаты несуществующего Email")
    void shouldThrowExceptionWhenEmailNotFound() {
        // Arrange
        String nonExistentEmail = "ghost@sky.ru";

        // Act & Assert
        assertThrows(SubscriptionNotFoundException.class, () -> {
            billing.processPaymentByEmail(nonExistentEmail);
        }, "Оплата по отсутствующему в базе email должна выбрасывать SubscriptionNotFoundException");
    }

    @Test
    @DisplayName("4. Stream API: фильтрация VIP-клиентов выше заданного порога")
    void shouldFindOnlyVipClientsAboveThreshold() {
        // Arrange
        // Клиент 1: BASIC 300 руб. (чек = 300.0) -> НЕ VIP
        Subscription regularUser = new UserSubscription("Обычный Клиент", 300.0, "BASIC");
        // Клиент 2: PREMIUM 800 руб. (800 * 1.90 = 1520.0) -> VIP
        Subscription vipUser = new UserSubscription("VIP Клиент", 800.0, "PREMIUM");
        // Клиент 3: Корпоративный, 5 лицензий по 400 руб. (чек = 2000.0) -> VIP
        Subscription corpUser = new CorporateSubscription("ООО Ромашка", 400.0, "BASIC", 5);

        billing.registerSubscription("user1@test.ru", regularUser);
        billing.registerSubscription("user2@test.ru", vipUser);
        billing.registerSubscription("corp@test.ru", corpUser);

        // Act (Ищем клиентов с чеком строго больше 1000 руб.)
        List<Subscription> vipClients = billing.findVipClients(1000.0);

        // Assert
        assertEquals(2, vipClients.size(), "В списке должно быть ровно 2 VIP-клиента");
        assertFalse(vipClients.contains(regularUser), "Обычный клиент с чеком 300 руб. не должен попасть в VIP");
        assertTrue(vipClients.contains(vipUser), "VIP-клиент должен быть в списке");
        assertEquals(2, vipClients.size(), "В списке должно быть ровно 2 VIP-клиента");
        assertTrue(vipClients.stream().anyMatch(s -> s.getOwnerName().equals("VIP Клиент")), "VIP-клиент должен быть в списке");
        assertTrue(vipClients.stream().anyMatch(s -> s.getOwnerName().equals("ООО Ромашка")), "Корпоративный клиент должен быть в списке");
    }

    @Test
    @DisplayName("5. Stream API: подсчет общей прогнозируемой выручки")
    void shouldCalculateTotalRevenueCorrectly() {
        // Arrange
        // Подписка 1: BASIC 400 руб. -> 400.0
        Subscription sub1 = new UserSubscription("Клиент 1", 400.0, "BASIC");
        // Подписка 2: BASIC 500 руб. + опция 100 руб. -> 600.0
        Subscription sub2 = new UserSubscription("Клиент 2", 500.0, "BASIC");
        sub2.addOption(new Option("Без рекламы", 100.0));

        billing.registerSubscription("a@test.ru", sub1);
        billing.registerSubscription("b@test.ru", sub2);

        // Act
        double totalRevenue = billing.calculateTotalRevenue();

        // Assert (Ожидаем 400.0 + 600.0 = 1000.0)
        assertEquals(1000.0, totalRevenue, 0.01, "Общая выручка должна быть равна сумме чеков всех подписок");
    }
}

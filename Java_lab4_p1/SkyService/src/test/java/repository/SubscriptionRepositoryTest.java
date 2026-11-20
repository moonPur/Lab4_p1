package repository;

import billing.Billing;
import exception.SubscriptionNotFoundException;
import model.CorporateSubscription;
import model.Option;
import model.PromoCode;
import model.Subscription;
import model.UserSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import servise.impl.EmailNotification;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionRepositoryTest {

    private static final String TEST_JDBC_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";
    private SubscriptionRepository repository;
    private Billing billing;

    @BeforeEach
    void setUp() throws Exception {
        repository = new SubscriptionRepository(TEST_JDBC_URL, "sa", "");
        repository.initDatabase();

        // Очищаем таблицы перед каждым тестом для полной изоляции
        try (Connection conn = DriverManager.getConnection(TEST_JDBC_URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM subscription_options");
            stmt.execute("DELETE FROM subscriptions");
        }

        billing = new Billing(new EmailNotification(), repository);
    }

    @Test
    @DisplayName("Полный цикл Save & Load: сохранение UserSubscription с опциями и промокодом")
    void shouldSaveAndLoadUserSubscriptionWithOptionsAndPromo() {
        // Arrange
        String email = "ivan@sky.ru";
        // 500 * 1.4 (STANDARD) = 700 + 100 + 200 = 1000. Скидка 20% -> 800.0
        UserSubscription originalSub = UserSubscription.builder()
                .ownerName("Иван Петров")
                .baseMonthlyPrice(500.0)
                .planName("STANDARD")
                .build();
        originalSub.addOption(new Option("Качество 4K", 100.0));
        originalSub.addOption(new Option("Спорт-пакет", 200.0));
        originalSub.setAppliedPromo(new PromoCode("SKY20", 20.0));

        double expectedPrice = originalSub.calculateBillingPrice();

        // Act
        billing.registerSubscription(email, originalSub);
        Subscription loadedSub = repository.findByEmail(email);

        // Assert
        assertNotNull(loadedSub, "Подписка должна быть найдена в БД");
        assertInstanceOf(UserSubscription.class, loadedSub, "Тип восстановленного объекта должен быть UserSubscription");
        assertEquals("Иван Петров", loadedSub.getOwnerName());
        assertEquals(2, loadedSub.getOptions().size(), "Должны загрузиться обе опции из второй таблицы");
        assertNotNull(loadedSub.getAppliedPromo(), "Промокод должен восстановиться");
        assertEquals(expectedPrice, loadedSub.calculateBillingPrice(), 0.01, "Расчетная цена после загрузки из БД должна совпадать с исходной");
    }

    @Test
    @DisplayName("Сохранение и загрузка CorporateSubscription с учетом лицензий и опций")
    void shouldSaveAndLoadCorporateSubscription() {
        // Arrange
        String email = "corp@vector.ru";
        // (400 * 1.0 + 50) * 4 лицензии = 1800.0
        CorporateSubscription corpSub = CorporateSubscription.builder()
                .ownerName("ООО Вектор")
                .baseMonthlyPrice(400.0)
                .planName("BASIC")
                .activeLicensesCount(4)
                .build();
        corpSub.addOption(new Option("Выделенная поддержка", 50.0));

        // Act
        billing.registerSubscription(email, corpSub);
        Subscription loaded = repository.findByEmail(email);

        // Assert
        assertNotNull(loaded);
        assertInstanceOf(CorporateSubscription.class, loaded);
        assertEquals(4, ((CorporateSubscription) loaded).getActiveLicensesCount());
        assertEquals(1800.0, loaded.calculateBillingPrice(), 0.01);
    }

    @Test
    @DisplayName("Выброс SubscriptionNotFoundException при поиске несуществующего email в БД")
    void shouldThrowExceptionWhenEmailNotInDatabase() {
        assertThrows(SubscriptionNotFoundException.class, () -> {
            billing.processPaymentByEmail("missing@sky.ru");
        });
    }
}

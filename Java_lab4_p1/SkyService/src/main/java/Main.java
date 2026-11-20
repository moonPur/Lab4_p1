import billing.Billing;
//import exception.InvalidSubscriptionException;
import repository.SubscriptionRepository;
import exception.SubscriptionNotFoundException;
import model.CorporateSubscription;
import model.Option;
import model.PromoCode;
import model.Subscription;
import model.UserSubscription;
import net.datafaker.Faker;
import servise.NotificationService;
import servise.impl.EmailNotification;

import java.util.List;
import java.util.Locale;
//import java.util.Map;

import java.io.PrintStream; // for UTF-8 // костыль
import java.nio.charset.StandardCharsets; // for UTF-8 // костыль

public class Main {
    // Файловая база H2: данные сохранятся на диске после остановки программы
    private static final String JDBC_URL = "jdbc:h2:./data/skyservice_db";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    public static void main(String[] args) {


        // 1. Инициализируем репозиторий и создаем таблицы
        SubscriptionRepository repository = new SubscriptionRepository(JDBC_URL, DB_USER, DB_PASSWORD);
        repository.initDatabase();

        NotificationService emailService = new EmailNotification();
        Billing billing = new Billing(emailService, repository);

        // 2. ПЕРВЫЙ ЗАПУСК: Генерируем 30 пользователей в БД.
        // ВТОРОЙ ЗАПУСК: Закомментируйте строчку ниже, чтобы доказать чтение из файла БД!
        if (billing.getTotalSubscriptionsCount() == 0) {
            generateTestUsers(billing, 30);
        } else {
            System.out.println("[Система] Данные успешно загружены из существующего файла БД H2.");
        }

        // 3. Читаем аналитику из БД через Stream API
        System.out.println("\n=== СТАТИСТИКА ИЗ БАЗЫ ДАННЫХ H2 ===");
        System.out.println("Всего подписок в БД: " + billing.getTotalSubscriptionsCount());
        System.out.println("Общая прогнозируемая выручка: " + billing.calculateTotalRevenue() + " руб.");
        System.out.println("Количество PREMIUM подписок: " + billing.countByPlan("PREMIUM"));

        List<Subscription> vipClients = billing.findVipClients(1000.0);
        System.out.println("VIP-клиентов (чек > 1000 руб.): " + vipClients.size());
        vipClients.stream()
                .limit(5)
                .forEach(sub -> System.out.printf("  * %s [%s] — %.2f руб. (опций: %d)%n",
                        sub.getOwnerName(), sub.getPlanName(), sub.calculateBillingPrice(), sub.getOptions().size()));

        // 4. Проверка обработки исключения при поиске в БД
        System.out.println("\n=== ПРОВЕРКА ПОИСКА НЕСУЩЕСТВУЮЩЕГО КЛИЕНТА ===");
        try {
            billing.processPaymentByEmail("unknown_client@sky.ru");
        } catch (SubscriptionNotFoundException e) {
            System.out.println("[Перехвачено исключение]: " + e.getMessage());
        }

        // 5. Вывод всех записей из базы данных для визуальной проверки
        System.out.println("\n=== ПОЛНЫЙ СПИСОК ПОДПИСОК В БД H2 ===");
        repository.findAll().forEach(System.out::println);


    }

    private static void generateTestUsers(Billing billing, int count) {
        Faker faker = new Faker(new Locale("ru"));
        String[] plans = {"BASIC", "STANDARD", "PREMIUM"};
        String[] availableOptions = {"Качество 4K", "Без рекламы", "Спорт-пакет", "Семейный доступ"};

        for (int i = 0; i < count; i++) {
            String email = "user_" + i + "_" + faker.internet().emailAddress();
            String plan = plans[faker.random().nextInt(plans.length)];
            double basePrice = faker.number().randomDouble(2, 200, 600);

            Subscription sub;
            if (i % 5 == 0) {
                sub = CorporateSubscription.builder()
                        .ownerName(faker.company().name())
                        .baseMonthlyPrice(basePrice)
                        .planName(plan)
                        .activeLicensesCount(faker.number().numberBetween(2, 10))
                        .build();
            } else {
                sub = UserSubscription.builder()
                        .ownerName(faker.name().fullName())
                        .baseMonthlyPrice(basePrice)
                        .planName(plan)
                        .build();

                int optionsCount = faker.number().numberBetween(1, 3);
                for (int j = 0; j < optionsCount; j++) {
                    sub.addOption(new Option(
                            availableOptions[faker.random().nextInt(availableOptions.length)],
                            faker.number().numberBetween(50, 150)
                    ));
                }

                if (faker.bool().bool()) {
                    sub.setAppliedPromo(new PromoCode("SKY" + faker.number().digits(2), 15.0));
                }
            }

            billing.registerSubscription(email, sub);
        }
        System.out.println("[Система] Сгенерировано и сохранено в БД пользователей: " + count);
    }
}

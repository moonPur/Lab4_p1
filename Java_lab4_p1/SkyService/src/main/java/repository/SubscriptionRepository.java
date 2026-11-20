package repository;

import exception.DataAccessException;
import lombok.RequiredArgsConstructor;
import model.CorporateSubscription;
import model.Option;
import model.PromoCode;
import model.Subscription;
import model.UserSubscription;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class SubscriptionRepository {
    private final String jdbcUrl;
    private final String user;
    private final String password;

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, user, password);
    }

    /**
     * Читает schema.sql из resources и создает таблицы при старте.
     */
    public void initDatabase() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
            if (is == null) {
                throw new DataAccessException("Файл schema.sql не найден в папке resources");
            }
            String sqlScript = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                for (String query : sqlScript.split(";")) {
                    if (!query.isBlank()) {
                        stmt.execute(query.trim());
                    }
                }
            }
        } catch (Exception e) {
            throw new DataAccessException("Ошибка при инициализации базы данных", e);
        }
    }

    /**
     * Сохраняет подписку и все её опции в БД (с перезаписью при повторном email).
     */
    public void save(String email, Subscription sub) {
        String mergeSubSql = """
                MERGE INTO subscriptions (email, owner_name, sub_type, plan_name, base_price, licenses_count, promo_code, promo_discount)
                KEY (email)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String deleteOptionsSql = "DELETE FROM subscription_options WHERE subscription_email = ?";
        String insertOptionSql = "INSERT INTO subscription_options (subscription_email, option_name, option_price) VALUES (?, ?, ?)";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // Начинаем транзакцию
            try (PreparedStatement subStmt = conn.prepareStatement(mergeSubSql);
                 PreparedStatement delOptStmt = conn.prepareStatement(deleteOptionsSql);
                 PreparedStatement insOptStmt = conn.prepareStatement(insertOptionSql)) {

                boolean isCorp = sub instanceof CorporateSubscription;
                int licenses = isCorp ? ((CorporateSubscription) sub).getActiveLicensesCount() : 1;

                subStmt.setString(1, email);
                subStmt.setString(2, sub.getOwnerName());
                subStmt.setString(3, isCorp ? "CORPORATE" : "USER");
                subStmt.setString(4, sub.getPlanName());
                subStmt.setDouble(5, sub.getBaseMonthlyPrice());
                subStmt.setInt(6, licenses);

                if (sub.getAppliedPromo() != null) {
                    subStmt.setString(7, sub.getAppliedPromo().getCode());
                    subStmt.setDouble(8, sub.getAppliedPromo().getDiscountPercent());
                } else {
                    subStmt.setString(7, null);
                    subStmt.setDouble(8, 0.0);
                }
                subStmt.executeUpdate();

                // Очищаем старые опции и записываем актуальный список
                delOptStmt.setString(1, email);
                delOptStmt.executeUpdate();

                for (Option option : sub.getOptions()) {
                    insOptStmt.setString(1, email);
                    insOptStmt.setString(2, option.getName());
                    insOptStmt.setDouble(3, option.getPrice());
                    insOptStmt.addBatch();
                }
                insOptStmt.executeBatch();

                conn.commit(); // Фиксируем транзакцию
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка при сохранении подписки для " + email, e);
        }
    }

    /**
     * Ищет подписку по email и подгружает её опции из второй таблицы.
     */
    public Subscription findByEmail(String email) {
        String selectSubSql = "SELECT * FROM subscriptions WHERE email = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectSubSql)) {

            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToSubscription(conn, rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка при поиске подписки по email: " + email, e);
        }
    }

    /**
     * Возвращает все подписки из базы данных для аналитики Stream API.
     */
    public List<Subscription> findAll() {
        String selectAllSql = "SELECT * FROM subscriptions";
        List<Subscription> result = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectAllSql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                result.add(mapRowToSubscription(conn, rs));
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка при получении списка всех подписок", e);
        }
    }

    private Subscription mapRowToSubscription(Connection conn, ResultSet rs) throws SQLException {
        String email = rs.getString("email");
        String ownerName = rs.getString("owner_name");
        String subType = rs.getString("sub_type");
        String planName = rs.getString("plan_name");
        double basePrice = rs.getDouble("base_price");
        int licensesCount = rs.getInt("licenses_count");
        String promoCode = rs.getString("promo_code");
        double promoDiscount = rs.getDouble("promo_discount");

        Subscription sub;
        if ("CORPORATE".equalsIgnoreCase(subType)) {
            sub = new CorporateSubscription(ownerName, basePrice, planName, licensesCount);
        } else {
            sub = new UserSubscription(ownerName, basePrice, planName);
        }

        if (promoCode != null) {
            sub.setAppliedPromo(new PromoCode(promoCode, promoDiscount));
        }

        // Подгружаем связанные опции из таблицы subscription_options
        String selectOptionsSql = "SELECT option_name, option_price FROM subscription_options WHERE subscription_email = ?";
        try (PreparedStatement optStmt = conn.prepareStatement(selectOptionsSql)) {
            optStmt.setString(1, email);
            try (ResultSet optRs = optStmt.executeQuery()) {
                while (optRs.next()) {
                    sub.addOption(new Option(
                            optRs.getString("option_name"),
                            optRs.getDouble("option_price")
                    ));
                }
            }
        }

        return sub;
    }
}

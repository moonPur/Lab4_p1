package model;

import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.EqualsAndHashCode;

@ToString(callSuper = true)
@SuperBuilder
@EqualsAndHashCode(callSuper = true) // <-- добавить
public class UserSubscription extends Subscription {

    public UserSubscription(String ownerName, double baseMonthlyPrice, String planName) {
        super(ownerName, baseMonthlyPrice, planName);
    }

    @Override
    public double calculateBillingPrice() {
        final double planModifier = switch (getPlanName()) {
            case "BASIC" -> 1.0;
            case "STANDARD" -> 1.40;
            case "PREMIUM" -> 1.90;
            default -> 1.0;
        };

        // 1. Базовый тариф + сумма всех подключенных опций
        double totalPrice = (getBaseMonthlyPrice() * planModifier) + calculateOptionsTotal();

        // 2. Применяем скидку по промокоду, если он есть
        if (getAppliedPromo() != null) {
            double discount = getAppliedPromo().getDiscountPercent() / 100.0;
            totalPrice *= (1.0 - discount);
        }

        return Math.round(totalPrice * 100.0) / 100.0;
    }
}

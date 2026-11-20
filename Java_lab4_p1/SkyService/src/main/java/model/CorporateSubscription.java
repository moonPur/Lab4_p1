package model;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.EqualsAndHashCode;

@Getter
@ToString(callSuper = true)
@SuperBuilder
@EqualsAndHashCode(callSuper = true) // <-- добавить
public class CorporateSubscription extends Subscription {
    private final int activeLicensesCount;

    public CorporateSubscription(String ownerName, double baseMonthlyPrice, String planName, int activeLicensesCount) {
        super(ownerName, baseMonthlyPrice, planName);
        this.activeLicensesCount = Math.max(1, activeLicensesCount);
    }

    @Override
    public double calculateBillingPrice() {
        String normalizedPlan = getPlanName() != null ? getPlanName().toUpperCase() : "STANDARD";

        final double planModifier = switch (normalizedPlan) {
            case "BASIC" -> 1.0;
            case "STANDARD" -> 1.30;
            case "PREMIUM" -> 1.70;
            default -> 1.0;
        };

        int safeLicensesCount = Math.max(1, activeLicensesCount);
        double singleLicensePrice = (getBaseMonthlyPrice() * planModifier) + calculateOptionsTotal();
        double rawTotal = singleLicensePrice * safeLicensesCount;

        // Применяем скидку по промокоду, если он установлен
        double discountedTotal = applyPromoDiscount(rawTotal);

        return Math.round(discountedTotal * 100.0) / 100.0;
    }
}

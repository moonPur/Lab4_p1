package billing;

import exception.SubscriptionNotFoundException;
//import lombok.Getter;
import lombok.RequiredArgsConstructor;
import model.Subscription;
import servise.NotificationService;

//import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import repository.SubscriptionRepository;

@RequiredArgsConstructor
public class Billing {
    private final NotificationService notificationService;
    private final SubscriptionRepository repository;


    public void registerSubscription(String email, Subscription sub) {
        repository.save(email, sub);
    }

    public void processPaymentByEmail(String email) throws SubscriptionNotFoundException {
        Subscription sub = repository.findByEmail(email);
        if (sub == null) {
            throw new SubscriptionNotFoundException("Пользователь с email " + email + " не найден в базе данных!");
        }
        double finalPrice = sub.calculateBillingPrice();
        String message = String.format("Счет оплачен. Тариф [%s]. Списано: %.2f руб.",
                sub.getPlanName(), finalPrice);
        notificationService.sendNotification(message, email);
    }

    public List<Subscription> findVipClients(double minAmount) {
        return repository.findAll().stream()
                .filter(sub -> sub.calculateBillingPrice() > minAmount)
                .toList();
    }

    public double calculateTotalRevenue() {
        double total = repository.findAll().stream()
                .mapToDouble(Subscription::calculateBillingPrice)
                .sum();
        return Math.round(total * 100.0) / 100.0;
    }

    public long countByPlan(String planName) {
        return repository.findAll().stream()
                .filter(sub -> sub.getPlanName().equalsIgnoreCase(planName))
                .count();
    }

    public Map<String, List<Subscription>> groupSubscriptionsByPlan() {
        return repository.findAll().stream()
                .collect(Collectors.groupingBy(Subscription::getPlanName));
    }

    public int getTotalSubscriptionsCount() {
        return repository.findAll().size();
    }
}

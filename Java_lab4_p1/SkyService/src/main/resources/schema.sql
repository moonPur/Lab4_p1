CREATE TABLE IF NOT EXISTS subscriptions (
    email VARCHAR(255) PRIMARY KEY,
    owner_name VARCHAR(255) NOT NULL,
    sub_type VARCHAR(50) NOT NULL,
    plan_name VARCHAR(50) NOT NULL,
    base_price DOUBLE NOT NULL,
    licenses_count INT DEFAULT 1,
    promo_code VARCHAR(100),
    promo_discount DOUBLE DEFAULT 0.0
);

CREATE TABLE IF NOT EXISTS subscription_options (
    id INT AUTO_INCREMENT PRIMARY KEY,
    subscription_email VARCHAR(255) NOT NULL,
    option_name VARCHAR(255) NOT NULL,
    option_price DOUBLE NOT NULL,
    FOREIGN KEY (subscription_email) REFERENCES subscriptions(email) ON DELETE CASCADE
);

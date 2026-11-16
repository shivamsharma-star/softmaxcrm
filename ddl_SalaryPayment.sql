CREATE TABLE salary_payments
(
    salary_payment_id BIGINT       NOT NULL,
    created_at        datetime     NULL,
    updated_at        datetime     NULL,
    user_id           BIGINT       NOT NULL,
    payment_id        BIGINT       NOT NULL,
    month             VARCHAR(255) NULL,
    salary_amount     VARCHAR(255) NULL,
    CONSTRAINT pk_salary_payments PRIMARY KEY (salary_payment_id)
);

ALTER TABLE salary_payments
    ADD CONSTRAINT FK_SALARY_PAYMENTS_ON_PAYMENT FOREIGN KEY (payment_id) REFERENCES payment_details (payment_id);

ALTER TABLE salary_payments
    ADD CONSTRAINT FK_SALARY_PAYMENTS_ON_USER FOREIGN KEY (user_id) REFERENCES users (user_id);
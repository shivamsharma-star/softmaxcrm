CREATE TABLE project_payments
(
    project_payment_id BIGINT   NOT NULL,
    created_at         datetime NULL,
    updated_at         datetime NULL,
    project_id         BIGINT   NOT NULL,
    payment_id         BIGINT   NOT NULL,
    CONSTRAINT pk_project_payments PRIMARY KEY (project_payment_id)
);

ALTER TABLE project_payments
    ADD CONSTRAINT FK_PROJECT_PAYMENTS_ON_PAYMENT FOREIGN KEY (payment_id) REFERENCES payment_details (payment_id);

ALTER TABLE project_payments
    ADD CONSTRAINT FK_PROJECT_PAYMENTS_ON_PROJECT FOREIGN KEY (project_id) REFERENCES project_details (project_id);
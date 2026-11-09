CREATE TABLE merge_event_audit
(
    merge_event_audit_id    UUID                        NOT NULL,
    surviving_prison_number VARCHAR(255)                NOT NULL,
    merged_prison_number    VARCHAR(255)                NOT NULL,
    message_received_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_merge_event_audit PRIMARY KEY (merge_event_audit_id)
);
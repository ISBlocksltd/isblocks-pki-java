-- These columns are added by the JPA provider if there are sufficient privileges
-- ALTER TABLE KeyRecoveryData ADD cryptoTokenId INT4 DEFAULT 0 NOT NULL;
-- ALTER TABLE KeyRecoveryData ADD keyAlias TEXT DEFAULT NULL;
-- ALTER TABLE KeyRecoveryData ADD publicKeyId TEXT DEFAULT NULL;
-- If there were existing data in the table, we set the value of cryptoTokenId to 0, even if it's default 0
-- UPDATE KeyRecoveryData SET cryptoTokenId=0;

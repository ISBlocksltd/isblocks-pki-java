/*************************************************************************
 *                                                                       *
 *  CESeCore: CE Security Core                                           *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.cesecore.audit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

import org.cesecore.audit.audit.AuditLogReportElem;
import org.cesecore.audit.audit.AuditLogValidationReport;
import org.cesecore.audit.audit.AuditLogValidatorException;
import org.cesecore.audit.audit.SecurityEventsAuditorSessionRemote;
import org.cesecore.audit.impl.integrityprotected.IntegrityProtectedAuditorProxySessionRemote;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authentication.tokens.UsernamePrincipal;
import org.cesecore.authentication.tokens.X509CertificateAuthenticationToken;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.mock.authentication.tokens.TestAlwaysAllowLocalAuthenticationToken;
import org.cesecore.util.EjbRemoteHelper;
import org.cesecore.util.query.Criteria;
import org.cesecore.util.query.QueryCriteria;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.keyfactor.util.CertTools;
import com.keyfactor.util.CryptoProviderTools;
import com.keyfactor.util.crypto.algorithm.AlgorithmConstants;
import com.keyfactor.util.keys.KeyTools;
import com.keyfactor.util.keys.token.CryptoToken;

/**
 * Security auditor logs auditor functional tests;
 * 
 * @version $Id$
 * 
 */
public class SecurityEventsAuditorSessionBeanTest extends SecurityEventsBase {

    private final SecurityEventsAuditorSessionRemote securityEventsAuditor = EjbRemoteHelper.INSTANCE.getRemoteSession(SecurityEventsAuditorSessionRemote.class);

    private static KeyPair keys;

    @BeforeClass
    public static void setUpCryptoProvider() throws Exception {
        CryptoProviderTools.installBCProvider();

        keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        
        /*
         * Clean out audit logs. Sorry, but this test becomes untenable with full 
         * log tables. Full deletion of the logs occurs in test05ExportLogs in any case. 
         */
        IntegrityProtectedAuditorProxySessionRemote integrityProtectedAuditorProxySession = EjbRemoteHelper.INSTANCE.getRemoteSession(IntegrityProtectedAuditorProxySessionRemote.class, EjbRemoteHelper.MODULE_TEST);
        final AuthenticationToken authenticationToken = new TestAlwaysAllowLocalAuthenticationToken(new UsernamePrincipal(SecurityEventsAuditorSessionBeanTest.class.getSimpleName()));
        integrityProtectedAuditorProxySession.deleteRows(authenticationToken, new Date(), null);
    }

    @Test
    public void test02SelectLogsWithCriteria() throws AuthorizationDeniedException {
        for (final String logDeviceId : securityEventsAuditor.getQuerySupportingLogDevices()) {
            final List<? extends AuditLogEntry> list = securityEventsAuditor.selectAuditLogs(roleMgmgToken, 1, 10,
                    QueryCriteria.create().add(Criteria.eq(AuditLogEntry.FIELD_AUTHENTICATION_TOKEN, "userid")), logDeviceId);
            assertTrue(list.size() >= 0);
        }
    }

    @Test
    public void test03SelectLogsWithNullCriteria() throws AuthorizationDeniedException {
        for (final String logDeviceId : securityEventsAuditor.getQuerySupportingLogDevices()) {
            final List<? extends AuditLogEntry> list = securityEventsAuditor.selectAuditLogs(roleMgmgToken, 1, 10, null, logDeviceId);
            assertTrue(list.size() >= 0);
        }
    }

    /**
     * Validates existing audit logs since DB will always have audit logs (at least the ones generated by exports)
     */
    @Test
    public void test04AuditLogsValidation() throws AuditLogValidatorException, AuthorizationDeniedException {
        for (final String logDeviceId : securityEventsAuditor.getQuerySupportingLogDevices()) {
            final AuditLogValidationReport report = securityEventsAuditor.verifyLogsIntegrity(roleMgmgToken, new Date(), logDeviceId);
            assertNotNull(report);
            final StringBuilder strBuilder = new StringBuilder();
            strBuilder.append("logDevice: ").append(logDeviceId).append("\n");
            for (final AuditLogReportElem error : report.errors()) {
                strBuilder.append(String.format("invalid sequence: %d %d\n", error.getFirst(), error.getSecond()));
                for (final String reason : error.getReasons()) {
                    strBuilder.append(String.format("Reason: %s\n", reason));
                }
            }
            assertTrue("validation report: " + strBuilder.toString(), (report.warnings().size() == 1 || report.warnings().size() == 0)
                    && report.errors().size() == 0);
        }
    }

    @Test
    public void test05ExportLogs() throws Exception {
        final CryptoToken cryptoToken = createTokenWithKeyPair();
        for (final String logDeviceId : securityEventsAuditor.getQuerySupportingLogDevices()) {
            final String file0 = securityEventsAuditor.exportAuditLogs(roleMgmgToken, cryptoToken, new Date(), false, keyAlias, keyPairSignAlgorithm,
                    logDeviceId).getExportedFile();
            final File f0 = new File(file0);
            final long length0 = f0.length();
            assertTrue("file does not exist, " + f0.getAbsolutePath(), f0.exists());
            assertTrue("file length is not > 0, " + f0.getAbsolutePath(), length0 > 0);
            assertTrue("file can not be deleted, " + f0.getAbsolutePath(), f0.delete());
            // Doing it again, but deleting logs after export this time
            final String file1 = securityEventsAuditor.exportAuditLogs(roleMgmgToken, cryptoToken, new Date(), true, keyAlias, keyPairSignAlgorithm,
                    logDeviceId).getExportedFile();
            final File f1 = new File(file1);
            final long length1 = f1.length();
            assertTrue("file does not exist, " + f1.getAbsolutePath(), f1.exists());
            assertTrue("file length is not > 0, " + f1.getAbsolutePath(), length1 > 0);
            assertTrue("f1 length is not >= f0 length", length1 >= length0);
            assertTrue("file can not be deleted, " + f1.getAbsolutePath(), f1.delete());
            // Doing it again should give less result, since logs have been deleted
            final String file2 = securityEventsAuditor.exportAuditLogs(roleMgmgToken, cryptoToken, new Date(), true, keyAlias, keyPairSignAlgorithm,
                    logDeviceId).getExportedFile();
            final File f2 = new File(file2);
            final long length2 = f2.length();
            assertTrue("file does not exist, " + f2.getAbsolutePath(), f2.exists());
            assertTrue("file length is not > 0, " + f2.getAbsolutePath(), length2 > 0);

            assertTrue("file can not be deleted, " + f2.getAbsolutePath(), f2.delete());
        }
    }

    @Test
    public void test06Authorization() throws Exception {
        final X509Certificate certificate = CertTools.genSelfCert("C=SE,O=Test,CN=Test LogMgmtSessionNoAuth", 365, null, keys.getPrivate(),
                keys.getPublic(), AlgorithmConstants.SIGALG_SHA1_WITH_RSA, true);
        final AuthenticationToken adminTokenNoAuth = new X509CertificateAuthenticationToken(certificate);
        for (final String logDeviceId : securityEventsAuditor.getQuerySupportingLogDevices()) {
            // SelectLogsWithNoCriteria
            try {
                securityEventsAuditor.selectAuditLogs(adminTokenNoAuth, 1, 10,
                        QueryCriteria.create().add(Criteria.orderDesc(AuditLogEntry.FIELD_TIMESTAMP)), logDeviceId);
                assertTrue("should throw", false);
            } catch (final AuthorizationDeniedException e) {
                // NOPMD
            }
            // AuditLogsValidation
            try {
                securityEventsAuditor.verifyLogsIntegrity(adminTokenNoAuth, new Date(), logDeviceId);
                assertTrue("should throw", false);
            } catch (final AuthorizationDeniedException e) {
                // NOPMD
            }
            // ExportLogs
            try {
                final CryptoToken cryptoToken = createTokenWithKeyPair();
                securityEventsAuditor.exportAuditLogs(adminTokenNoAuth, cryptoToken, new Date(), true, keyAlias, keyPairSignAlgorithm, logDeviceId);
                assertTrue("should throw", false);
            } catch (final AuthorizationDeniedException e) {
                // NOPMD
            }
        }
    }

    @AfterClass
    public static void rmCryptoProvider() {
        CryptoProviderTools.removeBCProvider();
    }
}

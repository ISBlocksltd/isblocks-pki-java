/*************************************************************************
 *                                                                       *
 *  EJBCA Community: The OpenSource Certificate Authority                *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.ejbca.core.ejb.ca.sign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1OutputStream;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jcajce.provider.asymmetric.ecgost.BCECGOST3410PublicKey;
import org.bouncycastle.jce.ECGOST3410NamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.provider.JCEECPublicKey;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.cesecore.CaTestUtils;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authentication.tokens.UsernamePrincipal;
import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.ca.CaSessionRemote;
import org.cesecore.certificates.certificate.request.PKCS10RequestMessage;
import org.cesecore.certificates.certificate.request.ResponseMessage;
import org.cesecore.certificates.certificate.request.X509ResponseMessage;
import org.cesecore.certificates.certificateprofile.CertificateProfile;
import org.cesecore.certificates.certificateprofile.CertificateProfileConstants;
import org.cesecore.certificates.certificateprofile.CertificateProfileSessionRemote;
import org.cesecore.certificates.endentity.EndEntityConstants;
import org.cesecore.mock.authentication.tokens.TestAlwaysAllowLocalAuthenticationToken;
import org.cesecore.util.EjbRemoteHelper;
import org.ejbca.core.ejb.ra.EndEntityManagementSessionRemote;
import org.ejbca.core.ejb.ra.raadmin.EndEntityProfileSessionRemote;
import org.ejbca.core.model.ra.raadmin.EndEntityProfile;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.keyfactor.util.CertTools;
import com.keyfactor.util.CryptoProviderTools;
import com.keyfactor.util.crypto.algorithm.AlgorithmConfigurationCache;
import com.keyfactor.util.crypto.algorithm.AlgorithmConstants;

/**
 * 
 * @version $Id$
 *
 */

public class SignSessionWithECGOST3410Test extends SignSessionCommon {

    private static final Logger log = Logger.getLogger(SignSessionWithECGOST3410Test.class);

    private static final AuthenticationToken internalAdmin = new TestAlwaysAllowLocalAuthenticationToken(new UsernamePrincipal(
            "ECGOST3410SignSessionTest"));

    private CaSessionRemote caSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class);
    private SignSessionRemote signSession = EjbRemoteHelper.INSTANCE.getRemoteSession(SignSessionRemote.class);
    private EndEntityManagementSessionRemote userAdminSession = EjbRemoteHelper.INSTANCE.getRemoteSession(EndEntityManagementSessionRemote.class);
    private static CertificateProfileSessionRemote certificateProfileSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CertificateProfileSessionRemote.class);
    private static EndEntityProfileSessionRemote endEntityProfileSession = EjbRemoteHelper.INSTANCE.getRemoteSession(EndEntityProfileSessionRemote.class);

    private static final String ECGOST3410_USERNAME = "Ecgost3410User";
    
    private static KeyPair gostkeys;
    
    private static int certificateProfileId = 0;
    
    private static int endEntityProfileId = 0;
    
    @BeforeClass
    public static void beforeClass() throws Exception {
        AlgorithmConfigurationCache.INSTANCE.setGost3410Enabled(true);
        // Install BouncyCastle provider
        CryptoProviderTools.installBCProviderIfNotAvailable();

        CertificateProfile certificateProfile = new CertificateProfile(CertificateProfileConstants.CERTPROFILE_FIXED_ENDUSER);
        certificateProfile.setAvailableKeyAlgorithms(new String[]{"ECGOST3410"});
        certificateProfileId = certificateProfileSession.addCertificateProfile(internalAdmin, ECGOST3410_USERNAME, certificateProfile);
        
        EndEntityProfile endEntityProfile = new EndEntityProfile(true);
        endEntityProfile.setAvailableCertificateProfileIds(Arrays.asList(certificateProfileId));
        endEntityProfileId = endEntityProfileSession.addEndEntityProfile(internalAdmin, ECGOST3410_USERNAME, endEntityProfile);
        
        createECGOST3410Ca();
        createEcgost3410EndEntity();
        
        KeyPairGenerator keygen = KeyPairGenerator.getInstance("ECGOST3410", BouncyCastleProvider.PROVIDER_NAME);
        final String keyspec = "GostR3410-2001-CryptoPro-B";
        AlgorithmParameterSpec ecSpec = ECGOST3410NamedCurveTable.getParameterSpec(keyspec); 
        keygen.initialize(ecSpec);
        gostkeys = keygen.generateKeyPair();
        
       
            
    }

    @AfterClass
    public static void afterClass() throws Exception {

        EndEntityManagementSessionRemote userAdminSession = EjbRemoteHelper.INSTANCE.getRemoteSession(EndEntityManagementSessionRemote.class);
        userAdminSession.deleteUser(internalAdmin, ECGOST3410_USERNAME);      
        CaTestUtils.removeCa(internalAdmin, ECGOST3410_USERNAME, ECGOST3410_USERNAME);
        certificateProfileSession.removeCertificateProfile(internalAdmin, ECGOST3410_USERNAME);
        endEntityProfileSession.removeEndEntityProfile(internalAdmin, ECGOST3410_USERNAME);

    }
    
    @Test
    public void testSignSessionECGOST3410WithECGOST3410CA() throws Exception {
        AlgorithmConfigurationCache.INSTANCE.setGost3410Enabled(true);
        log.trace(">test14SignSessionECGOST3410WithECGOST3410CA()");
        userAdminSession.setUserStatus(internalAdmin, ECGOST3410_USERNAME, EndEntityConstants.STATUS_NEW);
        log.debug("Reset status of '" + ECGOST3410_USERNAME + "' to NEW");

        X509Certificate selfcert = CertTools.genSelfCert("CN=selfsigned", 1, null, gostkeys.getPrivate(), gostkeys.getPublic(),
                AlgorithmConstants.SIGALG_GOST3411_WITH_ECGOST3410, false);

        X509Certificate cert = (X509Certificate) signSession.createCertificate(internalAdmin, ECGOST3410_USERNAME, "foo123", selfcert);
        
        assertNotNull("Failed to create certificate", cert);
        log.debug("Cert=" + cert.toString());
        PublicKey pk = cert.getPublicKey();
        checkECKey(pk);
        X509Certificate ecgost3410cacert = (X509Certificate) caSession.getCAInfo(internalAdmin, TEST_ECGOST3410_CA_NAME).getCertificateChain().toArray()[0];
        try {
            cert.verify(ecgost3410cacert.getPublicKey());
        } catch (Exception e) {
            assertTrue("Verify failed: " + e.getMessage(), false);
        }
        log.trace("<test14SignSessionECGOST3410WithECGOST3410CA()");
    }

    /**
     * tests bouncy PKCS10
     */
    @Test
    public void testBCPKCS10ECGOST3410WithECGOST3410CA() throws Exception {
        AlgorithmConfigurationCache.INSTANCE.setGost3410Enabled(true);
        log.trace(">test15TestBCPKCS10ECGOST3410WithECGOST3410CA()");
        userAdminSession.setUserStatus(internalAdmin, ECGOST3410_USERNAME, EndEntityConstants.STATUS_NEW);
        log.debug("Reset status of '" + ECGOST3410_USERNAME + "' to NEW");
        // Create certificate request
        PKCS10CertificationRequest req = CertTools.genPKCS10CertificationRequest("GOST3411withECGOST3410", CertTools.stringToBcX500Name("C=SE, O=AnaTom, CN="
                + ECGOST3410_USERNAME), gostkeys.getPublic(), new DERSet(), gostkeys.getPrivate(), null);
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        ASN1OutputStream dOut = ASN1OutputStream.create(bOut, ASN1Encoding.DER);
        
        dOut.writeObject(req.toASN1Structure());
        dOut.close();

        PKCS10CertificationRequest req2 = new PKCS10CertificationRequest(bOut.toByteArray());
        ContentVerifierProvider verifier = CertTools.genContentVerifierProvider(gostkeys.getPublic());
        boolean verify = req2.isSignatureValid(verifier);
        log.debug("Verify returned " + verify);
        assertTrue(verify);
        log.debug("CertificationRequest generated successfully.");
        
        byte[] bcp10 = bOut.toByteArray();
        PKCS10RequestMessage p10 = new PKCS10RequestMessage(bcp10);
        p10.setUsername(ECGOST3410_USERNAME);
        p10.setPassword("foo123");
        
        ResponseMessage resp = signSession.createCertificate(internalAdmin, p10, X509ResponseMessage.class, null);
        
        Certificate cert = CertTools.getCertfromByteArray(resp.getResponseMessage(), Certificate.class);
        assertNotNull("Failed to create certificate", cert);
        log.debug("Cert=" + cert.toString());
        PublicKey pk = cert.getPublicKey();
        checkECKey(pk);
        try {
            X509Certificate ecdsacacert = (X509Certificate) caSession.getCAInfo(internalAdmin, TEST_ECGOST3410_CA_NAME).getCertificateChain().toArray()[0];
            cert.verify(ecdsacacert.getPublicKey());
        } catch (Exception e) {
            assertTrue("Verify failed: " + e.getMessage(), false);
        }
       
        log.trace("<test15TestBCPKCS10ECGOST3410WithECGOST3410CA()");
    }
    
    private void checkECKey(PublicKey pk) {
        if (pk instanceof JCEECPublicKey) {
            JCEECPublicKey ecpk = (JCEECPublicKey) pk;
            assertEquals(ecpk.getAlgorithm(), "EC");
            org.bouncycastle.jce.spec.ECParameterSpec spec = ecpk.getParameters();
            assertNotNull("Only ImplicitlyCA curves can have null spec", spec);
        } else if (pk instanceof BCECPublicKey) {
            BCECPublicKey ecpk = (BCECPublicKey) pk;
            assertEquals(ecpk.getAlgorithm(), "EC");
            org.bouncycastle.jce.spec.ECParameterSpec spec = ecpk.getParameters();
            assertNotNull("Only ImplicitlyCA curves can have null spec", spec);
        } else if (pk instanceof BCECGOST3410PublicKey) {
            BCECGOST3410PublicKey ecpk = (BCECGOST3410PublicKey) pk;
            assertEquals(ecpk.getAlgorithm(), AlgorithmConstants.KEYALGORITHM_ECGOST3410);
            org.bouncycastle.jce.spec.ECParameterSpec spec = ecpk.getParameters();
            assertNotNull("Only ImplicitlyCA curves can have null spec", spec);
        }else {
            assertTrue("Public key is not EC: "+pk.getClass().getName(), false);
        }        
    }
    
    @Override
    public String getRoleName() {
        return SignSessionWithECGOST3410Test.class.getSimpleName();
    }

    private static void createEcgost3410EndEntity() throws Exception {
        CaSessionRemote caSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class);
        CAInfo infoecdsa = caSession.getCAInfo(internalAdmin, TEST_ECGOST3410_CA_NAME);
        assertTrue("No active ECGOST3410 CA! Must have at least one active CA to run tests!", infoecdsa != null);
        createEndEntity(ECGOST3410_USERNAME, endEntityProfileId, certificateProfileId, infoecdsa.getCAId());
    }
}

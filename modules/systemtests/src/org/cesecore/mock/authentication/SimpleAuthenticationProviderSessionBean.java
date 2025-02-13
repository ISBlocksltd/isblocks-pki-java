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
package org.cesecore.mock.authentication;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.Principal;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Set;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.security.auth.x500.X500Principal;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.operator.OperatorCreationException;
import org.cesecore.authentication.tokens.AuthenticationSubject;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authentication.tokens.InvalidAuthenticationTokenException;
import org.cesecore.jndi.JndiConstants;
import org.cesecore.mock.authentication.tokens.TestX509CertificateAuthenticationToken;
import org.cesecore.mock.authentication.tokens.UsernameAccessMatchValue;

import com.keyfactor.util.CertTools;
import com.keyfactor.util.CryptoProviderTools;
import com.keyfactor.util.crypto.algorithm.AlgorithmConstants;
import com.keyfactor.util.keys.KeyTools;

/**
 * @see SimpleAuthenticationProvider
 * 
 * @version $Id$
 * 
 */
@Stateless(mappedName = JndiConstants.APP_JNDI_PREFIX + "SimpleAuthenticationProviderSessionRemote")
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class SimpleAuthenticationProviderSessionBean implements SimpleAuthenticationProviderSessionRemote, SimpleAuthenticationProviderSessionLocal {

    private static final Logger log = Logger.getLogger(SimpleAuthenticationProviderSessionBean.class);

    private static final long serialVersionUID = -5788194519235705323L;

    static {
        CryptoProviderTools.installBCProviderIfNotAvailable();
        try {
            //Make sure this match value is registered,
            Class.forName(UsernameAccessMatchValue.class.getName());
        } catch (ClassNotFoundException e) {
            //NOPMD
        }
    }

    /**
     * This is the pug of authentication; loves everybody.
     */
    @Override
    public AuthenticationToken authenticate(AuthenticationSubject subject) {

    	// A small check if we have added a "fail" credential to the subject.
    	// If we have we will return null, so we can test authentication failure.
    	Set<?> usercredentials = subject.getCredentials();
    	if ((usercredentials != null) && (usercredentials.size() > 0)) {
    		Object o = usercredentials.iterator().next();
    		if (o instanceof String) {
				String str = (String) o;
				if (StringUtils.equals("fail", str)) {
				    if (log.isDebugEnabled()) {
				        log.debug("Found a 'fail' credential, returning null");
				    }
					return null;
				}
			}
    	}
    	
        X509Certificate certificate = null;
        // If we have a certificate as input, use that, otherwise generate a self signed certificate
        Set<?> inputcreds = subject.getCredentials();
        if (inputcreds != null) {
            for (Object object : inputcreds) {
    			if (object instanceof X509Certificate) {
    				certificate = (X509Certificate) object;
                    if (log.isDebugEnabled()) {
                        log.debug("Found a certificate credential that we will use, fp="+CertTools.getFingerprintAsString(certificate));
                    }
    			}
    		}        	
        }
        
        // If there was no certificate input, create a self signed
        if (certificate == null) {
            if (log.isDebugEnabled()) {
                log.debug("No certificate input, will create a self-signed one");
            }
            String dn = DEFAULT_DN;
            // If we have created a subject with an X500Principal we will use this DN to create the dummy certificate.
            if (subject != null) {
            	Set<Principal> principals = subject.getPrincipals();
            	if ((principals != null) && (principals.size() > 0)) {
            		Principal p = principals.iterator().next();
            		if (p instanceof X500Principal) {
    					X500Principal xp = (X500Principal)p;
    					dn = xp.getName();
    				}
            	}
            }
            KeyPair keys = null;
            try {
                keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
               }  catch (InvalidAlgorithmParameterException e) {
                throw new InvalidAuthenticationTokenException("Could not create authentication token.", e);
            }
            try {
                certificate = CertTools.genSelfCert(dn, 365, null, keys.getPrivate(), keys.getPublic(),
                        AlgorithmConstants.SIGALG_SHA1_WITH_RSA, true);
            } catch (CertificateEncodingException e) {
                throw new IllegalStateException("Error encountered when creating certificate", e);
            } catch (OperatorCreationException e) {
                throw new IllegalStateException("Error encountered when creating certificate", e);
            } catch (CertificateException e) {
                throw new IllegalStateException("Error encountered when creating certificate", e);
            }        	
            if (log.isDebugEnabled()) {
                log.debug("Creates a self signed authentication certificate, fp="+CertTools.getFingerprintAsString(certificate));
            }
        }
        // We cannot use the X509CertificateAuthenticationToken here, since it can only be used internally in a JVM.
        AuthenticationToken result = new TestX509CertificateAuthenticationToken(certificate);
        return result;
    }

}

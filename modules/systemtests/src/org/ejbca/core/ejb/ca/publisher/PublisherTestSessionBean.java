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
package org.ejbca.core.ejb.ca.publisher;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import org.cesecore.jndi.JndiConstants;
import org.ejbca.mock.publisher.MockedThrowAwayRevocationPublisher;

/**
 * @version $Id$
 */
@Stateless(mappedName = JndiConstants.APP_JNDI_PREFIX + "PublisherTestSessionRemote")
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class PublisherTestSessionBean implements PublisherTestSessionRemote {

    @Override
    public int getLastMockedThrowAwayRevocationReason() {
        return MockedThrowAwayRevocationPublisher.getLastTestRevocationReason();
    }

    @Override
    public void setLastMockedThrowAwayRevocationReason(int revocationReason) {
        MockedThrowAwayRevocationPublisher.setLastTestRevocationReason(revocationReason);
    }

}

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

package org.ejbca.ui.cli;

import org.apache.log4j.Logger;
import org.ejbca.ui.cli.infrastructure.command.CommandResult;
import org.ejbca.ui.cli.infrastructure.library.CommandLibrary;

import com.keyfactor.util.CryptoProviderTools;

/**
 * Main entry point for the EJBCA EJB CLI
 * 
 * @version $Id$
 */
public class EjbcaEjbCli {

    private static final Logger log = Logger.getLogger(EjbcaEjbCli.class);

    public static void main(String[] args) {

            if (args.length == 0 || !CommandLibrary.INSTANCE.doesCommandExist(args)) {
                CommandLibrary.INSTANCE.listRootCommands();
            } else {
                CryptoProviderTools.installBCProvider();

                CommandResult result = CommandLibrary.INSTANCE.findAndExecuteCommandFromParameters(args);
                if (result != CommandResult.SUCCESS) {
                    System.exit(result.getReturnCode());
                }

            }
    }
}

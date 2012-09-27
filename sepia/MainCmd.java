// Copyright 2010-2012 Martin Burkhart (martibur@ethz.ch)
//
// This file is part of SEPIA. SEPIA is free software: you can redistribute 
// it and/or modify it under the terms of the GNU Lesser General Public 
// License as published by the Free Software Foundation, either version 3 
// of the License, or (at your option) any later version.
//
// SEPIA is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with SEPIA.  If not, see <http://www.gnu.org/licenses/>.

import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import services.LogFilterNonVerbose;
import services.Services;
import services.Stopper;
import startup.ConfigFile;
import startup.PeerStarter;

/**
 * The main class to start the secret traffic measurements sharing from
 * a command line window.
 * 
 * The Syntax for starting SEPIA input and privacy peers from command line is as follows:
 * 
 * <p/>
 * java -jar sepia.jar [-options]
 * <p/>
 * 
 * The following options are available:
 * <ul>
 * <li>v: Enable verbose logging mode. This creates quite big log files.</li>
 * <li>help: Show usage information.</li>
 * <li>p peerType: Specifies the peer type (0: input peer: 1: privacy peer).</li>
 * <li>c configFile: Path to the configuration file.</li>
 * <li>t network: Perform a basic connection test. (0: standard connection, 1: SSL connection).</li>
 * </ul>
 * Thus, an input peer with verbose logging can be started by the following command:
 * <p/>
 * java -jar sepia.jar -v -p 0 -c MyConfig.properties
 * 
 * @author Lisa Barisic
 */
public class MainCmd {

    private static final String ARG_PEER_TYPE = "-p";
    private static final int PEER_TYPE = 0;
    private static final int PRIVACY_PEER_TYPE = 1;
    private static final String ARG_CONFIG_FILE = "-c";
    private static final String ARG_BE_VERBOSE = "-v";
    private static final String ARG_HELP = "-help";
    private static Logger logger = null;

    /**
     * The main class to start a peer or a privacy peer
     * 
     * @param args arguments
     */
    public static void main(String[] args) {
		String configFile = null;
		String argument = null;
		int peerType = 11111;
		boolean peerTypeDone = false;
		boolean configFileDone = false;
		boolean beVerbose = false;
		boolean argumentsOK = true;
		String logFile;
		int argIndex;

        System.out.println("\n" + Services.getApplicationName() + ": " + Services.getApplicationDescription());
        logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

        System.out.println("\nArguments: ");

        for (String s : args) {
            System.out.println(s);
        }
        System.out.println();

        if (args.length > 0) {
            argIndex = 0;
            while (argIndex < args.length) {
                argument = args[argIndex++];

                if (argument != null) {
                    if (argument.length() > 0 && argument.charAt(0) == '-') {
                        if (argument.equals(ARG_PEER_TYPE)) {
                            try {
                                if (!peerTypeDone) {
                                    if (argIndex < args.length) {
                                        argument = args[argIndex++];
                                        peerType = Integer.parseInt(argument);
                                        peerTypeDone = true;
                                    } else {
                                        System.out.println("Argument missing (peer type)");
                                        argumentsOK = false;
                                    }
                                } else {
                                    System.out.println("Peer type option already set: " + argument);
                                    argumentsOK = false;
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("Unexpected peer type: " + argument);
                                argumentsOK = false;
                            }

                        } else if (argument.equals(ARG_CONFIG_FILE)) {
                            if (!configFileDone) {
                                if (argIndex < args.length) {
                                    argument = args[argIndex++];
                                    configFile = argument;
                                } else {
                                    System.out.println("Argument missing (config file)");
                                    argumentsOK = false;
                                }
                            } else {
                                argumentsOK = false;
                            }
                        } else if (argument.equals(ARG_BE_VERBOSE)) {
                            beVerbose = true;
                        } else if (argument.equals(ARG_HELP)) {
                            printUsage();
                            System.exit(0);
                        } else {
                            System.out.println("Config file option already set: " + argument);
                            argumentsOK = false;
                        }
                    }
                }
            }
        } else {
            System.out.println("\nNo arguments found!");
            argumentsOK = false;
        }

        if (!peerTypeDone) {
            argumentsOK = false;
        }

        if (argumentsOK) {
            logFile = String.valueOf(System.currentTimeMillis());

            if (configFile == null) {
                System.out.println("No config file given... Setting empty properties");
            } else {
                System.out.println("Config file: " + configFile);
                ConfigFile.getInstance().initialize(configFile);
            }

            // Set log filter (if any)
            if (beVerbose) {
                System.out.println("Be verbose when logging...");
            } else {
                System.out.println("Logging in non-verbose mode...");
                logger.setFilter(new LogFilterNonVerbose());
            }


            // Start the peers
            boolean isInputPeer;
            if (peerType == PEER_TYPE) {
                System.out.println("Starting INPUT PEER...\n");
                logFile = "log.peer." + logFile;
                addLogFileHandler(logFile);
                isInputPeer = true;
            } else if (peerType == PRIVACY_PEER_TYPE) {
                System.out.println("Starting PRIVACY PEER...\n");
                logFile = "log.privacypeer." + logFile;
                addLogFileHandler(logFile);
                isInputPeer = false;
            } else {
                System.out.println("Unexpected peer Type: " + peerType);
                printUsage();
                return;
            }            
            
            PeerStarter starter = new PeerStarter(new Stopper(), isInputPeer);
            Thread thread = new Thread(starter);
            thread.start();
        } else {
            printUsage();
        }
    }

    /**
     * Attach a file handler to the log
     * 
     * @param logFile Where to store the log file
     */
    private static void addLogFileHandler(String logFile) {
        FileHandler fileHandler;
        SimpleFormatter formatter;

        try {
            fileHandler = new FileHandler(logFile, true);
            formatter = new SimpleFormatter();
            fileHandler.setFormatter(formatter);
            logger.addHandler(fileHandler);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error when adding log file handler... Run application without it...");
        }
    }

    /**
     * Let the user know how to handle this program
     */
    private static void printUsage() {
        System.out.println("\n\nUsage: java -jar sepia.jar [-options]");
        System.out.println("\nOptions:");
        System.out.println(ARG_BE_VERBOSE + "\t\tBe verbose (Creates large log files!)");
        System.out.println(ARG_HELP + "\t\tShow usage information");
        System.out.println(ARG_PEER_TYPE + " <peerType>\tWhat kind of peer (0 = input peer, 1 = privacy peer)");
        System.out.println(ARG_CONFIG_FILE + " <configFile>\tSpecifies the config file.");
        System.out.println("\nExample:");
        System.out.println("\tjava -jar sepia.jar " + ARG_BE_VERBOSE + " " + ARG_PEER_TYPE + " 0 " + ARG_CONFIG_FILE + " c:\\my.config.properties");

        System.exit(-1);
    }
}

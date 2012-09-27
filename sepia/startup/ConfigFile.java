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


package startup;

import java.io.FileInputStream;
import java.util.Properties;

import mpc.PeerBase;
import services.Utils;

/**
 * This class stores the configuration file properties in a central place.
 * Keys and default values for the basic properties (peers.*, connection.*, mpc.*) can also be found here.
 * ConfigFile is implemented as a singleton, so use {@link #getInstance()} to access it.  
 * @author Martin Burkhart
 */
public class ConfigFile {

    //=========================================================================
    // Properties for both Input Peers and Privacy Peers (prefix peers.*)
    //=========================================================================  
    /** The minimum number of input peers to participate in this round */
    public static final String PROP_MIN_INPUTPEERS = "peers.minpeers";
    public static final String PROP_NUMBER_OF_TIME_SLOTS = "peers.numberoftimeslots";
    public static final String PROP_NUMBER_OF_ITEMS = "peers.numberofitemsintimeslot";
    /** My (unique) peer ID */
    public static final String PROP_MY_PEER_ID = "peers.mypeerid";
    /** The privacy peers that are taking part in this round */
    public static final String PROP_ACTIVE_PRIVACY_PEERS = "peers.activeprivacypeers";

    //=========================================================================
    // Properties for connection setup (prefix connection.*)
    //=========================================================================  
    /** The timeout in milliseconds (e.g. when waiting for connections...) */
    public static final String PROP_TIMEOUT = "connection.timeout";
    public static final String DEFAULT_TIMEOUT = "10000";
    /** property if messages shall be compressed */
    public static final String PROP_CONNECTION_USE_COMPRESSION = "connection.usecompression";
    public static String PROP_KEY_STORE = "connection.keystore";
    public static String PROP_KEY_STORE_PASSWORD = "connection.keystorepassword";
    public static String PROP_KEY_PASSWORD = "connection.keypassword";
    public static String PROP_KEY_STORE_ALIAS = "connection.keystorealias";
    /** The default for using message compression */
    public static final String DEFAULT_USE_COMPRESSION = "false";

    //=========================================================================
    // Properties for basic MPC settings and Privacy Peers (prefix mpc.*)
    //=========================================================================  
    
    /** The type of the mpc peer to use */
    public static final String PROP_MPC_PEER_TYPE = "mpc.peertype";
    /** The class for input peers to be started in the CUSTOM framework */ 
    public static final String PROP_MPC_CUSTOM_PEER_CLASS = "mpc.peerclass";
    /** The class for privacy peers to be started in the CUSTOM framework */ 
    public static final String PROP_MPC_CUSTOM_PRIVACYPEER_CLASS ="mpc.privacypeerclass";
    /** do input verification? */
    public static final String PROP_SKIP_INPUT_VERIFICATION = "mpc.skipinputverification";
	/** The minimum number of privacy peers to participate in this round */
	public static final String PROP_MIN_PRIVACYPEERS = "mpc.minpeers";
	/** The pseudo random generator to use */
	public static final String PROP_PRG = "mpc.prg";
	/** name of the parallel operations count property */
	public static final String PROP_PARALLEL_OPERATIONS_COUNT = "mpc.paralleloperationscount";
	/** The default pseudo-random generator to use if property is missing */
	public static final String DEFAULT_PRG = PeerBase.PRG_LIST[0];
	/** Field order */
	public static final String PROP_FIELD = "mpc.field";
	/** degree of polynomials (t) */
	public static final String PROP_DEGREE = "mpc.degree";
	/** maximum values accepted in input files */
	public static final String PROP_MAXELEMENT = "mpc.maxelement";

	/** The default field size to use */
	public static final String DEFAULT_FIELD = "9223372036854775783";
	public static final String DEFAULT_PARALLEL_OPERATIONS_COUNT = "0"; // run all operations in parallel
	public static final String DEFAULT_MIN_PRIVACYPEERS = "3"; 
    /** The folder holding input files */
    public static final String PROP_INPUT_DIR = "mpc.inputdirectory";
    public static final String DEFAULT_INPUT_DIR = "input";
    /** The folder for output files */
    public static final String PROP_OUTPUT_DIR = "mpc.outputdirectory";
    public static final String DEFAULT_OUTPUT_DIR = "output";
    public static final String PROP_INPUT_TIMEOUT = "mpc.inputtimeout";
    public static final String DEFAULT_INPUT_TIMEOUT = "300";
    
    /**
     * In the multiplication operation, missing shares can lead to inconsistent states.
     * if this property is set to <code>true</code>, missing shares are synchronized. 
     */
    public static final String PROP_SYNCHRONIZE_SHARES = "mpc.synchronizeshares";

	private static ConfigFile instance;
	private Properties properties;
	
	/**
	 * Get an instance of the ConfigFile class.
	 * @return
	 */
	public static ConfigFile getInstance() {
		if (instance==null) {
			instance = new ConfigFile();
		}
			
		return instance;
	}

	/**
	 * Private constructor (singleton pattern).
	 */
	private ConfigFile() {
	}
	
	/**
	 * Gets the properties.
	 * @return the properties
	 */
	public Properties getProperties() {
		return properties;
	}

	/**
	 * Sets the properties.
	 * @param properties the properties
	 */
	public void setProperties(Properties properties) {
		this.properties = properties;
	}

	/**
	 * Loads the properties from a file. If the file cannot be opened, an empty set of properties is created.
	 * @param configFileName the name of the configuration file
	 */
	public void initialize(String configFileName) {
        properties = new Properties();

        if (configFileName != null) {
            try {
				FileInputStream in = new FileInputStream(configFileName);
			    properties.load(in);
				in.close();
            } catch (Exception e) {
                System.out.println("Error when loading properties from: " + configFileName + "(" + Utils.getStackTrace(e) + ") -> setting defaults");
            }
        }
    }
}

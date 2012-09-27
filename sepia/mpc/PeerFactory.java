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

package mpc;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

import services.Stopper;
import startup.ConfigFile;
import connections.ConnectionManager;

/**
 * A factory that returns input and/or privacy peers.
 * 
 * @author Lisa Barisic, ETH Zurich
 */
public class PeerFactory {
    /**
     * Returns a new input/privacy peer.
     * 
     * @param isInputPeer true if this is an input peer
     * @param peerNumber Peer's number. Usage depends on implementation
     * @param cm the connection manager
     * This is needed in case the framework {@link #CUSTOM_FRAMEWORK} is
     * used and class names need to be read from the config files.
     * 
     * @return A MPC instance of the wanted type
     */
    public synchronized static PeerBase getPeerInstance(boolean isInputPeer, int peerNumber,  ConnectionManager cm, Stopper stopper) throws Exception {
    	Properties props = ConfigFile.getInstance().getProperties();
    	if (isInputPeer) {
            	return getPeerInstanceByName(props.getProperty(ConfigFile.PROP_MPC_CUSTOM_PEER_CLASS), peerNumber, cm, stopper);
    	} else {
            	return getPeerInstanceByName(props.getProperty(ConfigFile.PROP_MPC_CUSTOM_PRIVACYPEER_CLASS), peerNumber, cm, stopper);
    	}
    }

    /**
     * Use reflection to instantiate custom class.
     * @param className the name of the class 
     * @param cm the connection manager
     * @param peerNumber the peer number to be handed over to the constructor
     * @param stopper the stopper to be handed over to the constructor
     * @return the MpcPeer instance
     * @throws ClassNotFoundException 
     * @throws NoSuchMethodException 
     * @throws SecurityException 
     * @throws InvocationTargetException 
     * @throws IllegalAccessException 
     * @throws InstantiationException 
     * @throws IllegalArgumentException 
     */
	private static PeerBase getPeerInstanceByName(String className, int peerNumber, ConnectionManager cm, Stopper stopper) throws ClassNotFoundException, SecurityException,
			NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
		Class c = Class.forName(className);
		Constructor con = c.getConstructor(new Class[] { int.class, ConnectionManager.class, Stopper.class });
		PeerBase mpcInstance = (PeerBase) con.newInstance(new Object[] { peerNumber, cm, stopper });
		return mpcInstance;
	}
    
   
   
}

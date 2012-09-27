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

package events;

import java.util.EventObject;

/**
 * General event class for Observer notification.
 *  
 * @author Lisa Barisic, ETH Zurich
 */
public class ChangeEvent extends EventObject {
	private static final long serialVersionUID = -8306116135324708475L;

	private int peerNumber;
    private String connectionDescription;
    private String localAddress;
    private String remoteAddress;
    private String senderPeerID;

    
    /**
     * General event class for Observer notification
     * 
     * @param source The sender of the event
     * @param peerNumber The peer's number
     * @param localAddress Peer's local Address
     * @param remoteAddress Remote address peer is connected to
     */
    public ChangeEvent(Object source, int peerNumber, String localAddress, String remoteAddress) {
        super(source);
        this.peerNumber = peerNumber;
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
        this.connectionDescription = localAddress + "->" + remoteAddress;
    }

    public synchronized int getPeerNumber() {
        return peerNumber;
    }

    public synchronized String getLocalAddress() {
        return localAddress;
    }

    public synchronized String getRemoteAddress() {
        return remoteAddress;
    }

    public synchronized String getConnectionDescription() {
        return connectionDescription;
    }

    public String getSenderPeerID() {
        return senderPeerID;
    }

    public void setSenderPeerID(String peerID) {
        this.senderPeerID = peerID;
    }
}

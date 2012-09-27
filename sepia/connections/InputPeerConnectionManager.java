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

package connections;

import java.util.List;

import javax.net.ssl.SSLContext;

/**
 * Manages all the connections of a single input peer.
 * @author martibur
 */

public class InputPeerConnectionManager extends ConnectionManager {

	/**
	 * See {@link ConnectionManager#ConnectionManager(String, List, SSLContext)}.
	 */
	public InputPeerConnectionManager(String myId, List<PrivacyPeerAddress> privacyPeerAddresses, SSLContext sslContext) {
		super(myId, privacyPeerAddresses, sslContext);
	}

	/**
	 * Establishes connections to all privacy peers that are not currently connected.
	 */
	public void establishConnections() {
		for (PrivacyPeerAddress ppa : privacyPeerAddresses) {
			connectToPrivacyPeer(ppa);
		}
	}
}

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

/**
 * Simple container class for privacy peer information.
 * 
 * @author martibur
 */
public class PrivacyPeerAddress implements Comparable<PrivacyPeerAddress>{
	/**
	 * The ID of the privacy peer.
	 */
	public String id;
	/**
	 * The host of the privacy peer.
	 */
	public String host;
	/**
	 * The server port of the privacy peer.
	 */
	public int serverPort;

	/**
	 * Constructs a PrivacyPeerAddress object.
	 * 
	 * @param id
	 *            the privacy peer ID
	 * @param host
	 *            the privacy peer host
	 * @param serverPort
	 *            the privacy peer server port
	 */
	public PrivacyPeerAddress(String id, String host, int serverPort) {
		this.id = id;
		this.host = host;
		this.serverPort = serverPort;
	}

	@Override
	public String toString() {
		return id+" ("+host+":"+serverPort+")";
	}

	/**
	 * Compares the IDs of the two privacy peer addresses.
	 */
	@Override
	public int compareTo(PrivacyPeerAddress o) {
		return this.id.compareTo(o.id);
	}
}

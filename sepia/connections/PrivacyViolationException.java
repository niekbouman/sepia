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
 * This exception is thrown if not enough input/privacy peers are available to
 * guarantee privacy.
 * @author martibur
 */
public class PrivacyViolationException extends Exception {
	private static final long serialVersionUID = 5667027039563305571L;
	private int activeIPs, activePPs, minIPs, minPPs;
	
	public PrivacyViolationException(int activeIPs, int activePPs, int minIPs, int minPPs) {
		this.activeIPs = activeIPs;
		this.activePPs = activePPs;
		this.minIPs = minIPs;
		this.minPPs = minPPs;
	}
	
	@Override
	public String getMessage() {
		StringBuilder sb = new StringBuilder("Not enough input/privacy peers connected!");
		sb.append("\nactive input peers: "+activeIPs+", required: "+minIPs);
		sb.append("\nactive privacy peers: "+activePPs+", required: "+minPPs);
		return sb.toString();
	}

	/**
	 * Gets the number of active input peers. 
	 * @return number of active input peers
	 */
	public int getActiveIPs() {
		return activeIPs;
	}

	/**
	 * Gets the number of active privacy peers. 
	 * @return number of active privacy peers
	 */
	public int getActivePPs() {
		return activePPs;
	}

	/**
	 * Gets the minimum number of input peers. 
	 * @return minimum number of input peers
	 */
	public int getMinIPs() {
		return minIPs;
	}

	/**
	 * Gets the minimum number of privacy peers. 
	 * @return minimum number of privacy peers
	 */
	public int getMinPPs() {
		return minPPs;
	}
}

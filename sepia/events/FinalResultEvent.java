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

import java.io.Serializable;

import mpc.VectorData;

/**
 * The event when a final result was sent/received.
 *  
 * @author Lisa Barisic, ETH Zurich
 */
public class FinalResultEvent extends ChangeEvent implements Serializable {
	private static final long serialVersionUID = -3332110407293345888L;

	private VectorData finalResult;
    private boolean verificationSuccessful;
    private int numberOfSuccessfulVerifications;
    private boolean isWholeRoundSuccessful;

    public FinalResultEvent(Object source, int userNumber, String localAddress, String remoteAddress, VectorData finalResult) {
        super(source, userNumber, localAddress, remoteAddress);
        this.finalResult = finalResult;
        verificationSuccessful = true;
        isWholeRoundSuccessful = true;
    }

    public synchronized VectorData getFinalResult() {
        return finalResult;
    }

    public void setFinalResult(VectorData finalResult) {
        this.finalResult = finalResult;
    }

    public boolean isVerificationSuccessful() {
        return verificationSuccessful;
    }

    public void setVerificationSuccessful(boolean verificationSuccessful) {
        this.verificationSuccessful = verificationSuccessful;
    }

    public int getNumberOfSuccessfulVerifications() {
        return numberOfSuccessfulVerifications;
    }

    public void setNumberOfSuccessfulVerifications(int numberOfSuccessfulVerifications) {
        this.numberOfSuccessfulVerifications = numberOfSuccessfulVerifications;
    }

    public boolean isWholeRoundSuccessful() {
        return isWholeRoundSuccessful;
    }

    public void setIsWholeRoundSuccessful(boolean isWholeRoundSuccessful) {
        this.isWholeRoundSuccessful = isWholeRoundSuccessful;
    }
	
}

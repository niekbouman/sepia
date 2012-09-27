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

package mpc.protocolPrimitives.operations;


import java.util.Properties;

import startup.ConfigFile;
import mpc.ShamirSharing;
import mpc.protocolPrimitives.Primitives;
import mpc.protocolPrimitives.PrimitivesException;
import mpc.protocolPrimitives.operationStates.MultiplicationState;


/**
 * Multiplication Operation, implemented according to:
 * <p/>
 * <i>R. Gennaro, M. Rabin, and T. Rabin. Simplified VSS and fast-track multiparty computations with applications to 
 * threshold cryptography. In 7th annual ACM symposium on Principles of distributed computing (PODC), 1998.</i>
 *
 * @author Dilip Many, Martin Burkhart
 *
 */
public class Multiplication extends MultiplicationState implements IOperation {
	
	/**
	 * When the evaluation points are interpolated in step 2, it is important
	 * that all privacy peers use the same set of shares. Otherwise, they don't
	 * arrive at the same polynomial representing the secret product. In case a
	 * privacy peer crashed during the message exchange, PP1 might have gotten
	 * its share while PP2 might have a missing share. If this flag is
	 * <code>true</code>, the PPs synchronize missing shares before
	 * interpolation. Note that this slows down the computation. So if you don't
	 * expect failures, set it to <code>false</code>. Note that in a worst case,
	 * an additional failure during the synchronization of missing shares could
	 * still lead to inconsistent configurations. We accept this risk (it's very
	 * unlikely). After all, we can not solve the consensus problem. Here,
	 * asynchronous MPC would be required.
	 */
	protected boolean synchronizeMissingShares = false;
	protected boolean synchronizationDone = false;
	
	protected long[] backupShares;
	
	/**
	 * creates a multiplication sub-operation.
	 *
	 * @param data	array containing the 2 shares to multiply
	 */
	public Multiplication(long[] data) {
		// store initial arguments
		setInitialData(data);
		
		// The synchronization of missing shares is controlled via a property.
		Properties props = ConfigFile.getInstance().getProperties();
		if (props != null) {
			String synchShares = props.getProperty(ConfigFile.PROP_SYNCHRONIZE_SHARES);
			if(synchShares!=null) {
				synchronizeMissingShares = Boolean.parseBoolean(synchShares);
			}
		}
		
		return;
	}


	/**
	 * do the next step of the multiplication operation
	 *
	 * @param primitives the protocol primitives.
	 * @throws PrimitivesException 
	 */
	public void doStep(Primitives primitives) throws PrimitivesException {
		ShamirSharing mpcShamirSharing = primitives.getMpcShamirSharing();
		
		// step1: multiply shares and generate truncation shares
		if(getCurrentStep() == 1) {
			// generate multipliedTruncationShares
			long[] data = getInitialData();
			setSharesForPrivacyPeers( mpcShamirSharing.generateShare( mpcShamirSharing.modMultiply(data[0],data[1]) ) );
			copyOwnShares(primitives.getMyPrivacyPeerIndex());
			return;
		}

		// step2: synchronize missing shares, if required. Then do interpolation.
		if(getCurrentStep() == 2) {
			if (synchronizeMissingShares && !synchronizationDone) {
				// step 2a) Here we misuse the "shares" as a container to transport information about missing shares
				backupShares();
				
				/*
				 * We encode the information about missing shares in an integer.
				 * In the bitwise representation, "1" indicates an available share, "0" a missing share.
				 * Note: For this to work, log2(p)>m must hold! 
				 */
				long[] shares = getSharesFromPrivacyPeers();
				long inventory=0;
				long positionValue=1;
				for(int i=0; i<shares.length; i++) {
					if(shares[i] != ShamirSharing.MISSING_SHARE) {
						inventory += positionValue;
					}
					positionValue = 2*positionValue;
				}
				
				// Send the same information to all privacy peers
				long[] inventories = new long[shares.length];
				for(int i=0; i<shares.length; i++) {
					inventories[i] = inventory;
				}
				setSharesForPrivacyPeers(inventories);
				copyOwnShares(primitives.getMyPrivacyPeerIndex());
				synchronizationDone = true;
			} else {
				// step 2b) 
				long[] shares;
				if(synchronizeMissingShares) {
					shares = backupShares;

					// Compute the intersection of available shares
					// Note: From a disconnected privacy peer, we get an inventory of value ShamirSharing.MISSING_SHARE.
					long[] inventories = getSharesFromPrivacyPeers();
					long aggregateInventory=-1;
					for (int i=0; i<inventories.length; i++) {
						if (inventories[i]!=ShamirSharing.MISSING_SHARE) {
							if (aggregateInventory==-1) {
								aggregateInventory = inventories[i];
							} else {
								aggregateInventory &= inventories[i];
							}
						}
					}
					
					// Now delete the shares that were not received by all PPs.
					long positionValue=1;
					for(int i=0; i<shares.length; i++) {
						if( (aggregateInventory & positionValue) == 0) {
							shares[i] = ShamirSharing.MISSING_SHARE;
						}
						positionValue = 2*positionValue;
					}
				} else {
					shares = getSharesFromPrivacyPeers();
				}
				
				// Finally interpolate the product.
				long[] result = new long[1];
				result[0] = mpcShamirSharing.interpolate( shares, true );
				setFinalResult(result);
			}
			return;
		}

		// step3: Nothing

		return;
	}
	
	protected void backupShares() {
		long[] shares = getSharesFromPrivacyPeers();
		backupShares = new long[shares.length];
		for (int i=0; i<shares.length; i++) {
			backupShares[i] = shares[i];
		}
	}
}

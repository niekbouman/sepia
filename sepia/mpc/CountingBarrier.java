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

/**
 * A barrier implementation that allows to synchronize threads.
 * The barrier releases all threads if (1) the number of waiting
 * threads that called {\link {@link #block()} is greater or equal to a threshold and 
 * (2) the barrier has been opened using {\link {@link #openBarrier()}.
 * 
 * This is, for instance, used to synchronize the various threads started by a privacy peer between two
 * protocol steps. The protocol threads wait on the barrier after finishing the work in the current step. 
 * As soon as the privacy peer is done preparing the next round, it opens the barrier and the threads
 * continue with the next step.  
 * 
 * (Note: The privacy peer can't just wait on the barrier like the other threads do, because it still needs to be
 * able to handle updates. In particular, it could be the case that some threads only enter the barrier after
 * the privacy peer is ready.)
 * 
 * @author Martin Burkhart
 */
public class CountingBarrier
{
	private int waitingThreads;
	private int threshold;
	private boolean barrierOpen;
	
	/**
	 * Creates a new counting barrier.
	 * @param threshold the number of waiting threads needed.
	 */
	public CountingBarrier(int threshold) {
		this.threshold = threshold;
	}
	
	/**
	 * This method releases all waiting threads if the barrier is open and enough threads are waiting (including the
	 * one calling the method). Otherwise, it calls {@link Object#wait()}. 
	 * @throws InterruptedException
	 */
    public synchronized void block() throws InterruptedException
    {
        waitingThreads++;
    	if (barrierOpen && waitingThreads>=threshold) {
    		releaseAll();
    	} else {
    		wait();
    	}
    }
 
	/**
	 * Releases all waiting threads. 
	 * Calls {@link Object#notifyAll()()}. 
	 * @throws InterruptedException
	 */
    private synchronized void releaseAll() throws InterruptedException
    {
        waitingThreads = 0;
        barrierOpen = false;
        notifyAll();
    }
    
    /**
     * Opens the barrier. As soon as enough threads are waiting they are released.
     * @throws InterruptedException
     */
    public synchronized void openBarrier() throws InterruptedException {
    	barrierOpen = true;
    	if (waitingThreads>=threshold) {
    		releaseAll();
    	}
    }
 
    /**
     * Returns the number of threads currently waiting at the barrier.
     * @return the number of waiting threads.
     */
    public synchronized int getNumberOfWaitingThreads() {
    	return waitingThreads;
    }
}

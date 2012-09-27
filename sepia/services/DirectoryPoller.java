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

package services;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * The DirectoryPoller provides methods to get the next input file from a
 * folder. If several files are available, the lexicographically smallest of all
 * new files is returned. If no new files are available, the poller waits until
 * one arrives (or a timeout occurs).
 * 
 * @author Martin Burkhart
 */

public class DirectoryPoller {
	private static final int POLLING_DELAY = 500;
	private long timeout = 300; // 5 minutes
	private Stopper stopper;
	private List<File> processedFiles = new ArrayList<File>();
	private File directory; 
	
	/**
	 * Create a new DirectoryPoller.
	 * 
	 * @param stopper
	 *            a stopper that can be used to stop the poller.
   	 * @param directory
	 *            The directory to monitor.
	 */
	public DirectoryPoller(Stopper stopper, File directory) {
		this.stopper = stopper;
		this.directory = directory;
	}

	
	/**
	 * Gets the current polling timeout.
	 * 
	 * @return timeout in seconds.
	 */
	public long getTimeout() {
		return timeout;
	}

	/**
	 * Sets the polling timeout. Default is 300s (5min.).
	 * 
	 * @param pollingTimeout
	 *            timeout in seconds.
	 */
	public void setTimeout(long pollingTimeout) {
		this.timeout = pollingTimeout;
	}

	/**
	 * Returns the lexicographically smallest new file in the directory. New
	 * files are those that are not passed in the excludedFiles parameter.
	 * 
	 * @return the next file. This can be <code>null</code> if the Stopper was
	 *         stopped or a timeout occured.
	 */
	public File getNextFile() {
		File nextFile = null;
		InputFileFilter filter = new InputFileFilter(processedFiles);

		long start = System.currentTimeMillis();

		while (nextFile == null && !stopper.isStopped()
				&& System.currentTimeMillis() < start + timeout * 1000) {
			for (File file : directory.listFiles(filter)) {
				if (nextFile == null
						|| file.getName().compareTo(nextFile.getName()) < 0) {
					nextFile = file;
				}
			}

			if (nextFile == null) {
				// Didn't find anything. Wait a while
				try {
					Thread.sleep(POLLING_DELAY);
				} catch (InterruptedException e) {
					// Don't care
				}
			}
		}

		if (nextFile!=null) {
			processedFiles.add(nextFile);
		}
		return nextFile;
	}

	/**
	 * A simple file filter that excludes directories and all files passed on a
	 * list.
	 * 
	 * @author Martin Burkhart
	 * 
	 */
	class InputFileFilter implements FileFilter {
		List<File> excludedFiles;

		public InputFileFilter(List<File> excludedFiles) {
			this.excludedFiles = excludedFiles;
		}

		public boolean accept(File pathname) {
			return pathname.isFile() && !excludedFiles.contains(pathname);
		}

	}

}

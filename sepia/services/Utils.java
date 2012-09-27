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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

/**
 * Implements some utility methods.
 * 
 * @author Martin Burkhart
 */
public class Utils {

	/**
	 * Turns the stack trace of an exception into a string. Mainly used for
	 * logging.
	 * 
	 * @param t
	 *            the throwable.
	 * @return the stack trace as a string.
	 */
	public static String getStackTrace(Throwable t) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw, true);
		t.printStackTrace(pw);
		pw.flush();
		sw.flush();
		return sw.toString();
	}

	/**
	 * Returns the current stack trace of all threads. This is useful for
	 * debugging purposes.
	 * 
	 * @return the stack traces as a string.
	 */
	public static String getStackTraceOfAllThreads() {
		StringBuilder sb = new StringBuilder("--- Stack Trace of all threads: ---");
		Map<Thread, StackTraceElement[]> st = Thread.getAllStackTraces();
		for (Map.Entry<Thread, StackTraceElement[]> e : st.entrySet()) {
			StackTraceElement[] el = e.getValue();
			Thread t = e.getKey();
			sb.append("\n\"" + t.getName() + "\"" + " " + (t.isDaemon() ? "daemon" : "") + " prio=" + t.getPriority() + " Thread id=" + t.getId() + " "
					+ t.getState());
			for (StackTraceElement line : el) {
				sb.append("\n\t- " + line);
			}
			sb.append("\n");
		}
		return sb.toString();
	}

}

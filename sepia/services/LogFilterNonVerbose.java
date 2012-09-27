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

import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Non-verbosity filter: Filters out all Info-level messages that do NOT start 
 * with {@link Services#getFilterPassingLogPrefix()}. 
 * <p/>
 * Note: WARNING and SEVERE levels are passed through, no matter what prefix
 */
public class LogFilterNonVerbose implements Filter {

    public boolean isLoggable(LogRecord logRecord) {
        String message;
        boolean recordPassed;

        recordPassed = false;

        message = logRecord.getMessage();
        if (message.startsWith(Services.getFilterPassingLogPrefix())) {
            recordPassed = true;
        } else if (logRecord.getLevel().equals(Level.WARNING)) {
            recordPassed = true;
        } else if (logRecord.getLevel().equals(Level.SEVERE)) {
            recordPassed = true;
        } 
        
        return recordPassed;
    }
}

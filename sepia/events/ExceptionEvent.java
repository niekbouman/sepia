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
 * Event when an exception occurred.
 * 
 * @author Lisa Barisic, ETH Zurich
 */
public class ExceptionEvent extends EventObject {
	private static final long serialVersionUID = 2026801577866197200L;

	private Exception exception;
    private String message;

    public ExceptionEvent(Object source, Exception exception) {
        super(source);
        this.exception = exception;
        if (exception.getMessage() != null) {
            message = exception.getMessage();
        } else {
            message = "";
        }
    }

    public ExceptionEvent(Object source, Exception exception, String message) {
        this(source, exception);
        this.message = message;
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

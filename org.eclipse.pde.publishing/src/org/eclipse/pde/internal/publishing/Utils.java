/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      IBM Corporation - initial API and implementation
 *      SAP AG - consolidation of publishers for PDE formats
 *******************************************************************************/
package org.eclipse.pde.internal.publishing;

import java.io.*;

public final class Utils {

	static public void copy(File source, File destination) throws IOException {
		InputStream in = null;
		OutputStream out = null;
		try {
			in = new BufferedInputStream(new FileInputStream(source));
			out = new BufferedOutputStream(new FileOutputStream(destination));
			final byte[] buffer = new byte[8192];
			while (true) {
				int bytesRead = -1;
				bytesRead = in.read(buffer);
				if (bytesRead == -1)
					break;
				out.write(buffer, 0, bytesRead);
			}
		} finally {
			try {
				if (in != null)
					in.close();
			} finally {
				if (out != null)
					out.close();
			}
		}
	}
}

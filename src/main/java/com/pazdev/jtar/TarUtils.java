/*
 * Copyright 2016 Jonathan Paz <jonathan@pazdev.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pazdev.jtar;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;

/**
 *
 * @author Jonathan Paz jonathan.paz@pazdev.com
 */
class TarUtils {
 	public static String getStringValue(byte[] block, int offset, int length) {
		for (int i = offset; i < length; i++) {
			if (block[i] == 0) {
				length = i;
				break;
			}
		}
		return new String(block, offset, length, StandardCharsets.US_ASCII);
	}
	
	public static void setStringValue(String value, byte[] block, int offset, int length, boolean termReq) throws IOException {
		CharsetEncoder encoder = StandardCharsets.US_ASCII.newEncoder()
				.onUnmappableCharacter(CodingErrorAction.REPORT);
		CharBuffer in = CharBuffer.wrap(value);
		ByteBuffer out = ByteBuffer.allocate(length);
		CoderResult result = encoder.encode(in, out, true);
		if (result.isOverflow()) {
			throw new IOException("The string does not fit in its space");
		} else if (result.isMalformed()) {
			throw new MalformedInputException(result.length());
		} else if (result.isUnmappable()) {
			throw new UnmappableCharacterException(result.length());
		}
		
		if (termReq) {
			try {
				out.put((byte) 0);
			} catch (BufferOverflowException e) {
				throw new IOException("The string does not fit in its space");
			}
		}
		while (out.position() < out.limit()) {
			out.put((byte) 0);
		}
		out.flip();
		out.get(block, offset, length);
	}
	
	private static String getNumberString(byte[] block, int offset, int length) {
		for (int i = offset; i < length; i++) {
			if (block[i] == 0 || block[i] == ' ') {
				length = i;
				break;
			}
		}
		return new String(block, offset, length, StandardCharsets.US_ASCII);
	}
	
	public static int getNumericValue(byte[] block, int offset, int length) {
		String strval = getNumberString(block, offset, length);
		return Integer.parseUnsignedInt(strval, 8);
	}
	
	public static long getLongNumericValue(byte[] block, int offset, int length) {
		String strval = getNumberString(block, offset, length);
		return Long.parseUnsignedLong(strval, 8);
	}
	
	public static void setNumericValue(long value, byte[] block, int offset, int length) {
		if (value < 0) {
			throw new IllegalArgumentException("Negative values are not allowed in TAR headers");
		}
		StringBuilder b = new StringBuilder(Long.toOctalString(value));
		b.append(0);
		while (b.length() < length) {
			b.insert(0, '0');
		}
		CharsetEncoder encoder = StandardCharsets.US_ASCII.newEncoder();
		CharBuffer in = CharBuffer.wrap(b);
		ByteBuffer out = ByteBuffer.allocate(length);
		encoder.encode(in, out, true);
		out.flip();
		out.get(block, offset, length);
	}

	private TarUtils() {
		throw new UnsupportedOperationException("Cannot instantiate object");
	}   
}

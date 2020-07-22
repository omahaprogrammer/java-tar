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
package com.pazdev.tar;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static com.pazdev.tar.TarConstants.MTIME_LENGTH;
import static com.pazdev.tar.TarConstants.MTIME_OFFSET;

/**
 *
 * @author Jonathan Paz jonathan.paz@pazdev.com
 */
class TarUtils {
 	public static String getStringValue(byte[] block, int offset, int length) {
		for (int i = offset; i < offset + length; i++) {
			if (block[i] == 0) {
				length = i - offset;
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
		for (int i = offset; i < offset + length; i++) {
			if (block[i] == 0 || block[i] == ' ') {
				length = i - offset;
				break;
			}
		}
		return new String(block, offset, length, StandardCharsets.US_ASCII);
	}

    private static BigInteger getBase256Number(byte[] block, int offset, int length) {
        byte[] buf = new byte[length];
        System.arraycopy(block, offset, buf, 0, length);
        if ((buf[0] & 0b0100_0000) != 0) {
            buf[0] &= 0b0011_1111;
        }
        return new BigInteger(buf);
    }
	
	public static Integer getNumericValue(byte[] block, int offset, int length) throws TarException {
        byte firstByte = block[offset];
        if ((firstByte & 0b1000_0000) != 0) {
            BigInteger val = getBase256Number(block, offset, length);
            try {
                return val.intValueExact();
            } catch (ArithmeticException e) {
                throw new TarException("Cannot fit value into integer");
            }
        } else {
            String strval = getNumberString(block, offset, length);
            if (strval.isEmpty()) {
                return null;
            }
            return Integer.parseUnsignedInt(strval, 8);
        }
	}

    public static Long getLongNumericValue(byte[] block, int offset, int length) throws TarException {
        byte firstByte = block[offset];
        if ((firstByte & 0b1000_0000) != 0) {
            BigInteger val = getBase256Number(block, offset, length);
            try {
                return val.longValueExact();
            } catch (ArithmeticException e) {
                throw new TarException("Cannot fit value into integer");
            }
        } else {
            String strval = getNumberString(block, offset, length);
            if (strval.isEmpty()) {
                return null;
            }
            return Long.parseUnsignedLong(strval, 8);
        }
    }

    public static FileTime getFileTime(byte[] block, int offset, int length) throws TarException {
        Long time = TarUtils.getLongNumericValue(block, offset, length);
        if (time == null) {
            return null;
        }
        return FileTime.from(time, TimeUnit.SECONDS);
    }

	public static BigInteger getBigNumericValue(byte[] block, int offset, int length) throws TarException {
        byte firstByte = block[offset];
        if ((firstByte & 0b1000_0000) != 0) {
            return getBase256Number(block, offset, length);
        } else {
            String strval = getNumberString(block, offset, length);
            if (strval.isEmpty()) {
                return null;
            }
            return new BigInteger(strval, 8);
        }
	}
	
    public static void setGnuNumericValue(long value, byte[] block, int offset, int length) {
        ByteBuffer buf = ByteBuffer.wrap(block, offset, length);
        if (value < 0) {
            byte[] byteversion = BigInteger.valueOf(value).toByteArray();
            while (length > byteversion.length) {
                buf.put((byte)0xff);
                --length;
            }
            buf.put(byteversion);
        } else {
            String val = Long.toOctalString(value);
            if (val.length() + 1 <= length) {
                setNumericValue(value, block, offset, length);
            } else {
                byte[] byteversion = BigInteger.valueOf(value).toByteArray();
                buf.put((byte)0b1000_0000);
                --length;
                while (length > byteversion.length) {
                    buf.put((byte)0);
                    --length;
                }
                buf.put(byteversion);
            }
        }
    }

    public static FileTime getFileTime(String value) throws TarException {
 	    try {
            BigDecimal decimalSeconds = new BigDecimal(value);
            long seconds = decimalSeconds.toBigInteger().longValueExact();
            BigDecimal fraction = decimalSeconds.subtract(BigDecimal.valueOf(seconds));
            long nanos = fraction.setScale(9).toBigInteger().longValue();
            return FileTime.from(Instant.ofEpochSecond(seconds, nanos));
        } catch (ArithmeticException e) {
 	        throw new TarException("Cannot parse file time");
        }
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

    public static Charset charsetFromString(String c) {
        if (c == null) {
            return null;
        }
        try {
            switch (c) {
                case "ISO-IR 646 1900":
                    return Charset.forName("US-ASCII");
                case "ISO-IR 8859 1 1998":
                   return Charset.forName("ISO-8859-1");
                case "ISO-IR 8859 2 1999":
                   return Charset.forName("ISO-8859-2");
                case "ISO-IR 8859 3 1999":
                   return Charset.forName("ISO-8859-3");
                case "ISO-IR 8859 4 1998":
                   return Charset.forName("ISO-8859-4");
                case "ISO-IR 8859 5 1999":
                   return Charset.forName("ISO-8859-5");
                case "ISO-IR 8859 6 1999":
                   return Charset.forName("ISO-8859-6");
                case "ISO-IR 8859 7 1987":
                   return Charset.forName("ISO-8859-7");
                case "ISO-IR 8859 8 1999":
                   return Charset.forName("ISO-8859-8");
                case "ISO-IR 8859 9 1999":
                   return Charset.forName("ISO-8859-9");
                case "ISO-IR 8859 10 1998":
                   return Charset.forName("ISO-8859-10");
                case "ISO-IR 8859 13 1998":
                   return Charset.forName("ISO-8859-13");
                case "ISO-IR 8859 14 1998":
                   return Charset.forName("ISO-8859-14");
                case "ISO-IR 8859 15 1999":
                   return Charset.forName("ISO-8859-15");
                default:
                    return null;
            }
        } catch (UnsupportedCharsetException | IllegalCharsetNameException e) {
            return null;
        }
    }

    public static String stringFromCharset(Charset c) {
        if (c == null) {
            return null;
        }
        switch (c.name()) {
            case "US-ASCII":
                return "ISO-IR 646 1900";
            case "ISO-8859-1":
                return "ISO-IR 8859 1 1998";
            case "ISO-8859-2":
                return "ISO-IR 8859 2 1999";
            case "ISO-8859-3":
                return "ISO-IR 8859 3 1999";
            case "ISO-8859-4":
                return "ISO-IR 8859 4 1998";
            case "ISO-8859-5":
                return "ISO-IR 8859 5 1999";
            case "ISO-8859-6":
                return "ISO-IR 8859 6 1999";
            case "ISO-8859-7":
                return "ISO-IR 8859 7 1987";
            case "ISO-8859-8":
                return "ISO-IR 8859 8 1999";
            case "ISO-8859-9":
                return "ISO-IR 8859 9 1999";
            case "ISO-8859-10":
                return "ISO-IR 8859 10 1998";
            case "ISO-8859-13":
                return "ISO-IR 8859 13 1998";
            case "ISO-8859-14":
                return "ISO-IR 8859 14 1998";
            case "ISO-8859-15":
                return "ISO-IR 8859 15 1999";
            default:
                return null;
        }
    }

    public static void verifyChecksum(byte[] block) throws TarException {
        int chksum = getNumericValue(block, TarConstants.CHKSUM_OFFSET, TarConstants.CHKSUM_LENGTH);
        int sum = 0;
        for (int i = block.length - 1; i >= 0; --i) {
            if (i >= TarConstants.CHKSUM_OFFSET && i < TarConstants.TYPEFLAG_OFFSET) {
                sum += ' ';
            } else {
                sum += block[i];
            }
        }
        if (chksum != sum) {
            throw new TarException("The checksum did not validate correctly");
        }
    }

    public static String makeRecord(String key, String value) {
        StringBuilder builder = new StringBuilder(" ");
        builder.append(key).append('=').append(value).append('\n');
        int len = builder.length();
        int lenchrct = (int)Math.log10(len) + 1;
        while (lenchrct != (int)Math.log10(lenchrct+len) + 1) {
            lenchrct = (int)Math.log10(lenchrct+len) + 1;
        }
        len += lenchrct;
        builder.insert(0, len);
        return builder.toString();
    }

    public static String getTimestamp(FileTime t) {
        Instant i = t.toInstant();
        return String.format("%d.%09d", i.getEpochSecond(), i.getNano());
    }

	private TarUtils() {
		throw new UnsupportedOperationException("Cannot instantiate object");
	}   
}

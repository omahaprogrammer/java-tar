/*
 * Copyright 2016 Jonathan Paz jonathan.paz@pazdev.com.
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

import static com.pazdev.jtar.TarUtils.*;
import static com.pazdev.jtar.TarConstants.*;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * <p>
 * This class implements an input stream for reading files in the TAR file format.
 * Includes support for old (V7), USTAR, Posix, and GNU TAR formats. As is appropriate
 * for a TAR implementation, this class is designed to read blocks.
 * </p>
 * <p>
 * This class has no support for decompressing a TAR file. It is intended that the
 * TAR file would be decompressed first. An example of this situation would be
 * <pre>new TarInputStream(new GZIPInputStream(new FileInputStream("foo.tar.gz")));</pre>
 * </p>
 * <p>
 * This class is intended to be analogous in functionality to {@link java.util.zip.ZipInputStream}
 * as much as feasible.
 * </p>
 * 
 * @author Jonathan Paz jonathan.paz@pazdev.com
 */
public class TarInputStream extends BufferedInputStream {
    /**
     * The length of the current entry.
     */
    protected long entrylen;

    /**
     * The current position of the current entry.
     */
    protected long entrypos;

    /**
     * The current entry being read.
     */
    protected TarEntry currentEntry;

    private boolean closed;

    private byte[] tmpbuf = new byte[512];

    private TarEntry globalEntry;

    /**
     * Creates a new TAR input stream. The blocksize is defaulted to 10240 as specified
     * in the Posix standard.
     * 
     * @param in The actual input stream
     */
    public TarInputStream(InputStream in) {
        super(in);
    }

    /**
     * Creates a new TAR input stream.
     * 
     * @param in The actual input stream
     * @param blocksize The blocksize in bytes to use to read from the stream
     * @throws IllegalArgumentException If the blocksize is greater than 32256 or
     * is not a multiple of 512
     */
    public TarInputStream(InputStream in, int blocksize) {
        super(in, blocksize);
        if (blocksize > 32256) {
            throw new IllegalArgumentException("blocksize cannot be larger than 32256");
        }
        if ((blocksize % 512) != 0) {
            throw new IllegalArgumentException("blocksize must be a multiple of 512");
        }
    }

    /**
     * Reads the next TAR file entry and positions the stream at the beginning
     * of the entry data.
     * @return the next TAR entry or {@code null} if there are no more entries
     * @throws TarException if a TAR file exception occurred 
     * @throws IOException if an I/O exception occurred
     */
    public TarEntry getNextEntry() throws IOException {
        ensureOpen();
        if (currentEntry != null) {
            closeEntry();
        }
        processEntry();
        return currentEntry;
    }

    /**
     * Closes the current TAR entry and positions the stream for reading the next
     * entry.
     * 
     * @throws TarException if a TAR file exception occurred 
     * @throws IOException if an I/O exception occurred
     */
    public void closeEntry() throws IOException {
        ensureOpen();
        while (read(tmpbuf, 0, tmpbuf.length) != -1) {
            // keep reading
        }
        currentEntry = null;
    }

    /**
     * Returns an estimate of the number of bytes that can be read (or skipped
     * over) in the current TAR entry without blocking by the next invocation of
     * a method for this input stream. This method returns the smaller of the
     * number of bytes remaining in this entry or what would be returned in
     * {@link java.io.BufferedInputStream#available()}.
     * @return an estimate of the number of bytes that can be read (or skipped
     * over) without blocking.
     * @throws IOException  if an I/O exception occurs
     */
    @Override
    public int available() throws IOException {
        int len = (int) Math.min(entrylen - entrypos, (long)Integer.MAX_VALUE);
        int ct = super.available();
        return Math.min(len, ct);
    }

    /**
     * <p>
     * Reads from the current TAR entry into an array of bytes. An attempt is made
     * to read as many as {@code len} bytes, but a smaller number may be read. The
     * actual number of bytes read is returned. If there are no many bytes to read
     * in the current entry, the method will return -1.
     * </p>
     * 
     * <p>
     * If len is not zero, the method blocks until some input is available;
     * otherwise, no bytes are read and 0 is returned.
     * </p>
     * 
     * @param b the buffer into which the data will be read
     * @param off the start offset in destination {@code b}
     * @param len the maximum number of bytes to read
     * @return The actual number of bytes read, or -1 if at the end of the entry
     * @throws NullPointerException if {@code b} is null
     * @throws IndexOutOfBoundsException if either {@code off} or {@code len} is
     * negative or if {@code len} is greater than {@code b.length - off}
     * @throws TarException if a TAR exception occurred.
     * @throws IOException if an I/O exception occurred.
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Skips the given number of bytes in the current TAR entry.
     * 
     * @param n the maximum number of bytes to skip in the current entry
     * @return the actual number of bytes skipped.
     * @throws IllegalArgumentException if {@code n} is negative
     * @throws IOException if an I/O exception occurred
     */
    @Override
    public long skip(long n) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Closes this and the underlying input stream.
     * @throws IOException if an I/O exception occurs.
     */
    @Override
    public void close() throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("The stream is closed");
        }
    }
    private void processEntry() throws IOException {
        byte[] header = readHeader();
        if (header != null) {
            String magic = getStringValue(header, MAGIC_OFFSET, GNU_MAGIC_LENGTH);
            if ("ustar".equals(magic)) {
                String version = getStringValue(header, VERSION_OFFSET, VERSION_LENGTH);
                if ("00".equals(version)) {
                    processPosix(header);
                }
            } else if("ustar  ".equals(magic)) {
                processGnu(header);
            }
        }
    }

    private void processPosix(byte[] header) throws IOException {
        char typeflag = (char) (0x00ff & header[TYPEFLAG_OFFSET]);
        switch (typeflag) {
            case XGLTYPE:
                break;
            case XHDTYPE:
                break;
            default:
                processUstar(header);
        }
    }

    private void processUstar(byte[] header) {

    }

    private void processGnu(byte[] header) throws IOException {

    }


    private byte[] readHeader() throws IOException {
        int readct = 0;
        byte[] header = new byte[512];
        do {
            int ct = super.read(header, readct, 512 - readct);
            if (ct == -1) {
                return null;
            }
            readct += ct;
        } while (readct < 512);
        return header;
    }
}

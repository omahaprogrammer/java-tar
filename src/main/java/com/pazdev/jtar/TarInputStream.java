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

import static java.util.regex.Pattern.*;
import static com.pazdev.jtar.TarUtils.*;
import static com.pazdev.jtar.TarConstants.*;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    protected TarEntry entry;

    private boolean closed;

    private final byte[] blockarray = new byte[512];
    private final ByteBuffer block = ByteBuffer.wrap(blockarray);
    private boolean eof = false;
    private boolean entryeof = false;

    private final byte[] tmpbuf = new byte[512];

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
        if (eof) {
            return null;
        }
        if (entry != null) {
            closeEntry();
        }
        readblock();
        if (isZeroBlock()) {
            readblock();
            if (isZeroBlock()) {
                eof = true;
            }
        }
        processEntry();
        return entry;
    }

    private boolean isZeroBlock() {
        boolean zeroblock = true;
        while (block.hasRemaining()) {
            if (block.get() != 0) {
                zeroblock = false;
                break;
            }
        }
        return zeroblock;
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
        entry = null;
    }

    /**
     * Returns an estimate of the number of bytes that can be read (or skipped
     * over) in the current TAR entry without blocking by the next invocation of
     * a method for this input stream. This method will return 0 if there are no
     * more remaining bytes in this entry.
     * @return an estimate of the number of bytes that can be read (or skipped
     * over) without blocking.
     * @throws IOException  if an I/O exception occurs
     */
    @Override
    public int available() throws IOException {
        ensureOpen();
        int len = (int) Math.min(entrylen - entrypos, (long)Integer.MAX_VALUE);
        int ct = block.remaining();
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
        ensureOpen();
        if (!block.hasRemaining() && !readblock()) {
            return -1;
        }
        len = Math.min(len, block.remaining());
        block.get(b, off, len);
        return len;
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
        ensureOpen();
        long ct = 0;
        while (n > 0) {
            int num = read(tmpbuf, 0, (int) Math.min(n, tmpbuf.length));
            if (num == -1) {
                return ct;
            }
            ct += num;
            n -= num;
        }
        return ct;
    }

    /**
     * Closes this and the underlying input stream.
     * @throws IOException if an I/O exception occurs.
     */
    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            super.close();
        }
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("The stream is closed");
        }
    }

    private void processEntry() throws IOException {
        String magic = getStringValue(blockarray, MAGIC_OFFSET, GNU_MAGIC_LENGTH);
        switch (magic) {
            case "ustar":
                if ("00".equals(getStringValue(blockarray, VERSION_OFFSET, VERSION_LENGTH))) {
                    processPosix();
                }
                break;
            case "ustar  ":
                processGnu();
                break;
            default:
                processV7();
                break;
        }
        if (entry != null) {
            entrylen = entry.getSize();
            entrypos = 0;
        } else {
            entrylen = 0;
        }
    }

    private void processPosix() throws IOException {
        char typeflag = (char) (0x00ff & blockarray[TYPEFLAG_OFFSET]);
        TarEntry extendedHeader = null;
        switch (typeflag) {
            case XGLTYPE:
                globalEntry = tarEntryFromExtendedHeader();
                readblock();
                processEntry();
                return; // This wasn't a normal header, so I need to start over with the processing
            case XHDTYPE:
                extendedHeader = tarEntryFromExtendedHeader();
                readblock();
                break;
                
        }
        processUstar();
        if (extendedHeader != null) {
            entry.mergeEntry(extendedHeader);
        }
    }

    Pattern paramPattern = compile("(\\d*) ([^=]*)=(.*)\n", DOTALL | UNIX_LINES);
    private TarEntry tarEntryFromExtendedHeader() throws IOException {
        TarEntry newEntry = new TarEntry();
        int size = (int) entry.getSize(); // the extended header has no business being bigger than 2GB, so this is safe.
        int blocksToRead = (size + 511) / 512;
        int bytesToRead = blocksToRead * 512;
        int offset = 0;
        byte[] bytes = new byte[bytesToRead];
        
        while (bytesToRead > 0) {
            int ct = super.read(bytes, offset, bytesToRead);
            if (ct == -1) {
                throw new TarException("EOF before finished reading header");
            }
            offset += ct;
            bytesToRead -= ct;
        }
        ByteBuffer exthdr = ByteBuffer.wrap(bytes);
        exthdr.limit(bytesToRead);
        while (exthdr.hasRemaining()) {
            exthdr.mark();
            StringBuilder lenstr = new StringBuilder();
            byte r;
            while ((r = exthdr.get()) != ' ') {
                lenstr.append((char) (0x00ff & r));
            }
            exthdr.rewind();
            int len = Integer.parseInt(lenstr.toString());
            byte[] paramarr = new byte[len];
            exthdr.get(paramarr);
            String param = new String(paramarr, StandardCharsets.UTF_8);
            Matcher m = paramPattern.matcher(param);
            if (m.matches()) {
                String key = m.group(2);
                String value = m.group(3);
                if (value != null && value.isEmpty()) {
                    value = null;
                }
                switch (key) {
                    case "atime":
                        entry.setAtime(value);
                        break;
                    case "charset":
                        entry.setCharset(charsetFromString(value));
                        break;
                    case "comment":
                        entry.setComment(value);
                        break;
                    case "gid":
                        entry.setGid(Integer.parseInt(value));
                        break;
                    case "gname":
                        entry.setGname(value);
                        break;
                    case "linkpath":
                        entry.setLinkname(value);
                        break;
                    case "mtime":
                        entry.setMtime(value);
                        break;
                    case "path":
                        entry.setName(value);
                        break;
                    case "size":
                        entry.setSize(Long.parseLong(value));
                        break;
                    case "uid":
                        entry.setUid(Integer.parseInt(value));
                        break;
                    case "uname":
                        entry.setUname(value);
                        break;
                    default:
                        Map<String, String> extra = entry.getExtraHeaders();
                        if (extra == null) {
                            extra = new HashMap<>();
                        }
                        if (value == null) {
                            extra.remove(key);
                        } else {
                            extra.put(key, value);
                        }
                        if (extra.isEmpty()) {
                            extra = null;
                        }
                        entry.setExtraHeaders(extra);
                        break;
                }
            }
        }
        return null;
    }

    private void processUstar() {

    }

    private void processV7() throws IOException {
    }

    private void processGnu() throws IOException {

    }

    private boolean readblock() throws IOException {
        ensureOpen();
        if (eof || entryeof) {
            return false;
        }
        block.clear();
        int readct = 0;
        do {
            int ct = super.read(blockarray, readct, blockarray.length - readct);
            if (ct == -1) {
                entryeof = true;
                block.limit(0);
                return false;
            }
            readct += ct;
        } while (readct < 512);
        entrypos += 512;
        if (entrypos >= entrylen) {
            entryeof = true;
            block.limit(block.capacity() - (int) (entrypos - entrylen));
        }
        return true;
    }
}

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
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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

    /**
     * The closed status of this stream.
     */
    private boolean closed;

    /**
     * The array representing the current block.
     */
    private final byte[] blockarray = new byte[512];

    /**
     * The byte buffer wrapping the block array. This helps the stream read data
     * from the block.
     */
    private final ByteBuffer block = ByteBuffer.wrap(blockarray);

    /**
     * Whether the end of the file has been reached (two zero-blocks).
     */
    private boolean eof = false;

    /**
     * Whether the end of the entry has been reached.
     */
    private boolean entryeof = false;

    /**
     * A temporary buffer useful for putting in data for reads while doing non-reading
     * things.
     */
    private final byte[] tmpbuf = new byte[512];

    /**
     * The global entry if it has been found.
     */
    private TarEntry globalEntry;

    /**
     * The regex pattern to help read an extended header record.
     */
    private static final Pattern PARAM_PATTERN = compile("(\\d*) ([^=]*)=(.*)\n", DOTALL | UNIX_LINES);

    /**
     * Creates a new TAR input stream. The block size is defaulted to 10240 as
     * specified in the POSIX standard.
     * 
     * @param in The actual input stream
     */
    public TarInputStream(InputStream in) {
        this(in, 10240);
    }

    /**
     * Creates a new TAR input stream.
     * 
     * @param in The actual input stream
     * @param blocksize The block size in bytes to use to read from the stream
     * @throws IllegalArgumentException If the block size is greater than 32256 or
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
     * 
     * @return the next TAR entry or {@code null} if there are no more entries
     * @throws TarException if a TAR file exception occurred 
     * @throws IOException if an I/O error occurred
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
                return null;
            }
        }
        processEntry();
        return entry;
    }

    /**
     * Checks to see if the currently read block contains only zeroes.
     * 
     * @return true if the block is blank.
     */
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
     * Closes the current TAR entry and positions the stream for reading the
     * next entry.
     * 
     * @throws TarException if a TAR file exception occurred 
     * @throws IOException if an I/O error occurred
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
     * over) in the current TAR entry without blocking by the next invocation
     * of a method for this input stream. This method will return 0 if there are
     * no more remaining bytes in this entry.
     * 
     * @return an estimate of the number of bytes that can be read (or skipped
     * over) without blocking.
     * @throws IOException  if an I/O error occurs
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
     * @throws IOException if an I/O error occurred.
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
     * @throws IOException if an I/O error occurred
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
     * @throws IOException if an I/O error occured.
     */
    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            super.close();
        }
    }

    /**
     * Called at the start of every method that performs a read or read-like
     * operation to check if the file is closed.
     * 
     * @throws IOException if the file is closed
     */
    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("The stream is closed");
        }
    }

    /**
     * Processes the new entry (starting at the current block) into a new TarEntry
     * object. This method will move the input stream to the start of the file
     * data if present.
     * 
     * @throws TarException if the entry information is malformed.
     * @throws IOException if an I/O error occurred
     */
    private void processEntry() throws TarException, IOException {
        verifyChecksum(blockarray);
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
            switch (entry.getTypeflag()) {
                case SYMTYPE:
                case DIRTYPE:
                case CHRTYPE:
                case BLKTYPE:
                    entrylen = 0;
                    break;
            }
        } else {
            entrylen = 0;
        }
        entryeof = entrylen <= 0;
        readblock();
    }

    /**
     * Processes the entry as a POSIX (pax) entry. 
     * @throws TarException if the extended header information is flawed
     * @throws IOException if an I/O error occurred
     */
    private void processPosix() throws TarException, IOException {
        char typeflag = (char) (0x00ff & blockarray[TYPEFLAG_OFFSET]);
        TarEntry extendedHeader = null;
        switch (typeflag) {
            case XGLTYPE:
                TarEntry tmp = tarEntryFromExtendedHeader();
                if (globalEntry != null) {
                    globalEntry = globalEntry.mergeEntry(tmp);
                }
                readblock();
                processEntry(); // This wasn't a normal header, so I need to start over with the processing
                return; // no more processing
            case XHDTYPE:
                extendedHeader = tarEntryFromExtendedHeader();
                extendedHeader.applyEntry(globalEntry);
                readblock();
                break;
                
        }
        processUstar();
        entry.setFormat(TarFormat.PAX);
        if (extendedHeader != null) {
            entry.mergeEntry(extendedHeader);
        }
    }

    /**
     * Creates a partial TarEntry object based on the information in the extended
     * header. This TarEntry will be merged into entry created by the proper header
     * to fill in the information missing from the proper header. This also reads
     * the global header entry, and this entry will be used to merge into subsequent
     * POSIX-style entries.
     * 
     * @return a partial TarEntry object for merging later.
     * @throws TarException if the end of the file was hit before the header could be read.
     * @throws IOException if an I/O error occurred
     */
    private TarEntry tarEntryFromExtendedHeader() throws TarException, IOException {
        TarEntry newEntry = new TarEntry();
        int size = entry.getSize().intValue(); // the extended header has no business being bigger than 2GB, so this is safe.
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
            Matcher m = PARAM_PATTERN.matcher(param);
            if (m.matches()) {
                String key = m.group(2);
                String value = m.group(3);
                if (value != null && value.isEmpty()) {
                    value = null;
                }
                switch (key) {
                    case "atime":
                        newEntry.setAtime(value);
                        break;
                    case "charset":
                        newEntry.setCharset(charsetFromString(value));
                        break;
                    case "comment":
                        newEntry.setComment(value);
                        break;
                    case "gid":
                        newEntry.setGid(Integer.parseInt(value));
                        break;
                    case "gname":
                        newEntry.setGname(value);
                        break;
                    case "linkpath":
                        newEntry.setLinkname(value);
                        break;
                    case "mtime":
                        newEntry.setMtime(value);
                        break;
                    case "path":
                        newEntry.setName(value);
                        break;
                    case "size":
                        newEntry.setSize(Long.parseLong(value));
                        break;
                    case "uid":
                        newEntry.setUid(Integer.parseInt(value));
                        break;
                    case "uname":
                        newEntry.setUname(value);
                        break;
                    default:
                        Map<String, String> extra = newEntry.getExtraHeaders();
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
                        newEntry.setExtraHeaders(extra);
                        break;
                }
            }
        }
        return newEntry;
    }

    /**
     * Processes the current block as a USTAR heading. This is also used by
     * {@link #processPosix()} to read the base header block.
     * 
     * @throws TarException if a numeric field is not convertible to a number.
     */
    private void processUstar() throws TarException {
        processV7();
        entry.setMagic(getStringValue(blockarray, MAGIC_OFFSET, MAGIC_LENGTH));
        entry.setVersion(getStringValue(blockarray, VERSION_OFFSET, VERSION_LENGTH));
        entry.setUname(getStringValue(blockarray, UNAME_OFFSET, UNAME_LENGTH));
        entry.setGname(getStringValue(blockarray, GNAME_OFFSET, GNAME_LENGTH));
        entry.setDevmajor(getNumericValue(blockarray, DEVMAJOR_OFFSET, DEVMAJOR_LENGTH));
        entry.setDevminor(getNumericValue(blockarray, DEVMINOR_OFFSET, DEVMINOR_LENGTH));
        String prefix = getStringValue(blockarray, PREFIX_OFFSET, PREFIX_LENGTH);
        if (prefix != null && !prefix.isEmpty()) {
            entry.setName(prefix + "/" + entry.getName());
        }
    }

    private void processV7() throws TarException {
        entry = new TarEntry(getStringValue(blockarray, NAME_OFFSET, NAME_LENGTH));
        entry.setMode(getNumericValue(blockarray, MODE_OFFSET, MODE_LENGTH));
        entry.setUid(getNumericValue(blockarray, UID_OFFSET, UID_LENGTH));
        entry.setGid(getNumericValue(blockarray, GID_OFFSET, GID_LENGTH));
        entry.setSize(getLongNumericValue(blockarray, SIZE_OFFSET, SIZE_LENGTH));
        entry.setMtime(FileTime.from(getLongNumericValue(blockarray, MTIME_OFFSET, MTIME_LENGTH), TimeUnit.SECONDS));
        entry.setChksum(getNumericValue(blockarray, CHKSUM_OFFSET, CHKSUM_LENGTH));
        entry.setTypeflag((char) (0x00ff & blockarray[TYPEFLAG_OFFSET]));
        entry.setLinkname(getStringValue(blockarray, LINKNAME_OFFSET, LINKNAME_LENGTH));
    }

    private String readGnuString() throws TarException, IOException {
        int size = entry.getSize().intValue(); // the long name or link name has no business being bigger than 2GB, so this is safe.
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
        return new String(bytes);
    }

    private void processGnu() throws TarException, IOException {
        char typeflag = (char) (0x00ff & blockarray[TYPEFLAG_OFFSET]);
        boolean atFile;
        String longname = null;
        String longlink = null;
        do {
            atFile = true;
            switch (typeflag) {
                case GNUTYPE_LONGLINK:
                    longlink = readGnuString();
                    readblock();
                    atFile = false;
                    break;
                case GNUTYPE_LONGNAME:
                    longname = readGnuString();
                    readblock();
                    atFile = false;
                    break;
            }
        } while (!atFile);
        processV7();
        if (longname != null) {
            entry.setName(longname);
        }
        if (longlink != null) {
            entry.setLinkname(longlink);
        }
        entry.setMagic(getStringValue(blockarray, MAGIC_OFFSET, GNU_MAGIC_LENGTH));
        entry.setUname(getStringValue(blockarray, UNAME_OFFSET, UNAME_LENGTH));
        entry.setGname(getStringValue(blockarray, GNAME_OFFSET, GNAME_LENGTH));
        entry.setDevmajor(getNumericValue(blockarray, DEVMAJOR_OFFSET, DEVMAJOR_LENGTH));
        entry.setDevminor(getNumericValue(blockarray, DEVMINOR_OFFSET, DEVMINOR_LENGTH));
        entry.setAtime(FileTime.from(getLongNumericValue(blockarray, GNU_ATIME_OFFSET, GNU_ATIME_LENGTH), TimeUnit.SECONDS));
        entry.setCtime(FileTime.from(getLongNumericValue(blockarray, GNU_CTIME_OFFSET, GNU_CTIME_LENGTH), TimeUnit.SECONDS));
    }

    private boolean readblock() throws IOException {
        ensureOpen();
        if (eof || (entry != null && entryeof)) {
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
        if (entry != null) {
            entrypos += 512;
            if (entrypos >= entrylen) {
                entryeof = true;
                block.limit(block.capacity() - (int) (entrypos - entrylen));
            }
        }
        return true;
    }
}

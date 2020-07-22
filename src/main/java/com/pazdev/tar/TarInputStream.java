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
package com.pazdev.tar;

import static com.pazdev.tar.TarConstants.*;
import static java.util.regex.Pattern.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

/**
 * <p>
 *     This class implements an input stream for reading files in the TAR file format.
 *     Includes support for old (V7), USTAR, Posix, GNU, and Schily TAR formats. As is appropriate
 *     for a TAR implementation, this class is designed to read blocks.
 * </p>
 * <p>
 *     This class has no support for decompressing a TAR file. It is intended that the
 *     TAR file would be decompressed first. An example of this situation would be
 *     <pre>new TarInputStream(new GZIPInputStream(new FileInputStream("foo.tar.gz")));</pre>
 * </p>
 * <p>
 *     GNU TAR archives support sparse headers (including apparently supporting a variant Schily header with sparse
 *     headers). This implementation fills in sparse holes with zeroes so they are transparent to the user. Old GNU
 *     format and PAX format versions 0.1 and 1.0 are supported. PAX format 0.0 is not supported.
 * </p>
 * <p>
 *     This class is intended to be analogous in functionality to {@link java.util.zip.ZipInputStream}
 *     as much as feasible.
 * </p>
 * 
 * @author Jonathan Paz jonathan.paz@pazdev.com
 */
public class TarInputStream extends BufferedInputStream {
    private TarEntry entry;
    private final Map<String, String> globalHeader = new HashMap<>();
    private boolean closed;
    private final byte[] tmpbuf = new byte[BLOCKSIZE];
    private boolean entryEOF;
    private boolean archiveEOF;
    private long offset;
    private long remainingData;
    private long remainingRecord;
    private Deque<SparseHole> sparseHoles;

    /**
     * The regex pattern to help read an extended header record.
     */
    private static final Pattern PARAM_PATTERN = compile("(\\d*) ([^=]*)=(.*)\n", DOTALL);

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
     * @param bufferSize The block size in bytes to use to read from the stream
     * @throws IllegalArgumentException If the block size is greater than 32256 or
     * is not a multiple of 512
     */
    public TarInputStream(InputStream in, int bufferSize) {
        super(in, bufferSize);
        if (bufferSize > 32256) {
            throw new IllegalArgumentException("buffer size cannot be larger than 32256");
        }
        if ((bufferSize % 512) != 0) {
            throw new IllegalArgumentException("buffer size must be a multiple of 512");
        }
    }

    public synchronized TarEntry getNextEntry() throws IOException {
        ensureOpen();
        if (archiveEOF) {
            return null;
        }
        if (entry != null) {
            closeEntry();
        }
        entry = readEntry();
        if (entry == null) {
            return null;
        }
        long size = entry.getSize();
        long records = (size + 511) / 512;
        switch (entry.getTypeflag()) {
            case TarConstants.LNKTYPE:
            case TarConstants.SYMTYPE:
            case TarConstants.DIRTYPE:
            case TarConstants.CHRTYPE:
            case TarConstants.BLKTYPE:
            case TarConstants.SCHILY_INODE:
                offset = 0;
                remainingData = 0;
                remainingRecord = 0;
                entryEOF = true;
                break;
            default:
                offset = 0;
                remainingData = size;
                remainingRecord = records * 512;
                entryEOF = false;
                break;
        }
        return entry;
    }

    public synchronized void closeEntry() throws IOException {
        super.markpos = -1;
        while (remainingData > 0) {
            skip(remainingData);
        }
        while (remainingRecord > 0) {
            long ct = super.skip(remainingRecord);
            remainingRecord -= ct;
        }
        entry = null;
    }

    @Override
    public synchronized long skip(long n) throws IOException {
        if (entryEOF) {
            return 0;
        }
        n = Long.min(n, remainingData);
        long ct = super.skip(n);
        remainingData -= ct;
        remainingRecord -= ct;
        if (remainingData == 0) {
            entryEOF = true;
        }
        return ct;
    }

    @Override
    public synchronized int available() throws IOException {
        ensureOpen();
        int remaining = (int) Long.min(Integer.MAX_VALUE, remainingData);
        return Integer.min(super.available(), remaining);
    }

    @Override
    public synchronized int read() throws IOException {
        ensureOpen();
        if (!sparseHoles.isEmpty() && sparseHoles.peek().offset == offset) {
            --sparseHoles.peek().size;
            return 0;
        } else if (remainingData > 0) {
            int b = super.read();
            ++offset;
            --remainingData;
            --remainingRecord;
            return b;
        } else {
            return -1;
        }
    }

    @Override
    public synchronized int read(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        if (remainingData > 0) {
            int remaining = (int) Long.min(remainingData, Integer.MAX_VALUE);
            len = Integer.min(len, remaining);
            int ct = super.read(b, off, len);
            remainingData -= ct;
            remainingRecord -= ct;
            return ct;
        } else {
            return -1;
        }
    }

    @Override
    public synchronized void reset() throws IOException {
        ensureOpen();
        if (super.markpos > 0) {
            int rewoundCount = super.pos - super.markpos;
            super.reset();
            remainingData += rewoundCount;
            remainingRecord += rewoundCount;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (!closed) {
            super.close();
            closed = true;
        }
    }

    private synchronized void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Stream is closed");
        }
    }

    private synchronized TarEntry readEntry() throws IOException {
        try {
            fill(tmpbuf, 0, BLOCKSIZE);
            if (isBlockZeroFilled(tmpbuf, 0, BLOCKSIZE)) {
                fill(tmpbuf, 0, BLOCKSIZE);
                if (isBlockZeroFilled(tmpbuf, 0, BLOCKSIZE)) {
                    archiveEOF = true;
                    return null;
                }
            }
        } catch (EOFException e) {
            archiveEOF = true;
            return null;
        }
        String magic = TarUtils.getStringValue(tmpbuf, MAGIC_OFFSET, GNU_MAGIC_LENGTH);
        if (GNU_MAGIC.equals(magic)) {
            return readGnuEntry();
        } else if (USTAR_MAGIC.equals(magic)) {
            String version = TarUtils.getStringValue(tmpbuf, VERSION_OFFSET, VERSION_LENGTH);
            if (USTAR_VERSION.equals(version)) {
                if (isSchily(tmpbuf)) {
                    return readSchilyEntry();
                } else {
                    return readUstarEntry();
                }
            } else {
                return readV7Entry();
            }
        } else {
            return readV7Entry();
        }
    }

    private TarEntry readSchilyEntry() {
        return null;
    }

    private boolean isSchily(byte[] block) {
        String xmagic = TarUtils.getStringValue(block, SCHILY_XMAGIC_OFFSET, SCHILY_XMAGIC_LENGTH);
        if (SCHILY_XMAGIC.equals(xmagic)) {
            return true;
        }
        byte atime0 = block[SCHILY_ATIME_OFFSET];
        byte ctime0 = block[SCHILY_CTIME_OFFSET];
        return block[PREFIX_OFFSET + 130] == ' '
                && (atime0 >= '0' && atime0 <= '7')
                && (ctime0 >= '0' && ctime0 <= '7')
                && block[SCHILY_ATIME_OFFSET + 11] == ' '
                && block[SCHILY_CTIME_OFFSET + 11] == ' ';
    }

    private TarEntry readV7Entry() throws TarException {
        TarUtils.verifyChecksum(tmpbuf);
        TarEntry entry = new TarEntry();
        entry.setName(TarUtils.getStringValue(tmpbuf, NAME_OFFSET, NAME_LENGTH));
        entry.setMode(TarUtils.getNumericValue(tmpbuf, MODE_OFFSET, MODE_LENGTH));
        entry.setUid(TarUtils.getNumericValue(tmpbuf, UID_OFFSET, UID_LENGTH));
        entry.setGid(TarUtils.getNumericValue(tmpbuf, GID_OFFSET, GID_LENGTH));
        entry.setSize(TarUtils.getLongNumericValue(tmpbuf, SIZE_OFFSET, SIZE_LENGTH));
        entry.setMtime(TarUtils.getFileTime(tmpbuf, MTIME_OFFSET, MTIME_LENGTH));
        entry.setChksum(TarUtils.getNumericValue(tmpbuf, CHKSUM_OFFSET, CHKSUM_LENGTH));
        entry.setTypeflag((char) (0x00ff & tmpbuf[TYPEFLAG_OFFSET]));
        entry.setLinkname(TarUtils.getStringValue(tmpbuf, LINKNAME_OFFSET, LINKNAME_LENGTH));
        entry.setFormat(TarFormat.V7);
        return entry;
    }

    private TarEntry readUstarEntry() throws IOException {
        char typeflag = (char) (0x00ff & tmpbuf[TYPEFLAG_OFFSET]);
        if (typeflag == 'x') {
            Map<String, String> extended = readPaxData();
            globalHeader.forEach(extended::putIfAbsent);
            TarEntry entry = readEntry();
            if (entry == null) {
                return null;
            }
            for (Iterator<Map.Entry<String, String>> it = extended.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, String> e = it.next();
                String k = e.getKey();
                String v = e.getValue();
                if (v == null || v.isEmpty()) {
                    continue;
                }
                switch (k) {
                    case PAX_ATIME:
                        entry.setAtime(TarUtils.getFileTime(v));
                        it.remove();
                        break;
                    case PAX_CHARSET:
                        entry.setCharset(TarUtils.charsetFromString(v));
                        // keep the value in the hashtable in case we can't identify it
                        break;
                    case PAX_COMMENT:
                        entry.setComment(v);
                        it.remove();
                        break;
                    case PAX_CTIME:
                        entry.setCtime(TarUtils.getFileTime(v));
                        it.remove();
                        break;
                    case PAX_GID:
                        entry.setGid(Integer.parseInt(v));
                        it.remove();
                        break;
                    case PAX_GNAME:
                        entry.setGname(v);
                        it.remove();
                        break;
                    case PAX_LINKPATH:
                        entry.setLinkname(v);
                        it.remove();
                        break;
                    case PAX_MTIME:
                        entry.setMtime(TarUtils.getFileTime(v));
                        it.remove();
                        break;
                    case PAX_PATH:
                        entry.setName(v);
                        it.remove();
                        break;
                    case PAX_SIZE:
                        entry.setSize(Long.parseLong(v));
                        it.remove();
                        break;
                    case PAX_UID:
                        entry.setUid(Integer.parseInt(v));
                        it.remove();
                        break;
                    case PAX_UNAME:
                        entry.setUname(v);
                        it.remove();
                        break;
                    default:
                        // keep the item in the hash table;
                        break;
                }
            }
            entry.getExtraHeaders().putAll(extended);
            return entry;
        } else {
            TarEntry entry = readV7Entry();
            entry.setMagic(TarUtils.getStringValue(tmpbuf, MAGIC_OFFSET, MAGIC_LENGTH));
            entry.setVersion(TarUtils.getStringValue(tmpbuf, VERSION_OFFSET, VERSION_LENGTH));
            entry.setUname(TarUtils.getStringValue(tmpbuf, UNAME_OFFSET, UNAME_LENGTH));
            entry.setGname(TarUtils.getStringValue(tmpbuf, GNAME_OFFSET, GNAME_LENGTH));
            entry.setDevmajor(TarUtils.getNumericValue(tmpbuf, DEVMAJOR_OFFSET, DEVMAJOR_LENGTH));
            entry.setDevminor(TarUtils.getNumericValue(tmpbuf, DEVMINOR_OFFSET, DEVMINOR_LENGTH));
            String prefix = TarUtils.getStringValue(tmpbuf, PREFIX_OFFSET, PREFIX_LENGTH);
            if (!prefix.isEmpty()) {
                entry.setName(prefix + "/" + entry.getName());
            }
            entry.setFormat(TarFormat.PAX);
            return entry;
        }
    }

    private TarEntry readGnuEntry() throws TarException {
        entry.setMagic(TarUtils.getStringValue(tmpbuf, MAGIC_OFFSET, GNU_MAGIC_LENGTH));
        entry.setUname(TarUtils.getStringValue(tmpbuf, UNAME_OFFSET, UNAME_LENGTH));
        entry.setGname(TarUtils.getStringValue(tmpbuf, GNAME_OFFSET, GNAME_LENGTH));
        entry.setDevmajor(TarUtils.getNumericValue(tmpbuf, DEVMAJOR_OFFSET, DEVMAJOR_LENGTH));
        entry.setDevminor(TarUtils.getNumericValue(tmpbuf, DEVMINOR_OFFSET, DEVMINOR_LENGTH));
        entry.setAtime(TarUtils.getFileTime(tmpbuf, GNU_ATIME_OFFSET, GNU_ATIME_LENGTH));
        entry.setCtime(TarUtils.getFileTime(tmpbuf, GNU_CTIME_OFFSET, GNU_CTIME_LENGTH));
        return entry;
    }

    private Map<String, String> readPaxData() throws IOException {
        Map<String, String> retval = new HashMap<>();
        Integer size = TarUtils.getNumericValue(tmpbuf, SIZE_OFFSET, SIZE_LENGTH);
        if (size == null) {
            throw new TarException("Unknown size on pax extended header");
        }
        int records = (size + 511) / 512;
        int len = records * 512;
        byte[] data = new byte[len];
        fill(data, 0, len);
        int i = 0;
        while (i < len) {
            int reclen = 0;
            for (int j = i; j < len; j++) {
                if (data[j] == ' ' || data[j] == 0) {
                    break;
                }
                int digit = data[j] - '0';
                reclen = (reclen * 10) + digit;
            }
            if (reclen == 0) {
                break;
            }
            String record = new String(data, i, reclen, StandardCharsets.UTF_8);
            i += reclen;
            Matcher matcher = PARAM_PATTERN.matcher(record);
            if (!matcher.matches()) {
                throw new TarException("Unknown header format");
            }
            String key = matcher.group(2);
            String value = matcher.group(3);
            retval.put(key, value);
        }
        return Collections.unmodifiableMap(retval);
    }

    private void readPax10SparseHoles() throws IOException {
        boolean finished = false;
        StringBuilder holeRecords = new StringBuilder();
        while (!finished) {
            int len = BLOCKSIZE;
            int off = 0;
            while (len > 0) {
                int ct = this.read(tmpbuf, off, len);
                len -= ct;
                off += ct;
            }
            off = 0;
            len = BLOCKSIZE;
            for (int i = 0; i < len; ++i) {
                if (tmpbuf[i] == 0) {
                    len = i;
                    break;
                }
            }
            String part = new String(tmpbuf, off, len, StandardCharsets.US_ASCII);
            holeRecords.append(part);
            if (len < BLOCKSIZE || getSparseHoles(holeRecords)) {
                finished = true;
            }
        }
    }

    private boolean getSparseHoles(CharSequence holeRecords) {
        long[] retval = null;
        String[] records = holeRecords.toString().split("\\n");
        int recCt = Integer.parseInt(records[0]);
        if (records.length == recCt + 1) {
            int i = 1;
            while (i < records.length) {
                String offset = records[i++];
                String length = records[i++];
                sparseHoles.add(new SparseHole(offset, length));
            }
            return true;
        }
        return false;
    }

    private synchronized void fill(byte[] block, int off, int len) throws IOException {
        ensureOpen();
        while (len > 0) {
            int r = super.read(block, off, len);
            if (r == -1) {
                throw new EOFException();
            }
            off += r;
            len -= r;
        }
    }

    private boolean isBlockZeroFilled(byte[] block, int off, int len) {
        for (int i = off; i < len; ++i) {
            if (block[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static final class SparseHole {
        long offset;
        long size;
        public SparseHole(String offset, String size) {
            this.offset = Long.parseLong(offset);
            this.size = Long.parseLong(size);
        }
    }
}

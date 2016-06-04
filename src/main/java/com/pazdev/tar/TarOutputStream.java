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

import static com.pazdev.tar.TarConstants.*;
import static com.pazdev.tar.TarUtils.*;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author Jonathan Paz <jonathan@pazdev.com>
 */
public class TarOutputStream extends BufferedOutputStream {
    private static final byte[] BLANK = new byte[512];

    /**
     * If this stream has been closed.
     */
    private boolean closed = false;

    /**
     * The current entry being written.
     */
    private TarEntry entry;

    /**
     * The length of the file in bytes.
     */
    private long size;

    /**
     * The number of bytes that have been written for the file.
     */
    private long written;

    /**
     * The number of bytes that must be written for the file. This includes bytes
     * to fill a full record.
     */
    private long bytesToWrite;

    /**
     * Creates a new TAR output stream. The block size is defaulted to 10240 as
     * specified in the POSIX standard.
     * 
     * @param out the actual output stream
     */
    public TarOutputStream(OutputStream out) {
        this(out, 10240);
    }

    /**
     * Creates a new TAR output stream.
     * 
     * @param out the actual output stream
     * @param blocksize The block size in bytes to use to read from the stream
     * @throws IllegalArgumentException If the block size is greater than 32256 or
     * is not a multiple of 512
     */
    public TarOutputStream(OutputStream out, int blocksize) {
        super(out, blocksize);
        if (blocksize > 32256) {
            throw new IllegalArgumentException("blocksize cannot be larger than 32256");
        }
        if ((blocksize % 512) != 0) {
            throw new IllegalArgumentException("blocksize must be a multiple of 512");
        }
    }
    
    /**
     * Begins writing a new TAR file entry and positions the stream start the entry
     * data. Closes the current entry if still active.
     * 
     * @param entry the TAR entry to be written
     * @throws TarFormatException if the TAR entry is malformed
     * @throws IOException if an I/O error occurs
     */
    public void putNewEntry(TarEntry entry) throws TarFormatException, IOException {
        Objects.requireNonNull(entry);
        ensureOpen();
        if (entry != null) {
            closeEntry();
        }
        checkEntry(entry);
        this.entry = new TarEntry(entry);
        written = 0;
        size = this.entry.getSize();
        switch (this.entry.getTypeflag()) {
            case CHRTYPE:
            case BLKTYPE:
            case DIRTYPE:
            case FIFOTYPE:
                size = 0;
                break;
        }
        writeEntry();
    }

    /**
     * Closes the current TAR entry and positions the stream for writing the next
     * entry.
     * 
     * @throws IOException if an I/O error occurs
     */
    public void closeEntry() throws IOException {
        if (entry != null) {
            while (bytesToWrite > 0) {
                int ct = (int)Math.min(bytesToWrite, Integer.MAX_VALUE);
                super.write(new byte[ct]);
                bytesToWrite -= ct;
            }
        }
        entry = null;
        written = 0;
        size = 0;
        bytesToWrite = 0;
    }

    /**
     * Writes an array of bytes to the current ZIP entry data. This method will
     * block until all the bytes are written.
     * 
     * @param b the data to be written
     * @param off the start offset in the data
     * @param len the number of bytes to write from the data
     * @throws TarException if no entry is present or the data to write is larger
     * than the specified size
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void write(byte[] b, int off, int len) throws TarException, IOException {
        ensureOpen();
        if (entry == null) {
            throw new TarException("No entry is present");
        }
        if (written + len > size) {
            throw new TarException("This write creates a file that is larger than the entry provides");
        }
        written += len;
        bytesToWrite -= len;
        super.write(b, off, len);
    }

    /**
     * Closes the TAR output stream as well as the underlying stream. The TAR file
     * is properly closed with two blank blocks.
     * 
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            super.write(BLANK);
            super.write(BLANK);
            super.close();
        }
    }

    /**
     * Checks if the stream is still open, and throws an IOException if it's closed.
     * This is useful for the write-equivalent operations in this class.
     * @throws IOException 
     */
    private void ensureOpen() throws IOException {
        if (closed) throw new IOException("Cannot perform I/O operations on a closed stream");
    }

    private String[] splitName() {
        if (entry.getName().length() < 100) {
            return new String[] {entry.getName()};
        } else {
            String[] parts = entry.getName().split("/");
            int ct = parts.length;
            int idx = ct - 1;
            while (idx > 0) {
                StringBuilder pb = new StringBuilder();
                StringBuilder nb = new StringBuilder();
                String prefix, name;
                for (int i = 0; i < ct; i++) {
                    if (i < idx) {
                        pb.append('/').append(parts[i]);
                    } else {
                        nb.append('/').append(parts[i]);
                    }
                }
                pb.deleteCharAt(0);
                nb.deleteCharAt(0);
                prefix = pb.toString();
                name = nb.toString();
                if (prefix.length() <= 155 && name.length() <= 100) {
                    return new String[] {name, prefix};
                } else if (prefix.length() > 155 && name.length() > 100) {
                    return null;
                }
                --idx;
            }
            return null;
        }
    }

    /**
     * Checks the given entry to verify that it will produce a valid TAR header.
     * These checks are heavily dependent on the specified entry format.
     * 
     * @param entry the entry to check
     * @throws TarException if the entry is malformed.
     */
    private void checkEntry(TarEntry entry) throws TarFormatException {
        switch (entry.getFormat()) {
            case V7:
                if (entry.getName().length() > 99) {
                    throw new TarFormatException("Name cannot be longer than 99 characters");
                }
                switch (entry.getTypeflag()) {
                    case REGTYPE:
                    case AREGTYPE:
                    case LNKTYPE:
                    case SYMTYPE:
                        // we're good
                        break;
                    default:
                        throw new TarFormatException("Unsupported typeflag");
                }
                if (entry.getTypeflag() == LNKTYPE && entry.getSize() != 0) {
                    throw new TarFormatException("The size for a hard link must be zero for this format");
                }
                if (entry.getUid() > 07777777) {
                    throw new TarFormatException("uid too large");
                }
                if (entry.getGid() > 07777777) {
                    throw new TarFormatException("gid too large");
                }
                if (entry.getSize() > 077777777777l) {
                    throw new TarFormatException("size too large");
                }
                if (entry.getMtime().to(TimeUnit.SECONDS) > 077777777777l) {
                    throw new TarFormatException("mtime too large");
                }
                if (entry.getLinkname().length() > 99) {
                    throw new TarFormatException("linkname too large");
                }
                break;
            case USTAR:
                if (splitName() == null) {
                    throw new TarFormatException("The name is too long and can't be split to fit");
                }
                if (entry.getTypeflag() == LNKTYPE && entry.getSize() != 0) {
                    throw new TarFormatException("The size for a hard link must be zero for this format");
                }
                if (entry.getUid() > 07777777) {
                    throw new TarFormatException("uid too large");
                }
                if (entry.getGid() > 07777777) {
                    throw new TarFormatException("gid too large");
                }
                if (entry.getSize() > 077777777777l) {
                    throw new TarFormatException("size too large");
                }
                if (entry.getMtime().to(TimeUnit.SECONDS) > 077777777777l) {
                    throw new TarFormatException("mtime too large");
                }
                if (entry.getLinkname().length() > 100) {
                    throw new TarFormatException("linkname too large");
                }
                if (entry.getUname().length() > 32) {
                    throw new TarFormatException("uname too large");
                }
                if (entry.getGname().length() > 32) {
                    throw new TarFormatException("gname too large");
                }
                if (entry.getDevmajor() > 07777777) {
                    throw new TarFormatException("devmajor too large");
                }
                if (entry.getDevminor() > 07777777) {
                    throw new TarFormatException("devminor too large");
                }
                break;
            case GNU:
                if (entry.getTypeflag() == LNKTYPE && entry.getSize() != 0) {
                    throw new TarFormatException("The size for a hard link must be zero for this format");
                }
                break;
            default:
                if (entry.getDevmajor() > 07777777) {
                    throw new TarFormatException("devmajor too large");
                }
                if (entry.getDevminor() > 07777777) {
                    throw new TarFormatException("devminor too large");
                }
                break;
        }
        
    }

    private void writeEntry() throws IOException {
        switch (entry.getFormat()) {
            case GNU:
                writeGNU();
                break;
            case PAX:
                writePAX();
                break;
            case USTAR:
                writeUSTAR();
                break;
            case V7:
                writeV7();
                break;
        }
    }

    private byte[] populateV7() throws IOException {
        byte[] block = new byte[512];
        String[] name = splitName();
        setStringValue(name[0], block, NAME_OFFSET, NAME_LENGTH, entry.getFormat().equals(TarFormat.V7));
        setNumericValue(entry.getMode(), block, MODE_OFFSET, MODE_LENGTH);
        setNumericValue(entry.getUid(), block, UID_OFFSET, UID_LENGTH);
        setNumericValue(entry.getGid(), block, GID_OFFSET, GID_LENGTH);
        setNumericValue(entry.getSize(), block, SIZE_OFFSET, SIZE_LENGTH);
        setNumericValue(entry.getMtime().to(TimeUnit.SECONDS), block, MTIME_OFFSET, MTIME_LENGTH);
        setStringValue("        ", block, CHKSUM_OFFSET, CHKSUM_LENGTH, false);
        block[TYPEFLAG_OFFSET] = (byte)entry.getTypeflag();
        setStringValue(entry.getLinkname(), block, LINKNAME_OFFSET, LINKNAME_LENGTH, false);
        if (name.length > 1) {
            setStringValue(name[1], block, PREFIX_OFFSET, PREFIX_LENGTH, false);
        }
        return block;
    }

    private byte[] populateUSTAR() throws IOException {
        byte[] block = populateV7();
        setStringValue(entry.getMagic(), block, MAGIC_OFFSET, MAGIC_LENGTH, true);
        setStringValue(entry.getVersion(), block, VERSION_OFFSET, VERSION_LENGTH, false);
        setStringValue(entry.getUname(), block, UNAME_OFFSET, UNAME_LENGTH, true);
        setStringValue(entry.getGname(), block, GNAME_OFFSET, GNAME_LENGTH, true);
        setNumericValue(entry.getDevmajor(), block, DEVMAJOR_OFFSET, DEVMAJOR_LENGTH);
        setNumericValue(entry.getDevminor(), block, DEVMINOR_OFFSET, DEVMINOR_LENGTH);
        return block;
    }

    private void writeV7() throws IOException {
        byte[] block = populateV7();
        setChksum(block);
        super.write(block);
    }

    private void writeUSTAR() throws IOException {
        byte[] block = populateUSTAR();
        setChksum(block);
        super.write(block);
    }

    private void writeGNU() throws IOException {
        String[] split = splitName();
        boolean bigname = split == null;
        boolean biglink = entry.getLinkname().length() > 100;
        byte[] tarheader = new byte[512];
        if (bigname) {
            byte[] headerdata = entry.getName().getBytes(StandardCharsets.UTF_8);
            int len = headerdata.length;
            int blocks = (len + 511) / 512;
            byte[] headerblocks = new byte[blocks * 512];
            System.arraycopy(headerdata, 0, headerblocks, 0, len);
            File f = new File(entry.getName());
            File d = f.getParentFile();
            String name = f.getName();
            // long pid = ProcessHandle.current().getPid();
            long pid = 0;
            String tmpfile = String.format("././@Longlink/%s", name).substring(0, 100);
            setStringValue(tmpfile, tarheader, NAME_OFFSET, NAME_LENGTH, false);
            setNumericValue(0444, tarheader, MODE_OFFSET, MODE_LENGTH);
            setNumericValue(0, tarheader, UID_OFFSET, UID_LENGTH);
            setNumericValue(0, tarheader, GID_OFFSET, GID_LENGTH);
            setNumericValue(len, tarheader, SIZE_OFFSET, SIZE_LENGTH);
            setNumericValue(System.currentTimeMillis() / 1000, tarheader, MTIME_OFFSET, MTIME_LENGTH);
            tarheader[TYPEFLAG_OFFSET] = (byte) 'L';
            setStringValue("ustar  ", tarheader, MAGIC_OFFSET, GNU_MAGIC_LENGTH, true);
            setStringValue("root", tarheader, UNAME_OFFSET, UNAME_LENGTH, true);
            setStringValue("root", tarheader, GNAME_OFFSET, GNAME_LENGTH, true);
            setNumericValue(0, tarheader, DEVMAJOR_OFFSET, DEVMAJOR_LENGTH);
            setNumericValue(0, tarheader, DEVMINOR_OFFSET, DEVMINOR_LENGTH);
            setChksum(tarheader);
            super.write(tarheader);
            super.write(headerdata);
        }
        if (biglink) {
            byte[] headerdata = entry.getLinkname().getBytes(StandardCharsets.UTF_8);
            int len = headerdata.length;
            int blocks = (len + 511) / 512;
            byte[] headerblocks = new byte[blocks * 512];
            System.arraycopy(headerdata, 0, headerblocks, 0, len);
            File f = new File(entry.getLinkname());
            File d = f.getParentFile();
            String name = f.getName();
            // long pid = ProcessHandle.current().getPid();
            long pid = 0;
            String tmpfile = String.format("././@Longlink/%s", name).substring(0, 100);
            setStringValue(tmpfile, tarheader, NAME_OFFSET, NAME_LENGTH, false);
            setNumericValue(0444, tarheader, MODE_OFFSET, MODE_LENGTH);
            setNumericValue(0, tarheader, UID_OFFSET, UID_LENGTH);
            setNumericValue(0, tarheader, GID_OFFSET, GID_LENGTH);
            setNumericValue(len, tarheader, SIZE_OFFSET, SIZE_LENGTH);
            setNumericValue(System.currentTimeMillis() / 1000, tarheader, MTIME_OFFSET, MTIME_LENGTH);
            tarheader[TYPEFLAG_OFFSET] = (byte) 'K';
            setStringValue("ustar  ", tarheader, MAGIC_OFFSET, GNU_MAGIC_LENGTH, true);
            setStringValue("root", tarheader, UNAME_OFFSET, UNAME_LENGTH, true);
            setStringValue("root", tarheader, GNAME_OFFSET, GNAME_LENGTH, true);
            setNumericValue(0, tarheader, DEVMAJOR_OFFSET, DEVMAJOR_LENGTH);
            setNumericValue(0, tarheader, DEVMINOR_OFFSET, DEVMINOR_LENGTH);
            setChksum(tarheader);
            super.write(tarheader);
            super.write(headerdata);
        }
        Arrays.fill(tarheader, (byte)0);
        if (bigname) {
            setStringValue(entry.getName().substring(0,100), tarheader, NAME_OFFSET, NAME_LENGTH, false);
        } else {
            String[] names = splitName();
            setStringValue(names[0], tarheader, NAME_OFFSET, NAME_LENGTH, false);
            if (names.length > 1) {
                setStringValue(names[1], tarheader, NAME_OFFSET, NAME_LENGTH, false);
            }
        }
        setNumericValue(entry.getMode(), tarheader, MODE_OFFSET, MODE_LENGTH);
        setGnuNumericValue(entry.getUid(), tarheader, UID_OFFSET, UID_LENGTH);
        setGnuNumericValue(entry.getGid(), tarheader, GID_OFFSET, GID_LENGTH);
        setGnuNumericValue(entry.getSize(), tarheader, SIZE_OFFSET, SIZE_LENGTH);
        setGnuNumericValue(entry.getMtime().to(TimeUnit.SECONDS), tarheader, MTIME_OFFSET, MTIME_LENGTH);
        tarheader[TYPEFLAG_OFFSET] = (byte) entry.getTypeflag();
        if (biglink) {
            setStringValue(entry.getLinkname().substring(0, 100), tarheader, LINKNAME_OFFSET, LINKNAME_LENGTH, false);
        } else {
            setStringValue(entry.getLinkname(), tarheader, LINKNAME_OFFSET, LINKNAME_LENGTH, false);
        }
        setStringValue(entry.getMagic(), tarheader, MAGIC_OFFSET, GNU_MAGIC_LENGTH, true);
        setStringValue(entry.getUname(), tarheader, UNAME_OFFSET, UNAME_LENGTH, false);
        setStringValue(entry.getGname(), tarheader, GNAME_OFFSET, GNAME_LENGTH, false);
        setGnuNumericValue(entry.getDevmajor(), tarheader, DEVMAJOR_OFFSET, DEVMAJOR_LENGTH);
        setGnuNumericValue(entry.getDevminor(), tarheader, DEVMINOR_OFFSET, DEVMINOR_LENGTH);
        setChksum(tarheader);
        super.write(tarheader);
    }

    private void writePAX() throws IOException {
        String[] split = splitName();
        boolean bigname = split == null;
        boolean biguid = entry.getUid() < 0 || entry.getUid() > 07777777;
        boolean biggid = entry.getGid() < 0 || entry.getGid() > 07777777;
        boolean bigsize = entry.getSize() > 077777777777l;
        boolean bigmtime = entry.getMtime().to(TimeUnit.SECONDS) > 077777777777l;
        boolean biglink = entry.getLinkname().length() > 100;
        boolean biguser = entry.getUname().length() > 32;
        boolean biggroup = entry.getGname().length() > 32;
        boolean hasatime = entry.getAtime() != null;
        boolean hasctime = entry.getCtime() != null;
        boolean hascharset = entry.getCharset() != null;
        boolean hascomment = entry.getComment() != null && !entry.getComment().isEmpty();
        boolean hasextra = entry.getExtraHeaders() != null && !entry.getExtraHeaders().isEmpty();
        boolean doextra = bigname || biguid || biggid || bigsize || bigmtime || biglink || biguser || biggroup || hasatime || hasctime || hascharset || hascomment || hasextra;
        if (doextra) {
            StringBuilder header = new StringBuilder();
            if (bigname) {
                header.append(makeRecord("path", entry.getName()));
            }
            if (biguid) {
                header.append(makeRecord("uid", entry.getUid().toString()));
            }
            if (biggid) {
                header.append(makeRecord("gid", entry.getGid().toString()));
            }
            if (bigsize) {
                header.append(makeRecord("size", entry.getSize().toString()));
            }
            if (bigmtime) {
                header.append(makeRecord("mtime", getTimestamp(entry.getMtime())));
            }
            if (hasatime) {
                header.append(makeRecord("atime", getTimestamp(entry.getAtime())));
            }
            if (hasctime) {
                header.append(makeRecord("ctime", getTimestamp(entry.getCtime())));
            }
            if (biglink) {
                header.append(makeRecord("linkpath", entry.getLinkname()));
            }
            if (biguser) {
                header.append(makeRecord("uname", entry.getUname()));
            }
            if (biggroup) {
                header.append(makeRecord("gname", entry.getGname()));
            }
            if (hascharset) {
                header.append(makeRecord("charset", stringFromCharset(entry.getCharset())));
            }
            if (hascomment) {
                header.append(makeRecord("comment", entry.getComment()));
            }
            if (hasextra) {
                entry.getExtraHeaders().forEach((k, v) -> {
                    switch (k) {
                        case "atime":
                        case "charset":
                        case "comment":
                        case "ctime":
                        case "gid":
                        case "gname":
                        case "hdrcharset":
                        case "linkpath":
                        case "mtime":
                        case "path":
                        case "size":
                        case "uid":
                        case "uname":
                            // do not save
                            break;
                        default:
                            header.append(makeRecord(k, v));
                            break;
                    }
                });
            }
            byte[] headerdata = header.toString().getBytes(StandardCharsets.UTF_8);
            int len = headerdata.length;
            int blocks = (len + 511) / 512;
            byte[] headerblocks = new byte[blocks * 512];
            System.arraycopy(headerdata, 0, headerblocks, 0, len);
            byte[] tarheader = new byte[512];
            File f = new File(entry.getName());
            File d = f.getParentFile();
            String name = f.getName();
            // long pid = ProcessHandle.current().getPid();
            long pid = 0;
            String tmpfile = String.format("%s/PaxHeaders.%d/%s", f.toString(), pid, name).substring(0, 100);
            setStringValue(tmpfile, tarheader, NAME_OFFSET, NAME_LENGTH, false);
            setNumericValue(0444, tarheader, MODE_OFFSET, MODE_LENGTH);
            setNumericValue(0, tarheader, UID_OFFSET, UID_LENGTH);
            setNumericValue(0, tarheader, GID_OFFSET, GID_LENGTH);
            setNumericValue(len, tarheader, SIZE_OFFSET, SIZE_LENGTH);
            setNumericValue(System.currentTimeMillis() / 1000, tarheader, MTIME_OFFSET, MTIME_LENGTH);
            tarheader[TYPEFLAG_OFFSET] = (byte) 'x';
            setStringValue("ustar", tarheader, MAGIC_OFFSET, MAGIC_LENGTH, true);
            setStringValue("00", tarheader, VERSION_OFFSET, VERSION_LENGTH, false);
            setStringValue("root", tarheader, UNAME_OFFSET, UNAME_LENGTH, true);
            setStringValue("root", tarheader, GNAME_OFFSET, GNAME_LENGTH, true);
            setNumericValue(0, tarheader, DEVMAJOR_OFFSET, DEVMAJOR_LENGTH);
            setNumericValue(0, tarheader, DEVMINOR_OFFSET, DEVMINOR_LENGTH);
            setChksum(tarheader);
            super.write(tarheader);
            super.write(headerdata);
            Arrays.fill(tarheader, (byte)0);
            if (bigname) {
                setStringValue(entry.getName().substring(0,100), tarheader, NAME_OFFSET, NAME_LENGTH, false);
            } else {
                String[] names = splitName();
                setStringValue(names[0], tarheader, NAME_OFFSET, NAME_LENGTH, false);
                if (names.length > 1) {
                    setStringValue(names[1], tarheader, NAME_OFFSET, NAME_LENGTH, false);
                }
            }
            setNumericValue(entry.getMode(), tarheader, MODE_OFFSET, MODE_LENGTH);
            if (biguid) {
                setNumericValue(07777777, tarheader, UID_OFFSET, UID_LENGTH);
            } else {
                setNumericValue(entry.getUid(), tarheader, UID_OFFSET, UID_LENGTH);
            }
            if (biggid) {
                setNumericValue(07777777, tarheader, GID_OFFSET, GID_LENGTH);
            } else {
                setNumericValue(entry.getGid(), tarheader, GID_OFFSET, GID_LENGTH);
            }
            if (bigsize) {
                setNumericValue(077777777777l, tarheader, SIZE_OFFSET, SIZE_LENGTH);
            } else {
                setNumericValue(entry.getSize(), tarheader, SIZE_OFFSET, SIZE_LENGTH);
            }
            if (bigmtime) {
                setNumericValue(077777777777l, tarheader, MTIME_OFFSET, MTIME_LENGTH);
            } else {
                setNumericValue(entry.getMtime().to(TimeUnit.SECONDS), tarheader, MTIME_OFFSET, MTIME_LENGTH);
            }
            tarheader[TYPEFLAG_OFFSET] = (byte) entry.getTypeflag();
            if (biglink) {
                setStringValue(entry.getLinkname().substring(0, 100), tarheader, LINKNAME_OFFSET, LINKNAME_LENGTH, false);
            } else {
                setStringValue(entry.getLinkname(), tarheader, LINKNAME_OFFSET, LINKNAME_LENGTH, false);
            }
            setStringValue(entry.getMagic(), tarheader, MAGIC_OFFSET, MAGIC_LENGTH, true);
            setStringValue(entry.getVersion(), tarheader, VERSION_OFFSET, VERSION_LENGTH, true);
            if (biguser) {
                setStringValue(entry.getUname().substring(0, 32), tarheader, UNAME_OFFSET, UNAME_LENGTH, false);
            } else {
                setStringValue(entry.getUname(), tarheader, UNAME_OFFSET, UNAME_LENGTH, false);
            }
            if (biggroup) {
                setStringValue(entry.getGname().substring(0, 32), tarheader, GNAME_OFFSET, GNAME_LENGTH, false);
            } else {
                setStringValue(entry.getGname(), tarheader, GNAME_OFFSET, GNAME_LENGTH, false);
            }
            setNumericValue(entry.getDevmajor(), tarheader, DEVMAJOR_OFFSET, DEVMAJOR_LENGTH);
            setNumericValue(entry.getDevminor(), tarheader, DEVMINOR_OFFSET, DEVMINOR_LENGTH);
            setChksum(tarheader);
            super.write(tarheader);
        } else {
            writeUSTAR();
        }
    }

    private void setChksum(byte[] block) {
        int chk = 0;
        for (int i = 511; i >= 0; --i) {
            chk += block[i];
        }
        setNumericValue(chk, block, CHKSUM_OFFSET, CHKSUM_LENGTH);
    }
}

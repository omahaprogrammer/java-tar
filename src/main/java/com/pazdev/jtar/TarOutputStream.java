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

import static com.pazdev.jtar.TarConstants.*;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

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

    /**
     * Checks the given entry to verify that it will produce a valid TAR header.
     * These checks are heavily dependent on the specified entry format.
     * 
     * @param entry the entry to check
     * @throws TarException if the entry is malformed.
     */
    private void checkEntry(TarEntry entry) throws TarFormatException {
        // check name
        switch (entry.getFormat()) {
            case V7:
                if (entry.getName().length() > 100) {
                    throw new TarFormatException("Name cannot be longer than 100 characters");
                }
                break;
            case USTAR:
                String name = entry.getName();
                String prefix = "";
                break;
        }
        
        // check size
        switch (entry.getTypeflag()) {
            case LNKTYPE:
                if (!TarFormat.PAX.equals(entry.getFormat()) && entry.getSize() != 0) {
                    throw new TarFormatException("The size for this type of file must be zero");
                }
                break;
            case SYMTYPE:
                if (entry.getSize() != 0) {
                    throw new TarFormatException("The size for this type of file must be zero");
                }
                break;
        }

    }

    private void writeEntry() throws IOException {
        
    }
}

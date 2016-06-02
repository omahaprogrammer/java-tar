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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Jonathan Paz jonathan.paz@pazdev.com
 */
public class TarEntry {

    private String name;
    private Integer mode;
    private Integer uid;
    private Integer gid;
    private Long size;
    private FileTime mtime;
    private FileTime atime;
    private FileTime ctime;
    private Integer chksum;
    private Character typeflag;
    private String linkname;
    private String magic;
    private String version;
    private String uname;
    private String gname;
    private Integer devmajor;
    private Integer devminor;
    private Charset charset;
    private String comment;
    private Map<String, String> extraHeaders;
    private TarFormat format;

    public TarEntry(String name) {
        this.name = name;
    }

    public TarEntry(TarEntry entry) {
        this.name = entry.name;
        this.mode = entry.mode;
        this.uid = entry.uid;
        this.gid = entry.gid;
        this.size = entry.size;
        this.mtime = entry.mtime;
        this.atime = entry.atime;
        this.ctime = entry.ctime;
        this.chksum = entry.chksum;
        this.typeflag = entry.typeflag;
        this.linkname = entry.linkname;
        this.magic = entry.magic;
        this.version = entry.version;
        this.uname = entry.uname;
        this.gname = entry.gname;
        this.devmajor = entry.devmajor;
        this.devminor = entry.devminor;
        this.charset = entry.charset;
        this.comment = entry.comment;
        this.format = entry.format;
        if (entry.extraHeaders != null && !entry.extraHeaders.isEmpty()) {
            this.extraHeaders = new HashMap<>(entry.extraHeaders);
        }
    }

    TarEntry() {
        super();
    }

    /**
     * Merge the given TarEntry object Integero this object. The attributes of the
     * given entry will override the attribute for this object.
     * @param e the entry
     * @return 
     */
    TarEntry mergeEntry(TarEntry e) {
        if (e.name != null) {
            this.name = e.name;
        }
        if (e.mode != null) {
            this.mode = e.mode;
        }
        if (e.uid != null) {
            this.uid = e.uid;
        }
        if (e.gid != null) {
            this.gid = e.gid;
        }
        if (e.size != null) {
            this.size = e.size;
        }
        if (e.mtime != null) {
            this.mtime = e.mtime;
        }
        if (e.atime != null) {
            this.atime = e.atime;
        }
        if (e.linkname != null) {
            this.linkname = e.linkname;
        }
        if (e.uname != null) {
            this.uname = e.uname;
        }
        if (e.gname != null) {
            this.gname = e.gname;
        }
        if (e.devmajor != null) {
            this.devmajor = e.devmajor;
        }
        if (e.devminor != null) {
            this.devminor = e.devminor;
        }
        if (e.charset != null) {
            this.charset = e.charset;
        }
        if (e.comment != null) {
            this.comment = e.comment;
        }
        if (e.extraHeaders != null) {
            this.extraHeaders.putAll(e.extraHeaders);
        }
        return this;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getMode() {
        return mode;
    }

    public void setMode(Integer mode) {
        this.mode = mode;
    }

    public Set<PosixFilePermission> getPermissions() {
        Set<PosixFilePermission> perms = null;
        if (mode != null) {
            perms = EnumSet.noneOf(PosixFilePermission.class);
            int m = mode; // auto-unbox once;
            if ((m & OWNER_READ) != 0) {
                perms.add(PosixFilePermission.OWNER_READ);
            }
            if ((m & OWNER_WRITE) != 0) {
                perms.add(PosixFilePermission.OWNER_WRITE);
            }
            if ((m & OWNER_EXECUTE) != 0) {
                perms.add(PosixFilePermission.OWNER_EXECUTE);
            }
            if ((m & GROUP_READ) != 0) {
                perms.add(PosixFilePermission.GROUP_READ);
            }
            if ((m & GROUP_WRITE) != 0) {
                perms.add(PosixFilePermission.GROUP_WRITE);
            }
            if ((m & GROUP_EXECUTE) != 0) {
                perms.add(PosixFilePermission.GROUP_EXECUTE);
            }
            if ((m & OTHERS_READ) != 0) {
                perms.add(PosixFilePermission.OTHERS_READ);
            }
            if ((m & OTHERS_WRITE) != 0) {
                perms.add(PosixFilePermission.OTHERS_WRITE);
            }
            if ((m & OTHERS_EXECUTE) != 0) {
                perms.add(PosixFilePermission.OTHERS_EXECUTE);
            }
            perms = Collections.unmodifiableSet(perms);
        }
        return perms;
    }

    public void setPermissions(Set<PosixFilePermission> permissions) {
        Integer newmode = null;
        if (permissions != null && !permissions.isEmpty()) {
            int m = 0;
            EnumSet<PosixFilePermission> perms = EnumSet.copyOf(permissions);
            if (perms.contains(PosixFilePermission.OWNER_READ)) {
                m |= OWNER_READ;
            }
            if (perms.contains(PosixFilePermission.OWNER_WRITE)) {
                m |= OWNER_WRITE;
            }
            if (perms.contains(PosixFilePermission.OWNER_EXECUTE)) {
                m |= OWNER_EXECUTE;
            }
            if (perms.contains(PosixFilePermission.GROUP_READ)) {
                m |= GROUP_READ;
            }
            if (perms.contains(PosixFilePermission.GROUP_WRITE)) {
                m |= GROUP_WRITE;
            }
            if (perms.contains(PosixFilePermission.GROUP_EXECUTE)) {
                m |= GROUP_EXECUTE;
            }
            if (perms.contains(PosixFilePermission.OTHERS_READ)) {
                m |= OTHERS_READ;
            }
            if (perms.contains(PosixFilePermission.OTHERS_WRITE)) {
                m |= OTHERS_WRITE;
            }
            if (perms.contains(PosixFilePermission.OTHERS_EXECUTE)) {
                m |= OTHERS_EXECUTE;
            }
            newmode = m;
        }
        mode = newmode;
    }

    public Integer getUid() {
        return uid;
    }

    /**
     * Sets the User ID owning the file described by this {@code TarEntry}. While, 
     * theoretically, a POSIX TAR file can support any arbitrary-length integer
     * number as a User ID, that would imply that more than two million users
     * are on a single system, and that is likely unsupportable on an operating
     * system anyway.
     * 
     * @param uid the User ID owining the file
     */
    public void setUid(Integer uid) {
        this.uid = uid;
    }

    public Integer getGid() {
        return gid;
    }

    /**
     * Sets the Group ID of the file described by this {@code TarEntry}. While, 
     * theoretically, a POSIX TAR file can support any arbitrary-length integer
     * number as a Group ID, that would imply that more than two million groups
     * are on a single system, and that is likely unsupportable on an operating
     * system anyway.
     * 
     * @param gid the Group ID of the file
     */
    public void setGid(Integer gid) {
        this.gid = gid;
    }

    public Long getSize() {
        return size;
    }

    /**
     * Sets the size of the file described by this {@code TarEntry}. While,
     * theoretically, a POSIX TAR entry can support any arbitrarily large
     * non-negative integer file size, this class will only support {@code long}
     * values as no filesystem today, or in the forseeable future, can support
     * files larger than nine exabytes.
     * 
     * @param size the size of the file
     * @throws IllegalArgumentException if <pre>size &lt; 0</pre>
     */
    public void setSize(Long size) {
        if (size < 0) {
            throw new IllegalArgumentException("Cannot support negative size");
        }
        this.size = size;
    }

    public FileTime getMtime() {
        return mtime;
    }

    /**
     * 
     * @param mtime 
     */
    public void setMtime(FileTime mtime) {
        this.mtime = mtime;
    }

    public FileTime getAtime() {
        return atime;
    }

    public void setAtime(FileTime atime) {
        this.atime = atime;
    }

    public FileTime getCtime() {
        return ctime;
    }

    public void setCtime(FileTime ctime) {
        this.ctime = ctime;
    }

    public Integer getChksum() {
        return chksum;
    }

    public void setChksum(Integer chksum) {
        this.chksum = chksum;
    }

    public char getTypeflag() {
        return typeflag;
    }

    public void setTypeflag(char typeflag) {
        this.typeflag = typeflag;
    }

    public String getLinkname() {
        return linkname;
    }

    public void setLinkname(String linkname) {
        this.linkname = linkname;
    }

    public String getMagic() {
        return magic;
    }

    public void setMagic(String magic) {
        this.magic = magic;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getUname() {
        return uname;
    }

    public void setUname(String uname) {
        this.uname = uname;
    }

    public String getGname() {
        return gname;
    }

    public void setGname(String gname) {
        this.gname = gname;
    }

    public Integer getDevmajor() {
        return devmajor;
    }

    public void setDevmajor(Integer devmajor) {
        this.devmajor = devmajor;
    }

    public Integer getDevminor() {
        return devminor;
    }

    public void setDevminor(Integer devminor) {
        this.devminor = devminor;
    }

    public Charset getCharset() {
        return charset;
    }

    public void setCharset(Charset charset) {
        this.charset = charset;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Map<String, String> getExtraHeaders() {
        return extraHeaders;
    }

    public void setExtraHeaders(Map<String, String> extraHeaders) {
        this.extraHeaders = extraHeaders;
    }

    public TarFormat getFormat() {
        return format;
    }

    public void setFormat(TarFormat format) {
        this.format = format;
    }

    private static Instant stringToInstant(String t) {
        BigDecimal time = new BigDecimal(t);
        BigDecimal seconds = time.setScale(0, RoundingMode.FLOOR);
        BigDecimal nanos = time.subtract(seconds).movePointRight(9).setScale(0, RoundingMode.FLOOR);
        return Instant.ofEpochSecond(seconds.longValue(), nanos.longValue());
    }

    void setMtime(String mtime) {
        if (mtime != null) {
            this.mtime = FileTime.from(stringToInstant(mtime));
        }
    }

    void setAtime(String atime) {
        if (atime != null) {
            this.atime = FileTime.from(stringToInstant(atime));
        }
    }

    void setCtime(String ctime) {
        if (ctime != null) {
            this.ctime = FileTime.from(stringToInstant(ctime));
        }
    }
}

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
import java.util.Objects;
import java.util.Set;

/**
 * This class represents a TAR file entry. It supports POSIX, USTAR, GNU, and
 * original (V7) TAR file entries, with the default being POSIX. 
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
    private String magic = "ustar";
    private String version = "00";
    private String uname;
    private String gname;
    private Integer devmajor;
    private Integer devminor;
    private Charset charset;
    private String comment;
    private Map<String, String> extraHeaders;
    private TarFormat format = TarFormat.PAX;

    /**
     * Creates a new TarEntry with the given name. All path names must use the "/"
     * character as a directory separator. This constructor will not fix the name
     * if it uses Windows-style folder separators.
     * @param name the entry name
     * 
     * @throws NullPointerException if name is null
     */
    public TarEntry(String name) {
        super();
        Objects.requireNonNull(name);
        this.name = name;
    }

    /**
     * Creates a new TAR entry with fields taken from the specified TAR entry
     * @param entry a TAR entry object
     * @throws NullPointerException if entry is null
     */
    public TarEntry(TarEntry entry) {
        super();
        Objects.requireNonNull(entry);
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

    /**
     * Creates a new blank tar entry for internal use only.
     */
    TarEntry() {
        super();
    }

    /**
     * Apply the properties of the given TarEntry object onto the blank properties
     * of this object. The attributes of the given entry will not override the
     * attribute for this object, as opposed to the {@code mergeEntry} method.
     * 
     * @param e the entry
     * @return the merged TAR entry
     */
    TarEntry applyEntry(TarEntry e) {
        if (e == null) {
            return this;
        }
        if (e.name != null && this.name == null) {
            this.name = e.name;
        }
        if (e.mode != null && this.mode == null) {
            this.mode = e.mode;
        }
        if (e.uid != null && this.uid == null) {
            this.uid = e.uid;
        }
        if (e.gid != null && this.gid == null) {
            this.gid = e.gid;
        }
        if (e.size != null && this.size == null) {
            this.size = e.size;
        }
        if (e.mtime != null && this.mtime == null) {
            this.mtime = e.mtime;
        }
        if (e.atime != null && this.atime == null) {
            this.atime = e.atime;
        }
        if (e.linkname != null && this.linkname == null) {
            this.linkname = e.linkname;
        }
        if (e.uname != null && this.uname == null) {
            this.uname = e.uname;
        }
        if (e.gname != null && this.gname == null) {
            this.gname = e.gname;
        }
        if (e.devmajor != null && this.devmajor == null) {
            this.devmajor = e.devmajor;
        }
        if (e.devminor != null && this.devminor == null) {
            this.devminor = e.devminor;
        }
        if (e.charset != null && this.charset == null) {
            this.charset = e.charset;
        }
        if (e.comment != null && this.comment == null) {
            this.comment = e.comment;
        }
        if (e.extraHeaders != null) {
            e.extraHeaders.forEach(this.extraHeaders::putIfAbsent);
        }
        return this;
    }

    /**
     * Merge the given TarEntry object into this object. The attributes of the
     * given entry will override the attribute for this object.
     * @param e the entry
     * @return the merged TAR entry
     * @throws NullPointerException if e is null
     */
    TarEntry mergeEntry(TarEntry e) {
        Objects.requireNonNull(e);
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

    /**
     * The name of the entry.
     * @return the name of the entry
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the entry. Note that the directory separator MUST be the
     * "/" character. This method will not fix Windows-style folder separators
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * The mode (POSIX permission representation) of the entry.
     * @return the mode of the entry
     */
    public Integer getMode() {
        return mode;
    }

    /**
     * Sets the mode (POSIX permission representation) of the entry.
     * @param mode the mode of the entry
     * @throws IllegalArgumentException if {@code mode} is negative or greater
     * than {@code 07777}.
     */
    public void setMode(Integer mode) {
        if (mode != null && (mode < 0 || mode > 07777)) {
            throw new IllegalArgumentException("invalid mode");
        }
        this.mode = mode;
    }

    /**
     * The POSIX permissions for this entry. This is a Java representation of the
     * mode of the entry.
     * @return the permission set for this entry.
     */
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

    /**
     * Sets the permissions for this entry. This method processes the given set
     * and uses it to determine the actual numeric value to set {@code mode} to.
     * Note that by using this method, the setuid, setgid, and sticky bits cannot
     * be set or preserved.
     * @param permissions the permissions for this entry
     */
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

    /**
     * The User ID owning the file described by this entry
     * @return the User ID for this entry
     */
    public Integer getUid() {
        return uid;
    }

    /**
     * Sets the User ID owning the file described by this entry.
     * 
     * @param uid the User ID owining the file
     */
    public void setUid(Integer uid) {
        this.uid = uid;
    }

    /**
     * The Group ID for the file described by this entry
     * @return the Group ID for this entry
     */
    public Integer getGid() {
        return gid;
    }

    /**
     * Sets the Group ID for the file described by this {@code TarEntry}.
     * 
     * @param gid the Group ID of the file
     */
    public void setGid(Integer gid) {
        this.gid = gid;
    }

    /**
     * The size of the file described by this entry.
     * @return 
     */
    public Long getSize() {
        return size;
    }

    /**
     * Sets the size of the file described by this entry. This value must be set
     * to the intended file size before writing the file to a {@code TarOutputStream}.
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

    /**
     * The last modification time of the file described by this entry.
     * @return the last modification time
     */
    public FileTime getMtime() {
        return mtime;
    }

    /**
     * Sets the last modification time of the entry
     * @param mtime Sets the last modification time of the entry
     */
    public void setMtime(FileTime mtime) {
        this.mtime = mtime;
    }

    /**
     * The last access time of the file described by this entry
     * @return the last access time
     */
    public FileTime getAtime() {
        return atime;
    }

    /**
     * Sets the last access time of this entry
     * @param atime the last access time
     */
    public void setAtime(FileTime atime) {
        this.atime = atime;
    }

    /**
     * The creation time of the file described by this entry
     * @return the creation time
     */
    public FileTime getCtime() {
        return ctime;
    }

    /**
     * Sets the creation time of the file described by this entry
     * @param ctime sets the creation time
     */
    public void setCtime(FileTime ctime) {
        this.ctime = ctime;
    }

    /**
     * The checksum of this entry. This parameter is generally only set while reading
     * a TAR file. It does not need to be set, nor will it be read, while writing
     * to a TAR file: it will be calculated at write-time. It is a simple sum of
     * all the bytes in the header record, with the space for the checksum being
     * replaced with space characters.
     * @return the checksum of the entry
     */
    public Integer getChksum() {
        return chksum;
    }

    /**
     * Sets the checksum of this entry. This parameter is generally only set while
     * reading a TAR file. It does not need to be set, nor will it be read, while
     * writing to a TAR file: it will be calculated at write-time.
     * 
     * @param chksum the checksum of the entry
     */
    public void setChksum(Integer chksum) {
        this.chksum = chksum;
    }

    /**
     * The type flag for this entry.
     * @return  the type flag
     */
    public char getTypeflag() {
        return typeflag;
    }

    /**
     * The type flag for this entry. This can be set with any character, but only
     * the characters 0, 1, 2, 3, 4, 5, 6, or 7 will have any real impact, as all
     * other characters will simply be treated as normal files.
     * @param typeflag the typeflag for this entry.
     */
    public void setTypeflag(char typeflag) {
        this.typeflag = typeflag;
    }

    /**
     * The path to the file that this hard link or symbolic link points to.
     * @return the link path
     */
    public String getLinkname() {
        return linkname;
    }

    /**
     * Sets the path to the file that this hard link or symbolic link points to.
     * If the file is a hard link, and the entry has no data, then 
     * @param linkname 
     */
    public void setLinkname(String linkname) {
        this.linkname = linkname;
    }

    /**
     * The magic string describing the type of TAR entry this object is.
     * @return the magic string
     */
    public String getMagic() {
        return magic;
    }

    /**
     * Sets the magic string describing the type of tar entry this object is.
     * @param magic the magic string
     */
    void setMagic(String magic) {
        this.magic = magic;
    }

    /**
     * The version describing the type of TAR entry this object is.
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Sets the version describing the type of tar entry this object is.
     * @param version the version
     */
    void setVersion(String version) {
        this.version = version;
    }

    /**
     * The user name owning the file described by this entry
     * @return the user name
     */
    public String getUname() {
        return uname;
    }

    /**
     * Sets the user name for this entry.
     * @param uname the user name
     */
    public void setUname(String uname) {
        this.uname = uname;
    }

    /**
     * The group name for this entry.
     * @return the group name.
     */
    public String getGname() {
        return gname;
    }

    /**
     * Sets the group name for this entry.
     * @param gname the group name.
     */
    public void setGname(String gname) {
        this.gname = gname;
    }

    /**
     * The devmajor id being described by this entry
     * @return the devmajor id
     */
    public Integer getDevmajor() {
        return devmajor;
    }

    /**
     * Sets the devmajor id to be described by this entry. This should only be set
     * with the appropriate typeflags set.
     * @param devmajor the devmajor id
     */
    public void setDevmajor(Integer devmajor) {
        this.devmajor = devmajor;
    }

    /**
     * The devminor id being described by this entry
     * @return the devminor id
     */
    public Integer getDevminor() {
        return devminor;
    }

    /**
     * Sets the devminor id to be described by this entry. This should only be set
     * with the appropriate typeflags set.
     * @param devminor the devminor id
     */
    public void setDevminor(Integer devminor) {
        this.devminor = devminor;
    }

    /**
     * The character set for the file described by this entry
     * @return the file's character set
     */
    public Charset getCharset() {
        return charset;
    }

    /**
     * Sets the character set for the file described by this entry. This flag has
     * no impact on the reading or writing of this file by {@code TarInputStream}
     * or {@code TarOutputStream}, but may give a clue to applications using this
     * class as to how to process the data for this file.
     * @param charset the file's character set
     */
    public void setCharset(Charset charset) {
        this.charset = charset;
    }

    /**
     * This entry's comment
     * @return the comment
     */
    public String getComment() {
        return comment;
    }

    /**
     * Sets this entry's comment. This value has no impact on data handling, but
     * may be set by applications for their own purposes.
     * @param comment this comment
     */
    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * A map describing any additional header values being set. This map may be
     * null. This class makes no arrangements for the thread safety for this map.
     * When this entry is being used to write to a TAR file, a copy of this map
     * if present, is made and read from while processing.
     * @return extra headers for this entry
     */
    public Map<String, String> getExtraHeaders() {
        return extraHeaders;
    }

    /**
     * Sets the map describing any additional header values being set.
     * This class makes no arrangements for the thread safety for this map.
     * When this entry is being used to write to a TAR file, a copy of this map
     * if present, is made and read from while processing.
     * @param extraHeaders extra headers for this entry
     */
    public void setExtraHeaders(Map<String, String> extraHeaders) {
        this.extraHeaders = extraHeaders;
    }

    /**
     * The format of this entry. By default, the format is {@code TarFormat.PAX}
     * @return this entry's format
     */
    public TarFormat getFormat() {
        return format;
    }

    /**
     * Sets the intended format of this entry. By default, the format is
     * {@code TarFormat.PAX}. When this format is changed, it will also change
     * the magic and version fields to the format-appropriate values.
     * @param format this entry's format
     */
    public void setFormat(TarFormat format) {
        this.format = format;
        switch (format) {
            case GNU:
                setMagic("ustar  ");
                setVersion(null);
                break;
            case PAX:
            case USTAR:
                setMagic("ustar");
                setVersion("00");
                break;
            case V7:
                setMagic(null);
                setVersion(null);
                break;
        }
    }

    /**
     * Creates an Instant based on the given string arbitrary-precision decimal
     * representation.
     * @param t the timestamp string
     * @return an instant representing the timestamp
     */
    private static Instant stringToInstant(String t) {
        BigDecimal time = new BigDecimal(t);
        BigDecimal seconds = time.setScale(0, RoundingMode.FLOOR);
        BigDecimal nanos = time.subtract(seconds).movePointRight(9).setScale(0, RoundingMode.FLOOR);
        return Instant.ofEpochSecond(seconds.longValue(), nanos.longValue());
    }

    /**
     * Sets the mtime based on a string value
     * @param mtime 
     */
    void setMtime(String mtime) {
        if (mtime != null) {
            this.mtime = FileTime.from(stringToInstant(mtime));
        }
    }

    /**
     * Sets the atime based on a string value
     * @param atime
     */
    void setAtime(String atime) {
        if (atime != null) {
            this.atime = FileTime.from(stringToInstant(atime));
        }
    }

    /**
     * Sets the ctime based on a string value
     * @param ctime
     */
    void setCtime(String ctime) {
        if (ctime != null) {
            this.ctime = FileTime.from(stringToInstant(ctime));
        }
    }

    /**
     * Determines if this entry represents a regular file. For this implementation's
     * purposes, the {@code CONTTYPE} (7) typeflag counts as a regular file.
     * @return {@code true} if this entry represents a regular file.
     */
    public boolean isRegularFile() {
        return typeflag == REGTYPE || typeflag == AREGTYPE || typeflag == CONTTYPE;
    }

    /**
     * Determines if this entry represents a hard link.
     * @return {@code true} if this entry represents a hard link
     */
    public boolean isHardLink() {
        return typeflag == LNKTYPE;
    }

    /**
     * Determines if this entry represents a symbolic link.
     * @return {@code true} if this entry represents a symbolic link
     */
    public boolean isSymbolicLink() {
        return typeflag == SYMTYPE;
    }

    /**
     * Determines if this entry represents a directory.
     * @return {@code true} if this entry represents a directory
     */
    public boolean isDirectory() {
        return typeflag == DIRTYPE; 
    }
}

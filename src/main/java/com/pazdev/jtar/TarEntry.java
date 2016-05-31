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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Jonathan Paz jonathan.paz@pazdev.com
 */
public class TarEntry {

    private String name;
    private EnumSet<PosixFilePermission> permissions;
    private Boolean setUid;
    private Boolean setGid;
    private Boolean sticky;
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

    public TarEntry(Path path) throws IOException {
        String[] pathParts = new String[path.getNameCount()];
        for (int i = pathParts.length - 1; i >= 0; i++) {
            pathParts[i] = path.getName(i).toString();
        }
        name = String.join("/", pathParts);
        permissions = EnumSet.copyOf(Files.getPosixFilePermissions(path));
    }

    public TarEntry(TarEntry entry) {
        this.name = entry.name;
        this.permissions = EnumSet.copyOf(entry.permissions);
        this.setUid = entry.setUid;
        this.setGid = entry.setGid;
        this.sticky = entry.sticky;
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
     * Merge the given TarEntry object into this object. The attributes of the
     * given entry will override the attribute for this object.
     * @param e the entry
     * @return 
     */
    TarEntry mergeEntry(TarEntry e) {
        if (e.name != null) {
            this.name = e.name;
        }
        if (e.permissions != null) {
            this.permissions = EnumSet.copyOf(e.permissions);
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

    public EnumSet<PosixFilePermission> getPermissions() {
        return permissions;
    }

    public void setPermissions(EnumSet<PosixFilePermission> permissions) {
        this.permissions = permissions;
    }

    public boolean isSetUid() {
        return setUid;
    }

    public void setSetUid(boolean setUid) {
        this.setUid = setUid;
    }

    public boolean isSetGid() {
        return setGid;
    }

    public void setSetGid(boolean setGid) {
        this.setGid = setGid;
    }

    public boolean isSticky() {
        return sticky;
    }

    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    public int getUid() {
        return uid;
    }

    public void setUid(int uid) {
        this.uid = uid;
    }

    public int getGid() {
        return gid;
    }

    public void setGid(int gid) {
        this.gid = gid;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public FileTime getMtime() {
        return mtime;
    }

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

    public int getChksum() {
        return chksum;
    }

    public void setChksum(int chksum) {
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

    public int getDevmajor() {
        return devmajor;
    }

    public void setDevmajor(int devmajor) {
        this.devmajor = devmajor;
    }

    public int getDevminor() {
        return devminor;
    }

    public void setDevminor(int devminor) {
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

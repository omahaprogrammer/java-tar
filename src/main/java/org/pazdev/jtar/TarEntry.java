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
package org.pazdev.jtar;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
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
    private boolean setUid;
    private boolean setGid;
    private boolean sticky;
    private int uid;
    private int gid;
    private long size;
    private FileTime mtime;
    private FileTime atime;
    private FileTime ctime;
    private int chksum;
    private char typeflag;
    private String linkname;
    private String magic;
    private String version;
    private String uname;
    private String gname;
    private int devmajor;
    private int devminor;
    private Charset charset;
    private String comment;
    private String hdrCharset;
    private Map<String, String> extraHeaders;
    private TarFormat format;
    private byte[] header;

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
        this.hdrCharset = entry.hdrCharset;
        this.format = entry.format;
        if (entry.extraHeaders != null && !entry.extraHeaders.isEmpty()) {
            this.extraHeaders = new HashMap<>(entry.extraHeaders);
        }
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

    public String getHdrCharset() {
        return hdrCharset;
    }

    public void setHdrCharset(String hdrCharset) {
        this.hdrCharset = hdrCharset;
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

    public byte[] getHeader() {
        return header;
    }

    public void setHeader(byte[] header) {
        this.header = header;
    }

}

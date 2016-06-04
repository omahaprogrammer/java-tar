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

/**
 * <p>
 * Provides classes for reading and writing the standard TAR file format. This
 * package is meant to be as analogous as possible to the {@code java.util.zip}
 * package, with the differences being due to the nature of the TAR format.
 * </p>
 * <h2>Supported formats</h2>
 * <ul>
 * <li>POSIX (pax)</li>
 * <li>
 * GNU - does not support multi-volume, sparse, dump directory, or volume heading
 * types. This system <em>does</em> support the enhanced number system (base256).
 * </li>
 * <li>
 * Old GNU - This system can read, but will not be able to write, this format. The
 * difference between this format and the GNU format is that this format will
 * <em>always</em> null-terminate name and prefix header fields.
 * </li>
 * <li>USTAR</li>
 * <li>Original (v7)</li>
 * </ul>
 * <h2>Major differences</h2>
 * <ul>
 * <li>
 * The absence of a {@code TarFile} class - TAR files are designed to be read and
 * processed like a stream, and therefore have nothing akin to a table of contents
 * that a ZIP file has. A {@code TarFile} class would then act as a strange and
 * highly inefficient wrapper around a {@code TarInputStream} instance. Also,
 * since TAR files are usually themselves compressed, a {@code TarFile} class
 * would have to guess based on file name as to what kind of decompression to
 * apply to the file before it could be read.
 * </li>
 * <li>
 * No relation to {@code InflatorInputStream} or {@code DeflatorOutputStream} - 
 * The TAR file format does not support internal compression the same way ZIP
 * files do. The files are compressed after they are formed. It is a reasonable
 * structure to have {@code TarInputStream} and {@code TarOutputStream} instances
 * wrap a decompressing input stream or compressing output stream to zip the files
 * up on the fly.
 * </li>
 * </ul>
 * @author Jonathan Paz jonathan.paz@pazdev.com
 */
package com.pazdev.tar;

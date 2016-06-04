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

import java.io.IOException;

/**
 * Signals that an exception occurred while processing TAR files.
 * @author Jonathan Paz jonathan.paz@pazdev.com
 */
public class TarException extends IOException {

    public TarException() {
        super();
    }

    public TarException(String message) {
        super(message);
    }

    public TarException(Throwable cause) {
        super(cause);
    }

    public TarException(String message, Throwable cause) {
        super(message, cause);
    }
    
}

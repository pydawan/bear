/*
 * Copyright (C) 2013 Andrey Chaschev.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cap4j.session;

/**
* User: chaschev
* Date: 7/29/13
*/
public class SshAddress {
    String username;
    String password;
    String address;

    public SshAddress(String username, String password, String address) {
        this.username = username;
        this.password = password;
        this.address = address;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("{");
        sb.append("username='").append(username).append('\'');
        sb.append(", address='").append(address).append('\'');
        sb.append('}');
        return sb.toString();
    }
}

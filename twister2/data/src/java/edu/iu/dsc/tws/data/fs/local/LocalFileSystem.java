//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
package edu.iu.dsc.tws.data.fs.local;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.dsc.tws.data.fs.FileSystem;
import edu.iu.dsc.tws.data.fs.Path;
import edu.iu.dsc.tws.data.utils.OperatingSystem;

/**
 * Represents a local file system.
 */
public class LocalFileSystem extends FileSystem {

  private static final Logger LOG = Logger.getLogger(LocalFileSystem.class.getName());

  /** The URI representing the local file system. */
  private static final URI uri = OperatingSystem.isWindows() ? URI.create("file:/") :
      URI.create("file:///");

  /** Path pointing to the current working directory.
   * Because Paths are not immutable, we cannot cache the proper path here */
  private final String workingDir;

  /** Path pointing to the current working directory.
   * Because Paths are not immutable, we cannot cache the proper path here */
  private final String homeDir;

  /** The host name of this machine */
  private final String hostName;

  /**
   * Constructs a new <code>LocalFileSystem</code> object.
   */
  public LocalFileSystem() {
    this.workingDir = new Path(System.getProperty("user.dir")).makeQualified(this).toString();
    this.homeDir = new Path(System.getProperty("user.home")).toString();

    String tmp = "unknownHost";
    try {
      tmp = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      LOG.log(Level.SEVERE,"Could not resolve local host", e);
    }
    this.hostName = tmp;
  }

  @Override
  public void setWorkingDirectory(Path path) {

  }

  @Override
  public Path getWorkingDirectory() {
    return null;
  }

  @Override
  public URI getUri() {
    return uri;
  }
}

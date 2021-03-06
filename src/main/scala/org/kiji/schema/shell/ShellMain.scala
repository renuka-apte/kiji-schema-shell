/**
 * (c) Copyright 2012 WibiData, Inc.
 *
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kiji.schema.shell

import org.kiji.common.flags.Flag
import org.kiji.common.flags.FlagParser
import org.kiji.schema.KConstants
import org.kiji.schema.KijiURI
import org.kiji.schema.shell.ddl.DDLCommand
import org.kiji.schema.shell.ddl.ErrorCommand
import org.kiji.schema.shell.input.FileInputSource
import org.kiji.schema.shell.input.JLineInputSource
import org.kiji.schema.shell.input.StringInputSource

/**
 * An object used to run a Kiji schema shell.
 */
class ShellMain {
  @Flag(name="kiji", usage="Kiji instance URI")
  var kijiURI: String = "kiji://.env/%s".format(KConstants.DEFAULT_INSTANCE_NAME)

  @Flag(name="expr", usage="Expression to execute")
  var expr: String = ""

  @Flag(name="file", usage="Script file to execute")
  var filename: String = ""

  /**
   * Programmatic entry point.
   * Like main(), but without that pesky sys.exit() call.
   * @returns a return status code. 0 is success.
   */
  def run(): Int = {
    val chatty = expr.equals("") && filename.equals("")
    if (chatty) {
      println("Kiji schema shell v" + ShellMain.version())
      println("""Enter 'help' for instructions (without quotes).
                |Enter 'quit' to quit.
                |DDL statements must be terminated with a ';'""".stripMargin)
    }
    processUserInput()
    if (chatty) {
      println("Thank you for flying Kiji!")
    }
    0 // Return 0 if we didn't terminate with an exception.
  }

  /**
   * Create the initial Environment object that should be used to start query processing.
   */
  def initialEnv(): Environment = {
    val input = (
      if (!filename.equals("")) {
        new FileInputSource(filename)
      } else if (!expr.equals("")) {
        new StringInputSource(expr)
      } else {
        // Read from the interactive terminal
        new JLineInputSource
      }
    )

    val uri = KijiURI.newBuilder(kijiURI).build()
    return new Environment(uri, Console.out, KijiSystem, input)
  }

  /**
   * Request a line of user input, parse it, and execute the command.
   * Recursively continue to request the next line of user input until
   * we exhaust the input.
   *
   * @return the final environment
   */
  def processUserInput(): Environment = {
    new InputProcessor().processUserInput(new StringBuilder, initialEnv())
  }
}

object ShellMain {

  /**
   * @returns the version number associated with this software package.
   */
  def version(): String = {
    // Uses the value of 'Implementation-Version' in META-INF/MANIFEST.MF:
    val version = Option(classOf[ShellMain].getPackage().getImplementationVersion())
    return version.getOrElse("(unknown)")
  }

  /**
   * Main entry point for running the Wibi shell.
   *
   * @param args Command line arguments.
   */
  def main(argv: Array[String]) {
    val shellMain = new ShellMain()
    val nonFlagArgs: Option[java.util.List[String]] = Option(FlagParser.init(shellMain, argv));
    if (nonFlagArgs.equals(None)) {
      sys.exit(1); // There was a problem parsing flags.
    }

    val retVal = shellMain.run()

    // Close all connections properly before exiting.
    KijiSystem.shutdown()
    if (retVal != 0) {
      sys.exit(retVal)
    }
  }
}

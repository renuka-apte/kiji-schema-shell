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

package org.kiji.schema.shell.ddl

import scala.collection.JavaConversions._

import org.kiji.schema.avro.ColumnDesc
import org.kiji.schema.avro.FamilyDesc
import org.kiji.schema.avro.LocalityGroupDesc
import org.kiji.schema.avro.TableLayoutDesc
import org.kiji.schema.layout.KijiTableLayout

import org.kiji.schema.shell.DDLException
import org.kiji.schema.shell.Environment
import org.kiji.schema.shell.TableNotFoundException

/**
 * Abstract base class for DDL command implementations that manipulate properties
 * of a specific table (vs. those which adjust the environment, instance, etc.).
 */
abstract class TableDDLCommand extends DDLCommand {

  /** The name of the table being operated on. */
  val tableName: String;

  /**
   * Method called by the runtime to execute this parsed command.
   * @return the environment object to use in subsequent commands.
   */
  def exec(): Environment = {
    // Default behavior: Get the table layout, mutate it, and apply the new layout.
    validateArguments()
    val layout = getInitialLayout()
    updateLayout(layout)
    applyUpdate(layout)
    echo("OK.")
    return env
  }

  /**
   * Retrieve the table layout to modify from Kiji.
   */
  def getInitialLayout(): TableLayoutDesc = {
    env.kijiSystem.getTableLayout(getKijiURI(), tableName) match {
      case Some(layout) => { layout.getDesc() }
      case None => { throw new TableNotFoundException(tableName) }
    }
  }

  /**
   * Validates that the arguments to this command can be applied correctly.
   * Subclasses should perform checks here (e.g., that a particular column family exists)
   * and throw DDLException if there's an error.
   */
  def validateArguments(): Unit

  /**
   * Given a table layout (e.g., from getInitialLayout()), apply any mutations to the
   * data structure representing the table's layout.
   */
  def updateLayout(layout: TableLayoutDesc): Unit

  /**
   * Given a table layout, apply it to the Kiji instance (e.g., by creating a table,
   * or updating an existing one.) The default behavior is to assume the table
   * already exists, and apply the layout to the table using KijiAdmin.
   */
  def applyUpdate(layout: TableLayoutDesc): Unit = {
    getLayoutReferenceId() match {
      case None => { } // No previous layout to refer to.
      case Some(ref) => { layout.setReferenceLayout(ref) }
    }
    env.kijiSystem.applyLayout(getKijiURI(), tableName, layout)
  }

  /**
   * Look up the current layout in the table layout database and determine its id number.
   * We need to set that as our reference id.
   *
   * @return Some(reference id) if there's an existing layout, or None if there isn't.
   */
  private def getLayoutReferenceId(): Option[String] = {
    env.kijiSystem.getTableLayout(getKijiURI(), tableName) match {
      case None => None // No existing layout. e.g., we're creating a new table.
      case Some(layout) => Some(layout.getDesc().getLayoutId())
    }
  }

  // Methods that check properties of tables for use in validateArguments().
  // On error, they throw DDLException. On success they do nothing.

  protected def checkColFamilyExists(layout: TableLayoutDesc, familyName: String): Unit = {
    getFamily(layout, familyName) match {
      case None => {
        throw new DDLException("No such family \"" + familyName + "\" in table " + layout.getName())
      }
      case Some(f) => { }
    }
  }

  protected def checkColFamilyIsGroupType(layout: TableLayoutDesc, familyName: String): Unit = {
    getFamily(layout, familyName) match {
      case None => {
        throw new DDLException("No such family \"" + familyName + "\" in table " + layout.getName())
      }
      case Some(f) => {
        Option(f.getMapSchema()) match {
          case Some(schema) => {
            throw new DDLException("Expected group-type family \"" + familyName + "\"")
          }
          case None => { }
        }
      }
    }
  }

  protected def checkColumnExists(layout: TableLayoutDesc, familyName: String,
      qualifier: String): Unit = {
    getFamily(layout, familyName).getOrElse(
        throw new DDLException("No such family \"" + familyName + "\""))
    checkColFamilyIsGroupType(layout, familyName)
    getColumn(layout, familyName, qualifier).getOrElse(
        throw new DDLException("Column \"" + familyName + ":" + qualifier
                  + "\" does not exist."))
  }

  protected def checkColumnMissing(layout: TableLayoutDesc, familyName: String,
      qualifier: String): Unit = {
    getFamily(layout, familyName).getOrElse(
        throw new DDLException("No such family \"" + familyName + "\""))
    checkColFamilyIsGroupType(layout, familyName)

    getColumn(layout, familyName, qualifier) match {
      case None  => { /* expected. */ }
      case Some(c) => {
        throw new DDLException("Column \"" + familyName + ":" + qualifier
                  + "\" already exists.")
      }
    }
  }

  protected def checkColFamilyIsMapType(layout: TableLayoutDesc, familyName: String): Unit = {
    getFamily(layout, familyName) match {
      case None => {
        throw new DDLException("No such family \"" + familyName + "\" in table " + layout.getName())
      }
      case Some(f) => {
        Option(f.getMapSchema()).getOrElse({
          throw new DDLException("Expected map-type family \"" + familyName + "\"")
        })
      }
    }
  }

  protected def checkColFamilyMissing(layout: TableLayoutDesc, familyName: String): Unit = {
    getFamily(layout, familyName) match {
      case Some(f) => {
        throw new DDLException("Family \"" + familyName + "\" already exists in table "
            + layout.getName())
      }
      case None => { }
    }
  }

  protected def checkLocalityGroupExists(layout: TableLayoutDesc, groupName: String): Unit = {
    getLocalityGroup(layout, groupName).getOrElse(
        throw new DDLException("No such locality group \"" + groupName + "\" in table "
            + layout.getName()))
  }

  protected def checkLocalityGroupMissing(layout: TableLayoutDesc, groupName: String): Unit = {
    getLocalityGroup(layout, groupName) match {
      case Some(lg) => {
        throw new DDLException("Locality group \"" + groupName + "\" already exists in table "
            + layout.getName())
      }
      case None => { }
    }
  }

  protected def checkTableExists(): Unit = {
    env.kijiSystem.getTableLayout(getKijiURI(), tableName) match {
      case Some(layout) => { /* success. */ }
      case None => throw new DDLException("No such table \"" + tableName + "\"")
    }
  }

  /**
   * Extracts a mutable ColumnDesc from a TableLayoutDesc.
   *
   * @param layout the Avro table description to walk.
   * @param familyName the family name for the column to extract.
   * @param qualifier the qualifier for the column to extract.
   * @return Some[ColumnDesc] describing the column, or None.
   */
  protected def getColumn(layout: TableLayoutDesc, familyName: String,
      qualifier: String): Option[ColumnDesc] = {
    layout.getLocalityGroups().foreach { localityGroup =>
      localityGroup.getFamilies().foreach { family =>
        if (family.getName().equals(familyName)) {
          family.getColumns().foreach { column =>
            if (column.getName().equals(qualifier)) {
              return Some(column)
            }
          }
        }
      }
    }

    return None
  }

  /**
   * Extracts a mutable ColumnDesc from a TableLayoutDesc.
   *
   * @param layout the Avro table description to walk.
   * @param columnName the family name and qualifier for the column to extract.
   * @return Some[ColumnDesc] describing the column, or None.
   */
  protected def getColumn(layout: TableLayoutDesc, columnName: ColumnName): Option[ColumnDesc] = {
    getColumn(layout, columnName.family, columnName.qualifier)
  }

  /**
   * Extracts a mutable FamilyDesc from a TableLayoutDesc.
   *
   * @param layout the Avro table description to walk.
   * @param familyName the family name to extract.
   * @return Some[FamilyDesc] describing the family, or None.
   */
  protected def getFamily(layout: TableLayoutDesc, familyName: String): Option[FamilyDesc] = {
    layout.getLocalityGroups().foreach { localityGroup =>
      localityGroup.getFamilies().foreach { family =>
        if (family.getName().equals(familyName)) {
          return Some(family)
        }
      }
    }

    return None
  }

  /**
   * Extracts a mutable LocalityGroupDesc from a TableLayoutDesc.
   *
   * @param layout the Avro table description to walk.
   * @param localityGroupName the locality group name to extract.
   * @return Some[LocalityGroupDesc] describing the group, or None.
   */
  protected def getLocalityGroup(layout: TableLayoutDesc,
      localityGroupName: String): Option[LocalityGroupDesc] = {
    layout.getLocalityGroups().foreach { localityGroup =>
      if (localityGroup.getName() == localityGroupName) {
        return Some(localityGroup)
      }
    }

    return None
  }
}

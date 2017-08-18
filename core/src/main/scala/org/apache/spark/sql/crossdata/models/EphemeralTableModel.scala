/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package org.apache.spark.sql.crossdata.models

import org.apache.spark.sql.types.StructType

case class EphemeralTableModel(name: String,
                               options: EphemeralOptionsModel,
                               schema: Option[StructType] = None) {

  def toPrettyString : String = ModelUtils.modelToJsonString(this).replaceAll("\\\\\"","\"")

}

/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package org.apache.spark.sql.crossdata.daos.impl

import com.typesafe.config.Config
import org.apache.spark.sql.crossdata.daos.DAOConstants._
import org.apache.spark.sql.crossdata.daos.EphemeralQueriesDAO

import scala.util.Try

class EphemeralQueriesTypesafeDAO(configuration: Config) extends EphemeralQueriesDAO {

  def prefix: String = Try(configuration.getString(PrefixStreamingCatalogsConfig) + "_") getOrElse ("")

  override val config = new TypesafeConfig(Option(configuration))

}

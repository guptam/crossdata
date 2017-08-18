/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package org.apache.spark.sql.crossdata.catalyst.execution

import org.apache.spark.sql.catalyst.TableIdentifier
import org.apache.spark.sql.catalyst.analysis.EliminateSubQueries
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.crossdata.XDContext
import org.apache.spark.sql._
import org.apache.spark.sql.crossdata.catalog.XDCatalog.CrossdataTable
import org.apache.spark.sql.crossdata.catalog.interfaces.XDCatalogCommon
import org.apache.spark.sql.crossdata.util.CreateRelationUtil._
import org.apache.spark.sql.execution.RunnableCommand
import org.apache.spark.sql.execution.datasources.{LogicalRelation, ResolvedDataSource}
import org.apache.spark.sql.sources.{HadoopFsRelation, InsertableRelation}
import org.apache.spark.sql.types.StructType

private[crossdata] trait DoCatalogDataSourceTable extends RunnableCommand {

  protected val crossdataTable: CrossdataTable
  protected val allowExisting: Boolean

  override def run(sqlContext: SQLContext): Seq[Row] =
    catalogDataSourceTable(sqlContext.asInstanceOf[XDContext])

  protected def catalogDataSourceTable(crossdataContext: XDContext): Seq[Row]

}

private[crossdata] case class PersistDataSourceTable(
                                                      protected val crossdataTable: CrossdataTable,
                                                      protected val allowExisting: Boolean
                                                    ) extends DoCatalogDataSourceTable {

  override protected def catalogDataSourceTable(crossdataContext: XDContext): Seq[Row] = {


    val tableIdentifier = crossdataTable.tableIdentifier

    if (crossdataContext.catalog.tableExists(tableIdentifier.toTableIdentifier)) {
      if (!allowExisting)
        throw new AnalysisException(s"Table ${tableIdentifier.unquotedString} already exists")
    } else
      crossdataContext.catalog.persistTable(crossdataTable, createLogicalRelation(crossdataContext, crossdataTable))

    Seq.empty[Row]
  }


}

private[crossdata] case class RegisterDataSourceTable(
                                                       protected val crossdataTable: CrossdataTable,
                                                       protected val allowExisting: Boolean
                                                     ) extends DoCatalogDataSourceTable {

  override protected def catalogDataSourceTable(crossdataContext: XDContext): Seq[Row] = {

    val tableIdentifier = crossdataTable.tableIdentifier.toTableIdentifier

    crossdataContext.catalog.registerTable(
      tableIdentifier,
      createLogicalRelation(crossdataContext, crossdataTable),
      Some(crossdataTable)
    )
    Seq.empty[Row]
  }

}

private[crossdata] case class PersistSelectAsTable(
                                 tableIdent: TableIdentifier,
                                 provider: String,
                                 partitionColumns: Array[String],
                                 mode: SaveMode,
                                 options: Map[String, String],
                                 query: LogicalPlan) extends RunnableCommand {

  override def run(sqlContext: SQLContext): Seq[Row] = {

    val crossdataContext = sqlContext.asInstanceOf[XDContext]

    // TODO REFACTOR HIVE CODE ***************
    var createMetastoreTable = false
    var existingSchema = None: Option[StructType]
    if (crossdataContext.catalog.tableExists(tableIdent)) {
      // Check if we need to throw an exception or just return.
      mode match {
        case SaveMode.ErrorIfExists =>
          throw new AnalysisException(s"Table ${tableIdent.unquotedString} already exists. " +
            s"If you are using saveAsTable, you can set SaveMode to SaveMode.Append to " +
            s"insert data into the table or set SaveMode to SaveMode.Overwrite to overwrite" +
            s"the existing data. " +
            s"Or, if you are using SQL CREATE TABLE, you need to drop ${tableIdent.unquotedString} first.")
        case SaveMode.Ignore =>
          // Since the table already exists and the save mode is Ignore, we will just return.
          Seq.empty[Row]
        case SaveMode.Append =>
          // Check if the specified data source match the data source of the existing table.
          val resolved = ResolvedDataSource(
            sqlContext, Some(query.schema.asNullable), partitionColumns, provider, options)
          val createdRelation = LogicalRelation(resolved.relation)
          EliminateSubQueries(sqlContext.catalog.lookupRelation(tableIdent)) match {
            case l@LogicalRelation(_: InsertableRelation | _: HadoopFsRelation, _) =>
              if (l.relation != createdRelation.relation) {
                val errorDescription =
                  s"Cannot append to table ${tableIdent.unquotedString} because the resolved relation does not " +
                    s"match the existing relation of ${tableIdent.unquotedString}. " +
                    s"You can use insertInto(${tableIdent.unquotedString}, false) to append this DataFrame to the " +
                    s"table ${tableIdent.unquotedString} and using its data source and options."
                val errorMessage =
                  s"""|$errorDescription
                      |== Relations ==
                      |${
                    sideBySide(
                      s"== Expected Relation ==" :: l.toString :: Nil,
                      s"== Actual Relation ==" :: createdRelation.toString :: Nil
                    ).mkString("\n")
                  }
                  """.stripMargin
                throw new AnalysisException(errorMessage)
              }
              existingSchema = Some(l.schema)
            case o =>
              throw new AnalysisException(s"Saving data in ${o.toString} is not supported.")
          }
        case SaveMode.Overwrite =>
          crossdataContext.catalog.dropTable(tableIdent)
          createMetastoreTable = true
      }
    } else {
      // The table does not exist. We need to create it in metastore.
      createMetastoreTable = true
    }

    val data = DataFrame(crossdataContext, query)
    val df = existingSchema match {
      // If we are inserting into an existing table, just use the existing schema.
      case Some(schema) => sqlContext.internalCreateDataFrame(data.queryExecution.toRdd, schema)
      case None => data
    }

    // **************** TODO end refactor

    if (createMetastoreTable) {
      val resolved = ResolvedDataSource(sqlContext, provider, partitionColumns, mode, options, df)
      import XDCatalogCommon._
      val identifier = TableIdentifier(tableIdent.table, tableIdent.database).normalize(crossdataContext.conf)
      val crossdataTable = CrossdataTable(identifier, Some(df.schema), provider, Array.empty, options)
      crossdataContext.catalog.persistTable(crossdataTable, createLogicalRelation(sqlContext, crossdataTable))
    }


    Seq.empty[Row]
  }

  private def sideBySide(left: Seq[String], right: Seq[String]): Seq[String] = {
    val maxLeftSize = left.map(_.size).max
    val leftPadded = left ++ Seq.fill(math.max(right.size - left.size, 0))("")
    val rightPadded = right ++ Seq.fill(math.max(left.size - right.size, 0))("")

    leftPadded.zip(rightPadded).map {
      case (l, r) => (if (l == r) " " else "!") + l + (" " * ((maxLeftSize - l.size) + 3)) + r
    }
  }
}


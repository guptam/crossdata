/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package org.apache.spark.sql.crossdata.authorizer

import com.stratio.crossdata.security._

class SMAllowingWriteCatalogAndDatastore extends BaseSecurityManagerTest{

  override def authorize(userId: String, resource: Resource, action: Action): Boolean = {
    val isWriteCatalog = resource.resourceType == CatalogResource && action == Write && resource.name == SecurityManagerTestConstants.CatalogIdentifier
    val isWriteDatastoreAll = resource.resourceType == DatastoreResource && resource.name == "*" && action == Write

    (isWriteCatalog || isWriteDatastoreAll) && super.authorize(userId, resource, action)
  }

}


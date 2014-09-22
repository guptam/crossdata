/*
 * Licensed to STRATIO (C) under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.  The STRATIO (C) licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.stratio.meta2.core.coordinator;

import com.stratio.meta2.common.api.generated.connector.SupportedOperationsType;
import com.stratio.meta2.common.api.generated.datastore.OptionalPropertiesType;
import com.stratio.meta2.common.api.generated.datastore.RequiredPropertiesType;
import com.stratio.meta2.common.data.CatalogName;
import com.stratio.meta2.common.data.ClusterName;
import com.stratio.meta2.common.data.ConnectorName;
import com.stratio.meta2.common.data.DataStoreName;
import com.stratio.meta2.common.metadata.ClusterAttachedMetadata;
import com.stratio.meta2.common.metadata.ClusterMetadata;
import com.stratio.meta2.common.metadata.ConnectorAttachedMetadata;
import com.stratio.meta2.common.metadata.ConnectorMetadata;
import com.stratio.meta2.common.metadata.DataStoreMetadata;
import com.stratio.meta2.common.statements.structures.selectors.Selector;
import com.stratio.meta2.core.metadata.MetadataManager;
import com.stratio.meta2.core.metadata.MetadataManagerTests;
import com.stratio.meta2.core.query.*;

import com.stratio.meta2.core.statements.AttachClusterStatement;
import com.stratio.meta2.core.statements.AttachConnectorStatement;

import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class CoordinatorTest extends MetadataManagerTests {

  @Test
  public void testAttachCluster() throws Exception {

    // Create and add a test datastore metadata to the metadatamanager
    DataStoreName name = new DataStoreName("datastoreTest");
    String version = "0.1.0";
    RequiredPropertiesType requiredProperties = null;
    OptionalPropertiesType othersProperties = null;
    DataStoreMetadata datastoreTest = new DataStoreMetadata(name, version, requiredProperties, othersProperties);
    MetadataManager.MANAGER.createDataStore(datastoreTest, false);

    // Add information about the cluster attachment to the metadatamanager
    BaseQuery baseQuery = new BaseQuery(UUID.randomUUID().toString(), "ATTACH CLUSTER cassandra_prod ON DATASTORE cassandra WITH OPTIONS {}", new CatalogName("test"));

    AttachClusterStatement
        attachClusterStatement = new AttachClusterStatement("clusterTest", false, "datastoreTest", "{}");

    MetadataParsedQuery
        metadataParsedQuery = new MetadataParsedQuery(baseQuery, attachClusterStatement);

    MetadataValidatedQuery metadataValidatedQuery = new MetadataValidatedQuery(metadataParsedQuery);

    MetadataPlannedQuery plannedQuery = new MetadataPlannedQuery(metadataValidatedQuery);

    Coordinator coordinator = new Coordinator();
    coordinator.coordinate(plannedQuery);

    // Check that changes persisted in the MetadataManager ("datastoreTest" datastore)
    datastoreTest = MetadataManager.MANAGER.getDataStore(new DataStoreName("dataStoreTest"));
    Map<ClusterName, ClusterAttachedMetadata> clusterAttachedRefsTest = datastoreTest.getClusterAttachedRefs();
    boolean found = false;
    for(ClusterName clusterNameTest: clusterAttachedRefsTest.keySet()){
      ClusterAttachedMetadata clusterAttachedMetadata = clusterAttachedRefsTest.get(clusterNameTest);
      if(clusterAttachedMetadata.getClusterRef().equals(new ClusterName("clusterTest"))){
        assertEquals(clusterAttachedMetadata.getDataStoreRef(), new DataStoreName("datastoreTest"), "Wrong attachment for clusterTest");
        found = true;
        break;
      }
    }
    assertTrue(found, "Attachment not found");
  }

  @Test
  public void testAttachConnector() throws Exception {

    // Create and add a test datastore metadata to the metadatamanager
    DataStoreName dataStoreName = new DataStoreName("datastoreTest");
    String dataStoreVersion = "0.1.0";
    RequiredPropertiesType requiredProperties = null;
    OptionalPropertiesType othersProperties = null;
    DataStoreMetadata datastoreTest = new DataStoreMetadata(dataStoreName, dataStoreVersion, requiredProperties, othersProperties);
    MetadataManager.MANAGER.createDataStore(datastoreTest, false);

    // Create and add a test cluster metadata to the metadatamanager
    ClusterName clusterName = new ClusterName("clusterTest");
    DataStoreName dataStoreRef = new DataStoreName("dataStoreTest");
    Map<String, Object> options = new HashMap<>();
    Map<ConnectorName, ConnectorAttachedMetadata> connectorAttachedRefs = new HashMap<>();
    ClusterMetadata
        clusterTest = new ClusterMetadata(clusterName, dataStoreRef, options,
                                          connectorAttachedRefs);
    MetadataManager.MANAGER.createCluster(clusterTest, false);

    // Create and add a test connector metadata to the metadatamanager
    ConnectorName connectorName = new ConnectorName("connectorTest");
    String connectorVersion = "0.1.0";
    Set<DataStoreName> dataStoreRefs = new HashSet<>();
    com.stratio.meta2.common.api.generated.connector.RequiredPropertiesType connectorRequiredProperties =
        null;
    com.stratio.meta2.common.api.generated.connector.OptionalPropertiesType connectorOptionalProperties =
        null;
    SupportedOperationsType supportedOperations = null;
    ConnectorMetadata
        connectorTest = new ConnectorMetadata(connectorName, connectorVersion, dataStoreRefs, connectorRequiredProperties, connectorOptionalProperties, supportedOperations);
    MetadataManager.MANAGER.createConnector(connectorTest, false);

    // Add information about the connector attachment to the metadatamanager
    BaseQuery baseQuery = new BaseQuery(UUID.randomUUID().toString(), "ATTACH CONNECTOR cassandra_connector TO cassandra_prod WITH OPTIONS {}", new CatalogName("test"));

    ConnectorName connectorRef = new ConnectorName("connectorTest");
    ClusterName clusterRef = new ClusterName("clusterTest");
    Map<Selector, Selector> properties = new HashMap<>();
    ConnectorAttachedMetadata connectorAttachedMetadata = new ConnectorAttachedMetadata(connectorRef, clusterRef, properties);

    AttachConnectorStatement
        attachConnectorStatement = new AttachConnectorStatement("connectorTest", "clusterTest", "{}");

    MetadataParsedQuery
        metadataParsedQuery = new MetadataParsedQuery(baseQuery, attachConnectorStatement);

    MetadataValidatedQuery metadataValidatedQuery = new MetadataValidatedQuery(metadataParsedQuery);

    MetadataPlannedQuery plannedQuery = new MetadataPlannedQuery(metadataValidatedQuery);

    Coordinator coordinator = new Coordinator();
    coordinator.coordinate(plannedQuery);

    // Check that changes persisted in the MetadataManager ("clusterTest" cluster)
    clusterTest = MetadataManager.MANAGER.getCluster(new ClusterName("clusterTest"));

    Map<ConnectorName, ConnectorAttachedMetadata>
        connectorAttachedRefsTest = clusterTest.getConnectorAttachedRefs();

    boolean found = false;

    for(ConnectorName connectorNameTest: connectorAttachedRefsTest.keySet()){
      ConnectorAttachedMetadata
          connectorAttachedMetadataTest = connectorAttachedRefsTest.get(connectorNameTest);
      if(connectorAttachedMetadataTest.getClusterRef().equals(new ClusterName("clusterTest"))){
        assertEquals(connectorAttachedMetadata.getClusterRef(), new ClusterName("clusterTest"), "Wrong attachment for connectorTest");
        found = true;
        break;
      }
    }
    assertTrue(found, "Attachment not found");
  }

}

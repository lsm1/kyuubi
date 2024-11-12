/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kyuubi.server

import org.apache.kyuubi.operation.{OperationHandle, OperationState, OperationStatus}
import org.apache.kyuubi.service.AbstractBackendService
import org.apache.kyuubi.session.{KyuubiSessionManager, SessionManager}
import org.apache.kyuubi.shaded.hive.service.rpc.thrift.TProtocolVersion

class KyuubiBackendService(name: String) extends AbstractBackendService(name) {

  def this() = this(classOf[KyuubiBackendService].getSimpleName)

  override val sessionManager: SessionManager = new KyuubiSessionManager()

  override def getOperationStatus(
      operationHandle: OperationHandle,
      maxWait: Option[Long]): OperationStatus = {
    val operation = sessionManager.operationManager.getOperation(operationHandle)
    val operationStatus = super.getOperationStatus(operationHandle, maxWait)
    // Clients less than version 2.1 have no HIVE-4924 Patch,
    // no queryTimeout parameter and no TIMEOUT status.
    // When the server enables kyuubi.operation.query.timeout,
    // this will cause the client of the lower version to get stuck.
    // Check thrift protocol version <= HIVE_CLI_SERVICE_PROTOCOL_V8(Hive 2.1.0),
    // convert TIMEDOUT_STATE to CANCELED.
    if (operationStatus.state == OperationState.TIMEOUT && operation.getSession.protocol.getValue <=
        TProtocolVersion.HIVE_CLI_SERVICE_PROTOCOL_V8.getValue) {
      operationStatus.copy(state = OperationState.CANCELED)
    } else {
      operationStatus
    }
  }
}

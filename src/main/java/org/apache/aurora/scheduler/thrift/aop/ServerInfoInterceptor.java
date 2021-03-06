/**
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
package org.apache.aurora.scheduler.thrift.aop;

import javax.inject.Inject;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.aurora.gen.Response;
import org.apache.aurora.scheduler.storage.entities.IServerInfo;

import static org.apache.aurora.gen.apiConstants.CURRENT_API_VERSION;

class ServerInfoInterceptor implements MethodInterceptor {

  @Inject
  private IServerInfo serverInfo;

  @Override
  public Object invoke(MethodInvocation invocation) throws Throwable {
    Response resp = (Response) invocation.proceed();
    resp.setDEPRECATEDversion(CURRENT_API_VERSION);
    resp.setServerInfo(serverInfo.newBuilder());
    return resp;
  }
}

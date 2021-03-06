// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the 
// specific language governing permissions and limitations
// under the License.
package com.cloud.hypervisor.vmware.resource;

import org.apache.log4j.Logger;

import com.cloud.hypervisor.vmware.manager.VmwareManager;
import com.cloud.hypervisor.vmware.manager.VmwareManagerImpl;
import com.cloud.hypervisor.vmware.util.VmwareContext;
import com.cloud.utils.StringUtils;
import com.cloud.utils.component.ComponentContext;

import com.vmware.apputils.version.ExtendedAppUtil;

public class VmwareContextFactory {
	
    private static final Logger s_logger = Logger.getLogger(VmwareContextFactory.class);
	
	private static volatile int s_seq = 1;
	private static VmwareManager s_vmwareMgr;
	
	static {
		// skip certificate check
		System.setProperty("axis.socketSecureFactory", "org.apache.axis.components.net.SunFakeTrustSocketFactory");
		s_vmwareMgr = ComponentContext.inject(VmwareManagerImpl.class);
	}

	public static VmwareContext create(String vCenterAddress, String vCenterUserName, String vCenterPassword) throws Exception {
		assert(vCenterAddress != null);
		assert(vCenterUserName != null);
		assert(vCenterPassword != null);

		String serviceUrl = "https://" + vCenterAddress + "/sdk/vimService";
		String[] params = new String[] {"--url", serviceUrl, "--username", vCenterUserName, "--password", vCenterPassword };

		if(s_logger.isDebugEnabled())
			s_logger.debug("initialize VmwareContext. url: " + serviceUrl + ", username: " + vCenterUserName + ", password: " + StringUtils.getMaskedPasswordForDisplay(vCenterPassword));
			
		ExtendedAppUtil appUtil = ExtendedAppUtil.initialize(vCenterAddress + "-" + s_seq++, params);
		
		appUtil.connect();
		VmwareContext context = new VmwareContext(appUtil, vCenterAddress);
		context.registerStockObject(VmwareManager.CONTEXT_STOCK_NAME, s_vmwareMgr);
		
		context.registerStockObject("serviceconsole", s_vmwareMgr.getServiceConsolePortGroupName());
		context.registerStockObject("manageportgroup", s_vmwareMgr.getManagementPortGroupName());

		return context;
	}
}

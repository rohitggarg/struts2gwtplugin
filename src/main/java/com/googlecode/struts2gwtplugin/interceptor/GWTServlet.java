/*
 * $Id$
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.googlecode.struts2gwtplugin.interceptor;


import java.lang.reflect.Method;

import javax.servlet.ServletContext;

import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.server.rpc.RPC;
import com.google.gwt.user.server.rpc.RPCRequest;
import com.google.gwt.user.server.rpc.RemoteServiceServlet;
import com.opensymphony.xwork2.ActionInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This class is a modified version of GWT's RemoteServiceServlet.java
 * @author rohitggarg
 */
@SuppressWarnings("serial")
class GWTServlet extends RemoteServiceServlet {
	private transient final Log log = LogFactory.getLog(getClass());

	/** Context for the servlet */
	private ServletContext servletContext;

	/** Action class to invoke */
	private ActionInvocation actionInvocation;

	/**
	 * Find the invoked method on either the specified interface or any super.
	 */
	private static Method findInterfaceMethod(Class<?> intf, String methodName,
			Class<?>[] paramTypes, boolean includeInherited) {
		try {
			return intf.getDeclaredMethod(methodName, paramTypes);
		} catch(NoSuchMethodException e) {
			if(includeInherited) {
				Class<?>[] superintfs = intf.getInterfaces();
				for(int i = 0; i < superintfs.length; i++) {
					Method method = findInterfaceMethod(superintfs[i],
							methodName, paramTypes, true);
					if(method != null) {
						return method;
					}
				}
			}

			return null;
		}
	}

	/**
	 * The default constructor.
	 */
	public GWTServlet() {
		super();
	}

	/* (non-Javadoc)
	 * @see com.google.gwt.user.server.rpc.RemoteServiceServlet#processCall(java.lang.String)
	 */
	public String processCall(String payload) throws SerializationException {

		// default return value - Should this be something else?
		// no known constants to use in this case
		String result = null; 

		// get the RPC Request from the request data
		//RPCRequest rpcRequest= RPC.decodeRequest(payload);
		RPCRequest rpcRequest = RPC.decodeRequest(payload,null,this);
	
		
		 onAfterRequestDeserialized(rpcRequest);
		 
		// get the parameter types for the method look-up
		Class<?>[] paramTypes = rpcRequest.getMethod().getParameterTypes();        
		paramTypes = rpcRequest.getMethod().getParameterTypes();

		// we need to get the action method from Struts
		Method method = findInterfaceMethod(
				actionInvocation.getAction().getClass(), 
				rpcRequest.getMethod().getName(), 
				paramTypes, true);

		// if the method is null, this may be a hack attempt
		// or we have some other big problem
		if (method == null) {
			// present the params
			StringBuffer params = new StringBuffer();
			for (int i=0; i < paramTypes.length; i++) {
				params.append(paramTypes[i]);
				if (i < paramTypes.length-1) {
					params.append(", ");
				}
			}

			// throw a security exception, could be attempted hack
			throw new GWTServletException(
					"Failed to locate method "+ rpcRequest.getMethod().getName()
					+ "("+ params +") on interface "
					+ actionInvocation.getAction().getClass().getName()
					+ " requested through interface "
					+ rpcRequest.getClass().getName());
		}

	
		Object callResult = null;
		try {
			callResult = method.invoke(actionInvocation.getAction(), 
					rpcRequest.getParameters());
			// package  up response for GWT
			result = RPC.encodeResponseForSuccess(method, callResult);
			
		} catch (Exception e) {
			// check for checked exceptions
			if (e.getCause() != null) {
				log.error("Struts2GWT exception", e.getCause());
				Throwable cause = e.getCause();
				boolean found = false;
				for (Class<?> checkedException : rpcRequest.getMethod().getExceptionTypes()){
					if (cause.getClass().equals(checkedException)) {
						found = true;
						break;
					}
				}
				if (!found) {
					
					throw new Struts2GWTBridgeException("Unhandled exception!", cause);
				}
				result = RPC.encodeResponseForFailure(null, e.getCause(), rpcRequest.getSerializationPolicy());
			} else {
				throw new Struts2GWTBridgeException("Unable to serialize the exception.", e);
			}
		} 

		// return our response
		return result;
	}

	/**
	 * Returns the servlet's context
	 * 
	 * @return a <code>ServletContext</code>
	 */
	public ServletContext getServletContext() {
		return servletContext;
	}

	/**
	 * Sets the servlet's context
	 * 
	 * @param servletContext <code>ServletContext</code> to use
	 */
	public void setServletContext(ServletContext servletContext) {
		this.servletContext = servletContext;
	}

	/**
	 * Returns the action class to invoke
	 * 
	 * @return an <code>ActionInvocation</code>
	 */
	public ActionInvocation getActionInvocation() {
		return actionInvocation;
	}

	/**
	 * Sets the action class to call
	 * 
	 * @param actionInvocation <code>ActionInvocation</code> to use
	 */
	public void setActionInvocation(ActionInvocation actionInvocation) {
		this.actionInvocation = actionInvocation;
	}

	class GWTServletException extends SecurityException {
		public GWTServletException(String msg) {
			super(msg);
		}
	}

	class Struts2GWTBridgeException extends RuntimeException {
		public Struts2GWTBridgeException(String msg, Throwable e) {
			super(msg, e);
		}
	}
}
